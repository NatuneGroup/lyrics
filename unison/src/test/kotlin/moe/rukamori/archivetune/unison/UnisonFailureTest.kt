package moe.rukamori.archivetune.unison

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

class UnisonFailureTest {
    @Test
    fun anOrdinaryMissDoesNotReportAFailure() = runBlocking {
        for (status in listOf(200, 404)) withResponse(status, "{\"success\":true,\"data\":[]}") {
            assertTrue(Unison.getLyrics("video", "Track", "Artist").exceptionOrNull() is IllegalStateException)
            val failures = mutableListOf<Throwable>()
            Unison.getAllLyrics("video", "Track", "Artist", onFailure = failures::add) { fail("Unexpected lyrics") }
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun transientFailuresReachBothLookupPaths() = runBlocking {
        for (status in listOf(429, 500, 503, 599)) withResponse(status) { assertFailedLookups() }
        withResponse(failure = IOException("DNS failure")) { assertFailedLookups() }
    }

    @Test
    fun failedHydrationAndVideoFallbacksAreNotMisses() = runBlocking {
        val summary = """{"success":true,"data":[{"id":1,"song":"Track","artist":"Artist","format":"lrc","syncType":"line"}]}"""
        withResponse(responseForPath = { path -> if (path.endsWith("search")) 200 to summary else 503 to "" }) {
            assertFailedLookups()
        }
        withResponse(responseForPath = { path -> if (path.endsWith("search")) 200 to "{\"success\":true,\"data\":[]}" else 503 to "" }) {
            assertFailedLookups()
        }
    }

    private suspend fun assertFailedLookups() {
        assertTrue(Unison.getLyrics("video", "Track", "Artist").exceptionOrNull() is IOException)
        val failures = mutableListOf<Throwable>()
        Unison.getAllLyrics("video", "Track", "Artist", onFailure = failures::add) { fail("Unexpected lyrics") }
        assertEquals(1, failures.size)
        assertTrue(failures.single() is IOException)
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
        Unison.clientForTests = client
        try { block() } finally { Unison.clientForTests = null; client.close() }
    }
}
