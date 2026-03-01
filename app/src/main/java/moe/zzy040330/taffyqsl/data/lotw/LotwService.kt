package moe.zzy040330.taffyqsl.data.lotw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.parser.AdifParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class LotwService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun query(
        username: String,
        password: String,
        params: LotwQueryParams
    ): Result<List<Map<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = LOTW_QUERY_URL.toHttpUrl().newBuilder()
                .addQueryParameter("login", username)
                .addQueryParameter("password", password)
                .addQueryParameter("qso_query", "1")
                .addQueryParameter("qso_qsl", params.qsoQsl)

            if (params.qsoQsl == "yes" && params.qsoQslSince != null) {
                urlBuilder.addQueryParameter("qso_qslsince", params.qsoQslSince)
            }
            if (params.qsoQsl == "no" && params.qsoQsoRxSince != null) {
                urlBuilder.addQueryParameter("qso_qsorxsince", params.qsoQsoRxSince)
            }
            params.qsoOwnCall?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_owncall", it)
            }
            params.qsoCallsign?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_callsign", it)
            }
            params.qsoMode?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_mode", it)
            }
            params.qsoBand?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_band", it)
            }
            params.qsoDxcc?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_dxcc", it)
            }
            urlBuilder.addQueryParameter(
                "qso_startdate",
                // use start date to force full query since LoTW API is not real RESTful.
                params.qsoStartDate?.takeIf { it.isNotBlank() } ?: "1970-01-01"
            )
            params.qsoEndDate?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("qso_enddate", it)
            }
            if (params.qsoQslDetail) {
                urlBuilder.addQueryParameter("qso_qsldetail", "yes")
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            val bodyBytes = response.body.bytes()
            val bodyText = String(bodyBytes, Charsets.ISO_8859_1)
            
            // The server returns HTML when an error occurs
            if (!bodyText.contains("<EOH>", ignoreCase = true)) {
                throw when {
                    bodyText.contains("Username/password incorrect", ignoreCase = true) ||
                        bodyText.contains("Log on to Logbook of The World", ignoreCase = true) ->
                        LotwException.AuthFailed()
                    else ->
                        LotwException.ServerError(response.code)
                }
            }

            AdifParser(ByteArrayInputStream(bodyBytes)).use { it.readAllQsos() }
        }
    }

    companion object {
        private const val LOTW_QUERY_URL = "https://lotw.arrl.org/lotwuser/lotwreport.adi"

        // mock user-agent
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Safari/537.36"
    }
}
