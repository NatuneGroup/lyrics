package moe.rukamori.archivetune.betterlyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BetterLyricsFailureTest {
    @Test
    fun missesAndTransientFailuresRemainDistinctForBothProviders() = runBlocking {
        for (portato in listOf(false, true)) {
            suspend fun lookup() = if (portato) BetterLyrics.getPortatoLyrics("Track", "Artist")
                else BetterLyrics.getLyrics("Track", "Artist")
            for (status in listOf(404, 200)) withResponse(status) {
                assertTrue(lookup().exceptionOrNull() is IllegalStateException)
            }
            for (status in listOf(429, 500, 503, 599)) withResponse(status) {
                assertTrue(lookup().exceptionOrNull() is IOException)
            }
            withResponse(failure = IOException("DNS failure")) {
                assertEquals("DNS failure", lookup().exceptionOrNull()?.message)
            }
        }
    }

    @Test
    fun aSuccessfulSecondaryEndpointCanRecoverFromATransientFailure() = runBlocking {
        withResponse(responseForPath = { path ->
            if (path == "/getLyrics") 503 to "" else 200 to "<tt>lyrics</tt>"
        }) { assertEquals("<tt>lyrics</tt>", BetterLyrics.getLyrics("Track", "Artist").getOrThrow()) }
    }

    private suspend fun withResponse(
        status: Int = 200,
        body: String = "",
        failure: IOException? = null,
        responseForPath: ((String) -> Pair<Int, String>)? = null,
        block: suspend () -> Unit,
    ) {
        val client = HttpClient(OkHttp) {
            defaultRequest { url("https://lyrics.invalid/") }
            engine { config { addInterceptor { chain ->
                if (failure != null) throw failure
                val (code, text) = responseForPath?.invoke(chain.request().url.encodedPath) ?: (status to body)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("test response").body(text.toResponseBody()).build()
            } } }
        }
        BetterLyrics.clientForTests = client
        try { block() } finally { BetterLyrics.clientForTests = null; client.close() }
    }
}
