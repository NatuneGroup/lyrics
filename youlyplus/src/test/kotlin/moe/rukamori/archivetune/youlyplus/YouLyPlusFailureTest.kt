package moe.rukamori.archivetune.youlyplus

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

class YouLyPlusFailureTest {
    @Test
    fun missesAndTransientFailuresRemainDistinctAcrossMirrors() = runBlocking {
        for (status in listOf(200, 404)) withResponse(status, "{}") {
            assertTrue(YouLyPlus.getLyrics("Track", "Artist").exceptionOrNull() is IllegalStateException)
        }
        for (status in listOf(429, 500, 503, 599)) withResponse(status) {
            assertTrue(YouLyPlus.getLyrics("Track", "Artist").exceptionOrNull() is IOException)
        }
        withResponse(failure = IOException("DNS failure")) {
            assertEquals("DNS failure", YouLyPlus.getLyrics("Track", "Artist").exceptionOrNull()?.message)
        }
    }

    @Test
    fun theLrcFallbackCanRecoverFromATtmlOutage() = runBlocking {
        withResponse(responseForPath = { path ->
            if (path.contains("ttml")) 503 to "" else 200 to """{"type":"Line","lyrics":[{"time":1000,"text":"Hello"}]}"""
        }) { assertTrue(YouLyPlus.getLyrics("Track", "Artist").getOrThrow().contains("Hello")) }
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
        YouLyPlus.clientForTests = client
        try { block() } finally { YouLyPlus.clientForTests = null; client.close() }
    }
}
