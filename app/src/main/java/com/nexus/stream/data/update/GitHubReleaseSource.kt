package com.nexus.stream.data.update

import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.GITHUB_OWNER
import com.nexus.stream.domain.model.GITHUB_REPO
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.ReleaseInfo
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.repository.AppInfoProvider
import com.nexus.stream.domain.repository.ReleaseResult
import com.nexus.stream.domain.repository.ReleaseSource
import com.nexus.stream.domain.usecase.parseDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val GITHUB_API_TIMEOUT_SECONDS = 15L
const val USER_AGENT_PREFIX = "NexusStream/"
private const val GITHUB_API_VERSION = "2022-11-28"
private const val ACCEPT_HEADER = "application/vnd.github+json"
private const val RATE_LIMIT_MULTIPLIER = 1000L
private const val HTTP_OK = 200
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto>? = null,
)

@Serializable
private data class GitHubAssetDto(
    val name: String? = null,
    val size: Long? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    val digest: String? = null,
)

@Singleton
class GitHubReleaseSource
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val appInfoProvider: AppInfoProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ReleaseSource {
        constructor(
            client: OkHttpClient,
            appInfoProvider: AppInfoProvider,
        ) : this(client, appInfoProvider, Dispatchers.IO)

        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
            }

        override suspend fun fetchLatestRelease(): ReleaseResult =
            withContext(ioDispatcher) {
                val request = buildRequest()
                try {
                    executeRequest(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: InterruptedIOException) {
                    ReleaseResult.Failure(UpdateFailure.Timeout)
                } catch (_: IOException) {
                    ReleaseResult.Failure(UpdateFailure.Network)
                } catch (_: Exception) {
                    ReleaseResult.Failure(UpdateFailure.BadResponse)
                }
            }

        private fun buildRequest(): Request {
            val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val versionName = appInfoProvider.versionName ?: "unknown"
            return Request
                .Builder()
                .url(url)
                .header("Accept", ACCEPT_HEADER)
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "$USER_AGENT_PREFIX$versionName")
                .build()
        }

        private fun executeRequest(request: Request): ReleaseResult {
            val callClient =
                client
                    .newBuilder()
                    .callTimeout(GITHUB_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build()

            callClient.newCall(request).execute().use { response ->
                return mapResponse(response)
            }
        }

        private fun mapResponse(response: Response): ReleaseResult =
            when (response.code) {
                HTTP_OK -> parseResponseBody(response)
                HTTP_NOT_FOUND -> ReleaseResult.Failure(UpdateFailure.NoRelease)
                HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> handleRateLimit(response)
                else -> ReleaseResult.Failure(UpdateFailure.BadResponse)
            }

        private fun handleRateLimit(response: Response): ReleaseResult {
            val remaining = response.header("X-RateLimit-Remaining")
            val reset = response.header("X-RateLimit-Reset")
            return if (remaining == "0") {
                val resetMs = reset?.toLongOrNull()?.let { it * RATE_LIMIT_MULTIPLIER }
                ReleaseResult.Failure(UpdateFailure.RateLimited(resetMs))
            } else {
                ReleaseResult.Failure(UpdateFailure.BadResponse)
            }
        }

        private fun parseResponseBody(response: Response): ReleaseResult {
            val bodyString = response.body.string()
            return try {
                val dto = json.decodeFromString(GitHubReleaseDto.serializer(), bodyString)
                dtoToReleaseInfo(dto)
            } catch (_: Exception) {
                ReleaseResult.Failure(UpdateFailure.BadResponse)
            }
        }

        private fun dtoToReleaseInfo(dto: GitHubReleaseDto): ReleaseResult {
            val tag = dto.tagName
            val htmlUrl = dto.htmlUrl
            if (tag == null || htmlUrl == null) {
                return ReleaseResult.Failure(UpdateFailure.BadResponse)
            }
            val validAssets =
                dto.assets.orEmpty().mapNotNull { assetDto ->
                    val name = assetDto.name ?: return@mapNotNull null
                    val size = assetDto.size ?: return@mapNotNull null
                    val downloadUrl = assetDto.browserDownloadUrl ?: return@mapNotNull null
                    ReleaseAsset(
                        name = name,
                        sizeBytes = size,
                        downloadUrl = downloadUrl,
                        sha256 = parseDigest(assetDto.digest),
                    )
                }
            val release =
                ReleaseInfo(
                    tag = tag,
                    title = dto.name ?: tag,
                    notes = dto.body,
                    htmlUrl = htmlUrl,
                    isDraft = dto.draft,
                    isPrerelease = dto.prerelease,
                    assets = validAssets,
                )
            return ReleaseResult.Success(release)
        }
    }
