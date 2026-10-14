package com.nexus.stream.data.remote.addon

import com.nexus.stream.di.NetworkModule
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AddonRemoteDataSourceTest {

    private val json = NetworkModule.provideJson()
    private val api: AddonApi = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dataSource = AddonRemoteDataSource(api, json, testDispatcher)

    private fun loadFixture(filename: String): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$filename")
            ?: error("Fixture not found: $filename")
        return json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
    }

    private inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit) = runTest {
        try {
            block()
            fail("Expected exception of type ${T::class.java.name} was not thrown")
        } catch (e: Throwable) {
            if (e !is T) {
                throw e
            }
        }
    }

    @Test
    fun manifest_onCinemetaReturnsSuccessWithCinemetaName() = runTest {
        val manifestObj = loadFixture("cinemeta_manifest.json")
        coEvery { api.get(any()) } returns manifestObj

        val outcome = dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json")
        assertTrue(outcome is Outcome.Success)
        val manifest = (outcome as Outcome.Success).value
        assertEquals("Cinemeta", manifest.name)
        assertEquals("3.0.14", manifest.version)
    }

    @Test
    fun manifestWithRaw_returnsBothParsedManifestAndExactRawJson() = runTest {
        val manifestObj = loadFixture("cinemeta_manifest.json")
        coEvery { api.get(any()) } returns manifestObj

        val outcome = dataSource.manifestWithRaw("https://v3-cinemeta.strem.io/manifest.json")
        assertTrue(outcome is Outcome.Success)
        val fetched = (outcome as Outcome.Success).value
        assertEquals("Cinemeta", fetched.manifest.name)
        assertEquals(manifestObj.toString(), fetched.rawJson)
    }

    @Test
    fun safeCall_rethrowsCancellationException() {
        coEvery { api.get(any()) } throws CancellationException("Coroutines cancelled")

        assertFailsWith<CancellationException> {
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json")
        }
    }

    @Test
    fun errorMapping_ioExceptionGivesNetwork() = runTest {
        coEvery { api.get(any()) } throws IOException("Connection reset")
        val outcome = dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json")
        assertEquals(Outcome.Failure(AppError.Network), outcome)
    }

    @Test
    fun errorMapping_timeoutExceptionsGiveTimeout() = runTest {
        coEvery { api.get(any()) } throws SocketTimeoutException("Read timed out")
        assertEquals(
            Outcome.Failure(AppError.Timeout),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )

        coEvery { api.get(any()) } throws InterruptedIOException("Interrupted")
        assertEquals(
            Outcome.Failure(AppError.Timeout),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )
    }

    @Test
    fun errorMapping_httpException404GivesNotFound_and500GivesHttp500() = runTest {
        val notFoundResponse = Response.error<Any>(404, "".toResponseBody("application/json".toMediaTypeOrNull()))
        coEvery { api.get(any()) } throws HttpException(notFoundResponse)
        assertEquals(
            Outcome.Failure(AppError.NotFound),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )

        val serverErrorResponse = Response.error<Any>(500, "".toResponseBody("application/json".toMediaTypeOrNull()))
        coEvery { api.get(any()) } throws HttpException(serverErrorResponse)
        assertEquals(
            Outcome.Failure(AppError.Http(500)),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )
    }

    @Test
    fun errorMapping_serializationExceptionGivesParse() = runTest {
        coEvery { api.get(any()) } throws SerializationException("Invalid json token")
        assertEquals(
            Outcome.Failure(AppError.Parse),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )
    }

    @Test
    fun errorMapping_illegalStateExceptionGivesUnknown() = runTest {
        coEvery { api.get(any()) } throws IllegalStateException("x")
        assertEquals(
            Outcome.Failure(AppError.Unknown("x")),
            dataSource.manifest("https://v3-cinemeta.strem.io/manifest.json"),
        )
    }

    @Test
    fun manifest_invalidUrlGivesInvalidUrlWithoutCallingApi() = runTest {
        val outcome = dataSource.manifest("not a url")
        assertEquals(Outcome.Failure(AppError.InvalidUrl), outcome)
        coVerify(exactly = 0) { api.get(any()) }
    }

    @Test
    fun catalog_badItemDropsBadItemAndCapturesExpectedUrlWithExtras() = runTest {
        val urlSlot = slot<String>()
        val catalogObj = loadFixture("catalog_bad_item.json")
        coEvery { api.get(capture(urlSlot)) } returns catalogObj

        val outcome = dataSource.catalog(
            transportUrl = "https://v3-cinemeta.strem.io/manifest.json",
            type = "movie",
            catalogId = "top",
            extra = mapOf("genre" to "Action", "skip" to "100"),
        )

        assertTrue(outcome is Outcome.Success)
        val page = (outcome as Outcome.Success).value
        assertEquals(1, page.skipped)
        assertEquals(1, page.items.size)
        assertEquals("Valid Movie", page.items.first().name)
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/genre=Action&skip=100.json", urlSlot.captured)
    }
}
