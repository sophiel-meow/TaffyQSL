package moe.zzy040330.taffyqsl.data.crypto

import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.CertBag
import org.bouncycastle.asn1.pkcs.ContentInfo
import org.bouncycastle.asn1.pkcs.EncryptedData
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo
import org.bouncycastle.asn1.pkcs.EncryptionScheme
import org.bouncycastle.asn1.pkcs.KeyDerivationFunc
import org.bouncycastle.asn1.pkcs.PBES2Parameters
import org.bouncycastle.asn1.pkcs.PBKDF2Params
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.SafeBag
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

/**
 * Manual PKCS#12 parser used as a fallback when BouncyCastle's KeyStore SPI cannot
 * decrypt a container — specifically password-less PKCS#12 produced by modern
 * OpenSSL (PBES2 / PBKDF2 / AES-256-CBC + SHA-256 MAC).
 *
 * BouncyCastle's JCE PBKDF2 provider hard-rejects an empty char[] password with
 * "password empty", so its KeyStore SPI cannot read such files even though the
 * algorithms themselves are supported. Here we derive the AES key with BC's
 * low-level crypto API (which accepts an empty password) and decrypt each SafeBag
 * ourselves. Only PBES2 containers are handled; legacy PBES1 containers are read
 * through the normal KeyStore path.
 */
object Pkcs12Parser {

    class Pkcs12Content(
        val privateKey: PrivateKey,
        val chain: Array<Certificate>
    )

    fun parse(data: ByteArray, password: String): Pkcs12Content {
        val pfx = ASN1Sequence.getInstance(data)
        val authSafeInfo = ContentInfo.getInstance(pfx.getObjectAt(1))
        val authSafe = ASN1Sequence.getInstance(
            ASN1OctetString.getInstance(authSafeInfo.content).octets
        )

        var key: PrivateKey? = null
        val certs = mutableListOf<X509Certificate>()

        for (i in 0 until authSafe.size()) {
            val ci = ContentInfo.getInstance(authSafe.getObjectAt(i))
            val bagData: ByteArray = when {
                ci.contentType == PKCSObjectIdentifiers.data ->
                    ASN1OctetString.getInstance(ci.content).octets
                ci.contentType == PKCSObjectIdentifiers.encryptedData -> {
                    val ed = EncryptedData.getInstance(ci.content)
                    decrypt(ed.encryptionAlgorithm, ed.content.octets, password)
                }
                else -> continue
            }

            val bags = ASN1Sequence.getInstance(bagData)
            for (j in 0 until bags.size()) {
                val bag = SafeBag.getInstance(bags.getObjectAt(j))
                when (bag.bagId) {
                    PKCSObjectIdentifiers.pkcs8ShroudedKeyBag -> {
                        val epki = EncryptedPrivateKeyInfo.getInstance(bag.bagValue)
                        val pkiBytes = decrypt(epki.encryptionAlgorithm, epki.encryptedData, password)
                        key = parsePrivateKey(PrivateKeyInfo.getInstance(pkiBytes))
                    }
                    PKCSObjectIdentifiers.keyBag ->
                        key = parsePrivateKey(PrivateKeyInfo.getInstance(bag.bagValue))
                    PKCSObjectIdentifiers.certBag -> {
                        val octets = ASN1OctetString.getInstance(
                            CertBag.getInstance(bag.bagValue).certValue
                        ).octets
                        val cert = CertificateFactory.getInstance("X.509")
                            .generateCertificate(ByteArrayInputStream(octets)) as X509Certificate
                        certs.add(cert)
                    }
                }
            }
        }

        val privateKey = key ?: throw Exception("No private key found in PKCS12 file")
        return Pkcs12Content(privateKey, orderChain(privateKey, certs))
    }

    private fun parsePrivateKey(pki: PrivateKeyInfo): PrivateKey {
        val der = pki.encoded
        return runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        }.recoverCatching {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
        }.getOrElse { throw Exception("Unsupported private key algorithm in PKCS12 file") }
    }

    /** Place the certificate matching the private key first in the chain. */
    private fun orderChain(key: PrivateKey, certs: List<X509Certificate>): Array<Certificate> {
        if (certs.isEmpty()) return emptyArray()
        val publicKey = if (key is RSAPrivateCrtKey) {
            runCatching {
                KeyFactory.getInstance("RSA").generatePublic(
                    RSAPublicKeySpec(key.modulus, key.publicExponent)
                )
            }.getOrNull()
        } else null
        if (publicKey != null) {
            val first = certs.firstOrNull {
                it.publicKey.encoded.contentEquals(publicKey.encoded)
            }
            if (first != null) {
                return (listOf(first) + certs.filterNot { it === first }).toTypedArray()
            }
        }
        return certs.toTypedArray()
    }

    /**
     * Decrypt a PBES2-encrypted blob (key bag or cert bag) using the given password.
     * PBKDF2 derivation uses BC's low-level API so an empty password is accepted.
     */
    private fun decrypt(algId: AlgorithmIdentifier, data: ByteArray, password: String): ByteArray {
        val p = PBES2Parameters.getInstance(algId.parameters)
        val kdf = KeyDerivationFunc.getInstance(p.keyDerivationFunc)
        val kp = PBKDF2Params.getInstance(kdf.parameters)
        val salt = kp.salt
        val iter = kp.iterationCount.toInt()
        val keyLenBytes = kp.keyLength?.toInt() ?: 32
        val scheme = EncryptionScheme.getInstance(p.encryptionScheme)
        val iv = ASN1OctetString.getInstance(scheme.parameters).octets

        val digest: Digest = when (kp.prf?.algorithm?.id) {
            "1.2.840.113549.2.7", "1.2.840.113549.2.26" -> SHA1Digest()
            else -> SHA256Digest()
        }
        val gen = PKCS5S2ParametersGenerator(digest)
        gen.init(password.toByteArray(Charsets.UTF_8), salt, iter)
        val kek = gen.generateDerivedParameters(keyLenBytes * 8) as KeyParameter

        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(AESEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(kek, iv))
        val out = ByteArray(cipher.getOutputSize(data.size))
        var len = cipher.processBytes(data, 0, data.size, out, 0)
        len += cipher.doFinal(out, len)
        return out.copyOf(len)
    }
}
