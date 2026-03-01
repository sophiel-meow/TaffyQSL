package moe.zzy040330.taffyqsl.data.lotw

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class LotwCredentialManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "lotw_credentials_wrap"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CRED_FILE = "lotw_credentials.enc"
    }

    private val credFile get() = File(context.filesDir, CRED_FILE)

    fun saveCredentials(username: String, password: String) {
        runCatching {
            val json = JSONObject().put("u", username).put("p", password).toString()
            val plaintext = json.toByteArray(Charsets.UTF_8)

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()

            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
            val wrapKey = ks.getKey(KEY_ALIAS, null)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext)

            val out = ByteArrayOutputStream()
            out.write(iv.size)
            out.write(iv)
            out.write(encrypted)
            credFile.writeBytes(out.toByteArray())
        }
    }

    fun loadCredentials(): Pair<String, String>? {
        if (!credFile.exists()) return null
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
            val wrapKey = ks.getKey(KEY_ALIAS, null) ?: return null

            val data = credFile.readBytes()
            val ivLen = data[0].toInt() and 0xFF
            val iv = data.copyOfRange(1, 1 + ivLen)
            val ciphertext = data.copyOfRange(1 + ivLen, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ciphertext)

            val json = JSONObject(String(plain, Charsets.UTF_8))
            Pair(json.getString("u"), json.getString("p"))
        }.getOrNull()
    }

    fun hasCredentials(): Boolean = credFile.exists() && loadCredentials() != null

    fun clearCredentials() {
        runCatching {
            credFile.delete()
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }
}
