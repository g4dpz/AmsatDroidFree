package uk.me.g4dpz.HamSatDroid.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A server's answer: HTTP status and (size-limited) body text. */
data class DownloadResponse(val status: Int, val body: String)

/** The response body was larger than the app is willing to read. Not worth retrying. */
class ResponseTooLargeException(limit: Int) : IOException("Response larger than $limit bytes")

/** Fetches a URL. Implementations throw [IOException] for network-level failures. */
fun interface KepsDownloader {
    @Throws(IOException::class)
    fun download(url: String): DownloadResponse
}

/**
 * Downloads over https only, without following redirects (Celestrak answers the retired `.com` host with HTTP 301,
 * and following it would double the number of requests), with timeouts, a size limit and a User-Agent that names the
 * app so the server operator can tell who is asking.
 */
class HttpsKepsDownloader(
    private val userAgent: String,
    private val maxBytes: Int = MAX_RESPONSE_BYTES,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : KepsDownloader {

    @Throws(IOException::class)
    override fun download(url: String): DownloadResponse {
        require(url.startsWith("https://")) { "Only https downloads are allowed: $url" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            // Error bodies are only used for messages, so read just a little of them.
            val limit = if (status in 200..299) maxBytes else ERROR_BODY_BYTES
            val body = stream?.use { readLimited(it, limit, failIfLarger = status in 200..299) } ?: ""
            return DownloadResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream, limit: Int, failIfLarger: Boolean): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) {
                if (failIfLarger) throw ResponseTooLargeException(limit)
                out.write(buffer, 0, limit - out.size())
                break
            }
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        /** Celestrak's amateur group is about 41 KB as JSON; 1 MB is generous without being unbounded. */
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        private const val ERROR_BODY_BYTES = 512

        /** e.g. `AmsatDroidFree/1.0.14 (Android 15; amateur radio satellite tracker)` */
        fun userAgent(appVersion: String, androidRelease: String): String =
            "AmsatDroidFree/$appVersion (Android $androidRelease; amateur radio satellite tracker)"
    }
}
