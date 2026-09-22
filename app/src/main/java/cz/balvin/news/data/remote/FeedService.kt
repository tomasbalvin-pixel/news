package cz.balvin.news.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fetches one feed document and hands it to [RssParser]. */
class FeedService(private val client: OkHttpClient = defaultClient()) {

    sealed interface Result {
        data class Success(
            val feed: ParsedFeed,
            val etag: String?,
            val lastModified: String?,
        ) : Result

        /** The server confirmed the cached copy is current; nothing to parse. */
        data object NotModified : Result

        data class Failure(val message: String) : Result
    }

    suspend fun fetch(url: String, etag: String? = null, lastModified: String? = null): Result =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .apply {
                    etag?.let { header("If-None-Match", it) }
                    lastModified?.let { header("If-Modified-Since", it) }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 304) return@withContext Result.NotModified
                    if (!response.isSuccessful) {
                        return@withContext Result.Failure("HTTP ${response.code}")
                    }
                    val body = response.body ?: return@withContext Result.Failure("Empty response")
                    val bytes = body.byteStream().readAtMost(MAX_BODY_BYTES)
                    if (bytes.isEmpty()) return@withContext Result.Failure("Empty response")

                    Result.Success(
                        feed = RssParser.parse(bytes),
                        etag = response.header("ETag"),
                        lastModified = response.header("Last-Modified"),
                    )
                }
            } catch (e: IOException) {
                Result.Failure(e.message ?: "Network error")
            } catch (e: IllegalArgumentException) {
                Result.Failure("Invalid URL")
            } catch (e: Exception) {
                // A malformed document should disable one source, not the refresh.
                Result.Failure(e.message ?: e.javaClass.simpleName)
            }
        }

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(chunk)
            if (read <= 0) break
            total += read
            if (total > limit) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        private const val USER_AGENT = "Zpravy/1.0 (Android RSS reader)"
        private const val MAX_BODY_BYTES = 8 * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
