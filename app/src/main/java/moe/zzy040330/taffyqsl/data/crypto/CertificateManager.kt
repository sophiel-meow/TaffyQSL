package moe.zzy040330.taffyqsl.data.crypto

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.domain.model.CertInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import java.time.ZoneOffset
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class CertificateManager(private val context: Context) {

    companion object {
        const val CERTS_DIR = "certs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Custom OIDs in ARRL certificates
        const val OID_CALLSIGN = "1.3.6.1.4.1.12348.1.1"
        const val OID_QSO_NOT_BEFORE = "1.3.6.1.4.1.12348.1.2"
        const val OID_QSO_NOT_AFTER = "1.3.6.1.4.1.12348.1.3"
        const val OID_DXCC_ENTITY = "1.3.6.1.4.1.12348.1.4"
        const val OID_ISSUER_ORG = "1.3.6.1.4.1.12348.1.6"
        const val OID_EMAIL = "1.3.6.1.4.1.12348.1.8"

        /**
         * Parse an ASN.1 string extension value from DER bytes returned by getExtensionValue().
         * getExtensionValue() wraps the actual extension value in an outer OCTET STRING.
         *
         * TrustedQSL stores DXCC entity and QSO date extensions as raw ASCII bytes inside an
         * ASN1_OCTET_STRING with no inner ASN.1 type tag (confirmed from openssl_cert.cpp:
         * tqsl_get_cert_ext copies ASN1_STRING_get0_data directly).
         * The callsign extension may use a proper ASN.1 UTF8String.
         * So we try the ASN.1 path first, then fall back to raw UTF-8.
         */
        fun parseAsn1String(der: ByteArray?): String? {
            if (der == null || der.size < 2) return null

            var offset = 0

            // Outer OCTET STRING wrapper (tag = 0x04)
            if (der[offset++].toInt() and 0xFF != 0x04) return null
            val (outerLen, outerLenBytes) = readAsn1Length(der, offset)
            offset += outerLenBytes

            if (offset >= der.size) return null

            // Try ASN.1 string first (e.g. UTF8String-encoded callsign)
            val asn1Str = parseAsn1StringRaw(der, offset)
            if (asn1Str != null) return asn1Str

            // Fall back to raw UTF-8 bytes (DXCC entity "291", QSO date "2024-01-01", etc.)
            val len = outerLen.coerceAtMost(der.size - offset)
            return if (len > 0) String(der, offset, len, Charsets.UTF_8) else null
        }

        /**
         * Parse a raw ASN.1 string value (no outer OCTET STRING wrapper).
         * Used for values embedded directly in Subject DN as #hex.
         */
        fun parseAsn1StringRaw(der: ByteArray, startOffset: Int = 0): String? {
            if (startOffset >= der.size) return null
            var offset = startOffset
            val tag = der[offset++].toInt() and 0xFF

            // Accept UTF8String (0x0C), IA5String (0x16), PrintableString (0x13),
            // VisibleString (0x1A), BMPString (0x1E), TeletexString (0x14)
            if (tag != 0x0C && tag != 0x16 && tag != 0x13 && tag != 0x1A && tag != 0x1E && tag != 0x14) return null
            val (innerLen, innerLenBytes) = readAsn1Length(der, offset)
            offset += innerLenBytes
            if (offset + innerLen > der.size) return null
            return String(
                der,
                offset,
                innerLen,
                if (tag == 0x1E) Charsets.UTF_16BE else Charsets.UTF_8
            )
        }

        /**
         * Decode a Subject DN attribute value that may be hex-encoded (#hex = DER bytes).
         * Java's X500Principal.getName() encodes unknown OID values as "#<hex-DER>".
         */
        fun decodeDnAttributeValue(value: String): String? {
            if (!value.startsWith("#")) return value
            val hex = value.substring(1)
            if (hex.length % 2 != 0) return null
            return try {
                val bytes = ByteArray(hex.length / 2) {
                    hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
                }
                parseAsn1StringRaw(bytes)
            } catch (_: Exception) {
                null
            }
        }

        private fun readAsn1Length(data: ByteArray, offset: Int): Pair<Int, Int> {
            if (offset >= data.size) return Pair(0, 1)
            val first = data[offset].toInt() and 0xFF
            return if (first < 0x80) {
                Pair(first, 1)
            } else {
                val numBytes = first and 0x7F
                var length = 0
                for (i in 1..numBytes) {
                    length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
                }
                Pair(length, 1 + numBytes)
            }
        }

        fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun extractCN(dn: String): String =
            dn.split(",").find { it.trim().startsWith("CN=") }
                ?.substringAfter("CN=")?.trim() ?: dn

        /**
         * Extract callsign from Subject DN.
         * ARRL LoTW certificates have the callsign as OID in Subject DN:
         * "1.3.6.1.4.1.12348.1.1=XX0FOO, CN=John Smith, emailAddress=..."
         */
        fun extractCallsignFromDN(dn: String): String {
            // Parse DN components
            val components = dn.split(",").map { it.trim() }

            // First try the ARRL callsign OID in Subject DN
            val callsignOid =
                components.find { it.startsWith("$OID_CALLSIGN=") || it.startsWith("1.3.6.1.4.1.12348.1.1=") }
                    ?.substringAfter("=")?.trim()
            if (!callsignOid.isNullOrEmpty()) {
                return decodeDnAttributeValue(callsignOid) ?: callsignOid
            }

            // Try CN field
            val cn = components.find { it.startsWith("CN=") }
                ?.substringAfter("CN=")?.trim()

            if (cn != null) {

                // Check if CN contains callsign pattern
                // TODO: replace with a robust regex
                val callsignPattern = Regex("\\b([A-Z0-9]{1,3}[0-9][A-Z0-9]{0,3}[A-Z])\\b")
                val match = callsignPattern.find(cn)
                if (match != null) {
                    return match.value
                }

                // If CN looks like a callsign itself
                if (cn.matches(Regex("^[A-Z0-9]+$")) && cn.any { it.isDigit() }) {
                    return cn
                }
            }

            // Try OU field as fallback
            val ou = components.find { it.startsWith("OU=") }
                ?.substringAfter("OU=")?.trim()
            if (ou != null && ou.matches(Regex("^[A-Z0-9]+$")) && ou.any { it.isDigit() }) {
                return ou
            }

            return ""
        }

        /**
         * Extract DXCC entity from Subject DN.
         * Format: "1.3.6.1.4.1.12348.1.4=318"
         */
        fun extractDxccFromDN(dn: String): Int {
            val components = dn.split(",").map { it.trim() }
            val dxccOid =
                components.find { it.startsWith("$OID_DXCC_ENTITY=") || it.startsWith("1.3.6.1.4.1.12348.1.4=") }
                    ?.substringAfter("=")?.trim()
            val dxccStr = dxccOid?.let { decodeDnAttributeValue(it) ?: it }
            return dxccStr?.toIntOrNull() ?: 0
        }

        /**
         * Extract email from Subject DN.
         * Format: "emailAddress=sophiel@zzy040330.moe"
         */
        fun extractEmailFromDN(dn: String): String {
            val components = dn.split(",").map { it.trim() }
            return components.find { it.startsWith("emailAddress=") || it.startsWith("EMAILADDRESS=") }
                ?.substringAfter("=")?.trim() ?: ""
        }
    }

    private val certsDir = File(context.filesDir, CERTS_DIR).also { it.mkdirs() }

    suspend fun importP12(uri: Uri, password: String): Result<CertInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p12Store = KeyStore.getInstance("PKCS12")
                val passwordChars: CharArray? =
                    if (password.isEmpty()) charArrayOf() else password.toCharArray()

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    p12Store.load(stream, passwordChars)
                } ?: throw Exception("Cannot open file")

                // Find the alias that has a private key which 's the user's end-entity certificate.
                // A PKCS12 from LoTW contains three certs (user + intermediate CA + root CA);
                // only the user cert has a corresponding private key entry (isKeyEntry == true).
                val alias = p12Store.aliases().toList().firstOrNull { p12Store.isKeyEntry(it) }
                    ?: throw Exception("No certificate with private key found in p12 file")
                val cert = p12Store.getCertificate(alias) as? X509Certificate
                    ?: throw Exception("Invalid certificate in p12 file")
                val privateKey = p12Store.getKey(alias, passwordChars) as? PrivateKey
                    ?: throw Exception("No private key found in p12 file")

                // Get the full certificate chain (user cert + intermediate CA + root CA)
                val certChain: Array<Certificate> =
                    p12Store.getCertificateChain(alias)?.map { it as Certificate }?.toTypedArray()
                        ?: arrayOf(cert)

                val certInfo = parseCertInfo(cert)

                // Store cert PEM
                saveCertPem(certInfo.alias, cert)

                // Store the full certificate chain for export
                saveCertChain(certInfo.alias, certChain)

                // Store private key in Android Keystore (for signing)
                importPrivateKeyToKeystore(certInfo.alias, privateKey, cert)

                // Store an encrypted copy of the raw private key bytes for later export.
                // Android Keystore keys are non-extractable (getEncoded() == null), so we can't
                // reconstruct a PKCS12 from them directly. We encrypt the PKCS8 bytes with a
                // per-cert AES-GCM key that itself lives in Android Keystore.
                saveEncryptedPrivateKey(certInfo.alias, privateKey)

                certInfo
            }
        }

    private fun saveCertPem(alias: String, cert: X509Certificate) {
        val b64 = android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT)
        val pem = "-----BEGIN CERTIFICATE-----\n$b64-----END CERTIFICATE-----\n"
        File(certsDir, "$alias.pem").writeText(pem)
    }

    /**
     * Save the full certificate chain (user cert + intermediate CA + root CA) for export.
     * Stored as concatenated PEM certificates.
     */
    private fun saveCertChain(alias: String, certChain: Array<Certificate>) {
        val pem = StringBuilder()
        certChain.forEach { cert ->
            val b64 = android.util.Base64.encodeToString(cert.encoded, android.util.Base64.DEFAULT)
            pem.append("-----BEGIN CERTIFICATE-----\n")
            pem.append(b64)
            pem.append("-----END CERTIFICATE-----\n")
        }
        File(certsDir, "$alias.chain").writeText(pem.toString())
    }

    /**
     * Load the certificate chain from storage.
     */
    private fun loadCertChain(alias: String): Array<Certificate>? {
        val chainFile = File(certsDir, "$alias.chain")
        if (!chainFile.exists()) return null
        return runCatching {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = mutableListOf<Certificate>()
            chainFile.inputStream().use { stream ->
                val allCerts = cf.generateCertificates(stream)
                certs.addAll(allCerts)
            }
            certs.toTypedArray()
        }.getOrNull()
    }

    private fun importPrivateKeyToKeystore(alias: String, key: PrivateKey, cert: X509Certificate) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE) // enable hardware security
        keyStore.load(null)
        val entry = KeyStore.PrivateKeyEntry(key, arrayOf(cert))
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_NONE)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()
        keyStore.setEntry(alias, entry, protection)
    }

    /**
     * Encrypt the raw PKCS8 private key bytes and save alongside the certificate.
     *
     * Layout of the saved file (alias.key):
     *   1 byte:    IV length
     *   N bytes:   AES-GCM IV
     *   remaining: AES-GCM ciphertext of PKCS8 DER bytes
     *
     * The AES-256 wrapping key is stored in Android Keystore under alias "<alias>_wrap".
     */
    private fun saveEncryptedPrivateKey(alias: String, privateKey: PrivateKey) {
        val keyBytes = privateKey.encoded
            ?: throw Exception("Private key encoding is not available")

        // Generate a dedicated AES wrapping key in Android Keystore
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                "${alias}_wrap",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        keyGen.generateKey()

        // Encrypt
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val wrapKey = ks.getKey("${alias}_wrap", null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(keyBytes)

        // Write IV length (1 byte) + IV + ciphertext
        val out = ByteArrayOutputStream()
        out.write(iv.size)
        out.write(iv)
        out.write(encrypted)
        File(certsDir, "$alias.key").writeBytes(out.toByteArray())
    }

    /**
     * Decrypt and reconstruct the private key from the saved encrypted file.
     */
    private fun loadDecryptedPrivateKey(alias: String, keyAlgorithm: String): PrivateKey? {
        val keyFile = File(certsDir, "$alias.key")
        if (!keyFile.exists()) return null
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
            val wrapKey = ks.getKey("${alias}_wrap", null) ?: return null

            val data = keyFile.readBytes()
            val ivLen = data[0].toInt() and 0xFF
            val iv = data.copyOfRange(1, 1 + ivLen)
            val ciphertext = data.copyOfRange(1 + ivLen, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val keyBytes = cipher.doFinal(ciphertext)

            KeyFactory.getInstance(keyAlgorithm).generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        }.getOrNull()
    }

    fun listCerts(): List<CertInfo> {
        return certsDir.listFiles { f -> f.name.endsWith(".pem") }?.mapNotNull { file ->
            runCatching { loadCertFromPem(file) }.getOrNull()
        }?.sortedBy { it.callSign } ?: emptyList()
    }

    fun getCertificate(alias: String): X509Certificate? {
        val file = File(certsDir, "$alias.pem")
        if (!file.exists()) return null
        return runCatching {
            val cf = CertificateFactory.getInstance("X.509")
            file.inputStream().use { cf.generateCertificate(it) as X509Certificate }
        }.getOrNull()
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getKey(alias, null) as? PrivateKey
        }.getOrNull()
    }

    fun deleteCert(alias: String) {
        File(certsDir, "$alias.pem").delete()
        File(certsDir, "$alias.key").delete()
        File(certsDir, "$alias.chain").delete()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            if (keyStore.containsAlias("${alias}_wrap")) keyStore.deleteEntry("${alias}_wrap")
        }
    }

    suspend fun exportP12(alias: String, password: String): ByteArray =
        withContext(Dispatchers.IO) {
            val cert = getCertificate(alias) ?: error("Certificate not found")

            // Load the private key from the encrypted file
            val privateKey = loadDecryptedPrivateKey(alias, cert.publicKey.algorithm)
                ?: error("Private key backup not found. Re-import the certificate to enable export.")

            // Load the full certificate chain (user cert + intermediate CA + root CA)
            val certChain = loadCertChain(alias) ?: arrayOf(cert)

            val exportPassword = if (password.isEmpty()) charArrayOf() else password.toCharArray()
            val p12Store = KeyStore.getInstance("PKCS12")
            p12Store.load(null, exportPassword)

            // Store the private key with the full cert chain
            p12Store.setKeyEntry("certificate", privateKey, exportPassword, certChain)

            ByteArrayOutputStream().also { out ->
                p12Store.store(out, exportPassword)
            }.toByteArray()
        }

    private fun loadCertFromPem(file: File): CertInfo {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = file.inputStream().use { cf.generateCertificate(it) as X509Certificate }
        return parseCertInfo(cert)
    }

    fun parseCertInfo(cert: X509Certificate): CertInfo {
        // Try to get callsign from OID extension first
        var callSign = parseAsn1String(cert.getExtensionValue(OID_CALLSIGN)) ?: ""

        // If OID extension doesn't exist, try to extract from Subject DN
        if (callSign.isEmpty()) {
            val subjectDN = cert.subjectX500Principal.name
            // ARRL certificates have callsign as OID in Subject DN: "1.3.6.1.4.1.12348.1.1=BG4KNN"
            callSign = extractCallsignFromDN(subjectDN)
        }

        // Also try to get DXCC from Subject DN if not in extension
        var dxccEntity =
            parseAsn1String(cert.getExtensionValue(OID_DXCC_ENTITY))?.trim()?.toIntOrNull() ?: 0
        if (dxccEntity == 0) {
            dxccEntity = extractDxccFromDN(cert.subjectX500Principal.name)
        }

        val qsoNotBefore = parseExtensionDate(cert, OID_QSO_NOT_BEFORE)
        val rawQsoNotAfter = parseExtensionDate(cert, OID_QSO_NOT_AFTER)
        val notAfterDate = cert.notAfter.toInstant().atZone(ZoneOffset.UTC).toLocalDate()

        // TrustedQSL convention (tqsl.cpp line 7948-7950):
        // When QSONotAfterDate == certNotAfter - 1 day, it means "no real end date restriction".
        // ARRL always stores notAfter-1 as the sentinel instead of leaving the field absent.
        // So here: treat sentinel as null so it shows "—" rather than a misleading date.
        val qsoNotAfter = rawQsoNotAfter?.takeIf { it != notAfterDate.minusDays(1) }

        val issuerOrg = parseAsn1String(cert.getExtensionValue(OID_ISSUER_ORG))
            ?: extractCN(cert.issuerX500Principal.name)
        val email = parseAsn1String(cert.getExtensionValue(OID_EMAIL))
            ?: extractEmailFromDN(cert.subjectX500Principal.name)
        val name = extractCN(cert.subjectX500Principal.name)
        val fingerprint = bytesToHex(MessageDigest.getInstance("SHA-256").digest(cert.encoded))

        return CertInfo(
            alias = fingerprint,
            callSign = callSign,
            name = name,
            dxccEntity = dxccEntity,
            qsoNotBefore = qsoNotBefore,
            qsoNotAfter = qsoNotAfter,
            notBefore = cert.notBefore.toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
            notAfter = notAfterDate,
            issuerOrg = issuerOrg,
            serialNumber = "${cert.serialNumber} (0x${cert.serialNumber.toString(16).uppercase()})",
            email = email,
            fingerprint = fingerprint
        )
    }

    private fun parseExtensionDate(cert: X509Certificate, oid: String): LocalDate? {
        val str = parseAsn1String(cert.getExtensionValue(oid)) ?: return null
        return runCatching { LocalDate.parse(str) }.getOrNull()
    }

    /** Get PEM-encoded certificate data (without headers), used for GABBI output */
    fun getCertPemData(alias: String): String? {
        val file = File(certsDir, "$alias.pem")
        if (!file.exists()) return null
        val pem = file.readText()
        val start = pem.indexOf('\n') + 1
        val end = pem.lastIndexOf("-----END CERTIFICATE-----")
        if (start < 1 || end < 0) return null
        return pem.substring(start, end)
    }
}
