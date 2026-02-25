package moe.zzy040330.taffyqsl.data.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LotwUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun upload(tq8Data: ByteArray): UploadResult = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "upfile",
                "log.tq8",
                tq8Data.toRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(LOTW_UPLOAD_URL)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseText = response.body.string()

            if (!response.isSuccessful) {
                return@withContext UploadResult.Error("HTTP ${response.code}")
            }

            parseResponse(responseText)
        } catch (e: Exception) {
            UploadResult.Error(e.message ?: "Network error")
        }
    }

    /**
     * Parse the HTML response from LoTW following TrustedQSL's logic.
     *
     * The server embeds the result in HTML comments:
     *   <!-- .UPL. accepted -->
     *   <!-- .UPLMESSAGE. Your log has been received... -->
     *
     * Status == "accepted" (case-insensitive, trimmed) → success.
     * Any other status, or missing UPL comment → error.
     */
    private fun parseResponse(html: String): UploadResult {
        val statusMatch = UPL_STATUS_RE.find(html)
            ?: return UploadResult.Error("Unexpected response from LoTW (no status found)")

        val status = statusMatch.groupValues[1].trim().lowercase()
        val message = UPL_MESSAGE_RE.find(html)?.groupValues?.get(1)?.trim() ?: ""

        return if (status == "accepted") {
            UploadResult.Success(message.ifEmpty { "Log accepted by LoTW" })
        } else {
            val detail = message.ifEmpty { status }
            UploadResult.Error("LoTW rejected the upload: $detail")
        }
    }

    companion object {
        const val LOTW_UPLOAD_URL = "https://lotw.arrl.org/lotw/upload"

        // From TrustedQSL tqsl_prefs.h DEFAULT_UPL_STATUS / DEFAULT_UPL_MESSAGE patterns
        // to check if upload successful
        private val UPL_STATUS_RE = Regex("""<!-- \.UPL\.\s*([^-]+)\s*-->""")
        private val UPL_MESSAGE_RE = Regex("""<!-- \.UPLMESSAGE\.\s*(.+)\s*-->""")

        // Fake User-Agent for picky firewalls
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}

sealed class UploadResult {
    data class Success(val response: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}
