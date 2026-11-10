package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PaxsenixLyricsFailureTest {
    private val providers: List<suspend () -> Result<String>> = listOf(
        { PaxsenixLyrics.getAppleMusicLyrics("Track", "Artist", 180) },
        { PaxsenixLyrics.getNeteaseLyrics("Track", "Artist", 180) },
        { PaxsenixLyrics.getSpotifyLyrics("Track", "Artist", 180) },
        { PaxsenixLyrics.getYouTubeLyrics("Track", "Artist", 180) },
        { PaxsenixLyrics.getMusixmatchLyrics("Track", "Artist", 180) },
    )

    @Test
    fun allFiveProvidersDistinguishMissesFromTransientFailures() = runBlocking {
        for (provider in providers) {
            withResponse(404) { assertTrue(provider().exceptionOrNull() is IllegalStateException) }
            for (status in listOf(429, 500, 503, 599)) withResponse(status) {
                assertTrue(provider().exceptionOrNull() is IOException)
            }
            withResponse(failure = IOException("DNS failure")) {
                assertTrue(provider().exceptionOrNull() is IOException)
            }
        }
    }

    @Test
    fun failuresAfterASuccessfulSearchAreRetained() = runBlocking {
        for (provider in providers) {
            for (status in listOf(404, 200, 429, 503)) {
                var lyricsRequested = false
                withResponse(responseForPath = { path ->
                    searchReply(path) ?: run {
                        lyricsRequested = true
                        status to ""
                    }
                }) {
                    val error = provider().exceptionOrNull()
                    assertTrue("Expected a lyrics request; lookup error: $error", lyricsRequested)
                    assertNotNull(error)
                    if (status == 429 || status == 503) assertTrue(error is IOException)
                    else assertFalse(error is IOException)
                }
            }
            withResponse(responseForPath = { path ->
                searchReply(path) ?: throw IOException("lyrics connection reset")
            }) {
                assertEquals("lyrics connection reset", provider().exceptionOrNull()?.message)
            }
        }
    }

    private fun searchReply(path: String): Pair<Int, String>? = when {
        path.startsWith("/v1/catalog/") -> 200 to
            """{"results":{"songs":{"data":[{"id":"1","attributes":{"name":"Track","artistName":"Artist","durationInMillis":180000}}]}}}"""
        path == "/netease/search" -> 200 to
            """{"result":{"songs":[{"id":1,"name":"Track","duration":180000}]}}"""
        path.endsWith("/search") -> 200 to """[{"id":"1","name":"Track","duration":180000}]"""
        else -> null
    }

    private suspend fun withResponse(
        status: Int = 200,
        body: String = "",
        failure: IOException? = null,
        responseForPath: ((String) -> Pair<Int, String>)? = null,
        block: suspend () -> Unit,
    ) {
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) { json() }
            defaultRequest { url("https://lyrics.invalid/") }
            engine { config { addInterceptor { chain ->
                if (failure != null) throw failure
                val (code, text) = responseForPath?.invoke(chain.request().url.encodedPath) ?: (status to body)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .header("Content-Type", "application/json")
                    .code(code).message("test response").body(text.toResponseBody()).build()
            } } }
        }
        PaxsenixLyrics.clientForTests = client
        try { block() } finally { PaxsenixLyrics.clientForTests = null; client.close() }
    }
}
