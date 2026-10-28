package com.nexus.stream.data.update

import com.nexus.stream.domain.model.GITHUB_OWNER
import com.nexus.stream.domain.model.GITHUB_REPO
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.repository.AppInfoProvider
import com.nexus.stream.domain.repository.ReleaseResult
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

class GitHubReleaseSourceTest {
    private val fakeAppInfo =
        object : AppInfoProvider {
            override val versionName: String = "1.0.0"
            override val versionCode: Long = 10000L
        }

    private fun clientWithResponse(
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val builder =
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "Error")
                        .body(body.toResponseBody("application/json".toMediaType()))
                headers.forEach { (k, v) -> builder.header(k, v) }
                builder.build()
            }.build()

    @Test
    fun GS1_ok_parsesTheRelease() =
        runTest {
            val json =
                """
                {
                    "tag_name": "v1.0.1",
                    "name": "Version 1.0.1",
                    "body": "Release notes",
                    "html_url": "https://github.com/Pranjalbudaniya/Nexus-Stream/releases/tag/v1.0.1",
                    "assets": [
                        { "name": "a.apk", "size": 1000, "browser_download_url": "https://github.com/a.apk" },
                        { "name": "b.apk", "size": 2000, "browser_download_url": "https://github.com/b.apk" }
                    ]
                }
                """.trimIndent()
            val source = GitHubReleaseSource(clientWithResponse(200, json), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertTrue(result is ReleaseResult.Success)
            val release = (result as ReleaseResult.Success).release
            assertEquals("v1.0.1", release.tag)
            assertEquals("Version 1.0.1", release.title)
            assertEquals("Release notes", release.notes)
            assertEquals(2, release.assets.size)
        }

    @Test
    fun GS2_notFound_isNoRelease() =
        runTest {
            val source = GitHubReleaseSource(clientWithResponse(404), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertEquals(ReleaseResult.Failure(UpdateFailure.NoRelease), result)
        }

    @Test
    fun GS3_rateLimited_carriesTheResetTime() =
        runTest {
            val headers = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1800000000")
            val source = GitHubReleaseSource(clientWithResponse(403, headers = headers), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertEquals(ReleaseResult.Failure(UpdateFailure.RateLimited(1800000000000L)), result)
        }

    @Test
    fun GS4_serverError_isBadResponse() =
        runTest {
            val source500 = GitHubReleaseSource(clientWithResponse(500), fakeAppInfo)
            assertEquals(ReleaseResult.Failure(UpdateFailure.BadResponse), source500.fetchLatestRelease())

            val source403NoHeader = GitHubReleaseSource(clientWithResponse(403), fakeAppInfo)
            assertEquals(ReleaseResult.Failure(UpdateFailure.BadResponse), source403NoHeader.fetchLatestRelease())
        }

    @Test
    fun GS5_malformedJson_isBadResponse() =
        runTest {
            val source = GitHubReleaseSource(clientWithResponse(200, "{not json"), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertEquals(ReleaseResult.Failure(UpdateFailure.BadResponse), result)
        }

    @Test
    fun GS6_missingAssets_givesAnEmptyList() =
        runTest {
            val json = """{"tag_name": "v1.0.1", "name": "1.0.1", "html_url": "https://github.com"}"""
            val source = GitHubReleaseSource(clientWithResponse(200, json), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertTrue(result is ReleaseResult.Success)
            assertEquals(emptyList<Any>(), (result as ReleaseResult.Success).release.assets)
        }

    @Test
    fun GS7_sendsTheRequiredHeaders() =
        runTest {
            var capturedAccept: String? = null
            var capturedApiVersion: String? = null
            var capturedUserAgent: String? = null

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        capturedAccept = request.header("Accept")
                        capturedApiVersion = request.header("X-GitHub-Api-Version")
                        capturedUserAgent = request.header("User-Agent")
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()

            val source = GitHubReleaseSource(client, fakeAppInfo)
            source.fetchLatestRelease()

            assertEquals("application/vnd.github+json", capturedAccept)
            assertEquals("2022-11-28", capturedApiVersion)
            assertTrue(capturedUserAgent?.startsWith("NexusStream/") == true)
        }

    @Test
    fun GS8_ioError_isNetwork() =
        runTest {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { throw IOException("connection reset") }
                    .build()
            val source = GitHubReleaseSource(client, fakeAppInfo)
            assertEquals(ReleaseResult.Failure(UpdateFailure.Network), source.fetchLatestRelease())
        }

    @Test
    fun GS9_timeout_isTimeout() =
        runTest {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { throw InterruptedIOException("timeout") }
                    .build()
            val source = GitHubReleaseSource(client, fakeAppInfo)
            assertEquals(ReleaseResult.Failure(UpdateFailure.Timeout), source.fetchLatestRelease())
        }

    @Test
    fun GS10_assetDigest_isParsed() =
        runTest {
            val json =
                """
                {
                    "tag_name": "v1.0.1",
                    "html_url": "https://github.com",
                    "assets": [
                        { "name": "a.apk", "size": 100, "browser_download_url": "https://github.com/a.apk", "digest": "sha256:AB" },
                        { "name": "b.apk", "size": 200, "browser_download_url": "https://github.com/b.apk" },
                        { "name": "c.apk" }
                    ]
                }
                """.trimIndent()
            val source = GitHubReleaseSource(clientWithResponse(200, json), fakeAppInfo)
            val result = source.fetchLatestRelease()
            assertTrue(result is ReleaseResult.Success)
            val assets = (result as ReleaseResult.Success).release.assets
            assertEquals(2, assets.size)
            assertEquals("ab", assets[0].sha256)
            assertNull(assets[1].sha256)
        }

    @Test
    fun GS11_requestUrl_usesTheRepoConstants() =
        runTest {
            var capturedUrl: String? = null
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        capturedUrl = chain.request().url.toString()
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val source = GitHubReleaseSource(client, fakeAppInfo)
            source.fetchLatestRelease()

            val expected = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            assertEquals(expected, capturedUrl)
        }
}
