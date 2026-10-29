package com.nexus.stream.data.update

import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.UpdateFailure
import com.nexus.stream.domain.repository.ApkDownloader
import com.nexus.stream.domain.repository.ApkFileStore
import com.nexus.stream.domain.repository.DownloadResult
import com.nexus.stream.domain.usecase.isAllowedDownloadUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val DOWNLOAD_READ_TIMEOUT_SECONDS = 30L
const val PROGRESS_UPDATE_INTERVAL_MS = 250L
private const val BUFFER_SIZE = 8192
private const val PERCENT_MULTIPLIER = 100

@Singleton
class ApkDownloaderImpl(
    private val client: OkHttpClient,
    private val fileStore: ApkFileStore,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) : ApkDownloader {
    @Inject
    constructor(
        client: OkHttpClient,
        fileStore: ApkFileStore,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        client = client,
        fileStore = fileStore,
        clock = { System.currentTimeMillis() },
        ioDispatcher = ioDispatcher,
    )

    constructor(
        client: OkHttpClient,
        fileStore: ApkFileStore,
    ) : this(
        client = client,
        fileStore = fileStore,
        clock = { System.currentTimeMillis() },
        ioDispatcher = Dispatchers.IO,
    )

    constructor(
        client: OkHttpClient,
        fileStore: ApkFileStore,
        clock: () -> Long,
    ) : this(
        client = client,
        fileStore = fileStore,
        clock = clock,
        ioDispatcher = Dispatchers.IO,
    )

    override fun cleanup(keep: String?) {
        fileStore.cleanup(keep)
    }

    override suspend fun download(
        asset: ReleaseAsset,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult =
        withContext(ioDispatcher) {
            // Line check 1: Initial URL validation
            if (!isAllowedDownloadUrl(asset.downloadUrl)) {
                return@withContext DownloadResult.Failure(UpdateFailure.DownloadFailed)
            }

            val dir = fileStore.dir()
            val safeName = File(asset.name).name
            val targetFile = File(dir, safeName)
            val reuseResult = checkReuse(asset, targetFile, onProgress)
            if (reuseResult != null) {
                return@withContext reuseResult
            }

            val partFile = File(dir, "$safeName.part")
            performDownload(asset, partFile, targetFile, onProgress)
        }

    private fun checkReuse(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult? {
        if (!isReuseCandidate(asset, targetFile)) return null
        onProgress(asset.sizeBytes, asset.sizeBytes)
        return DownloadResult.Success(targetFile)
    }

    private fun isReuseCandidate(
        asset: ReleaseAsset,
        targetFile: File,
    ): Boolean {
        if (!targetFile.exists() || targetFile.length() != asset.sizeBytes) return false
        val expectedSha = asset.sha256
        return expectedSha == null || computeFileSha256(targetFile).equals(expectedSha, ignoreCase = true)
    }

    private fun performDownload(
        asset: ReleaseAsset,
        partFile: File,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        prepareFiles(partFile, targetFile)
        val downloadClient = buildDownloadClient()
        val request = Request.Builder().url(asset.downloadUrl).build()

        return try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    partFile.delete()
                    return DownloadResult.Failure(UpdateFailure.DownloadFailed)
                }
                streamToFile(response, asset, partFile, targetFile, onProgress)
            }
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (_: Exception) {
            partFile.delete()
            DownloadResult.Failure(UpdateFailure.DownloadFailed)
        }
    }

    private fun buildDownloadClient(): OkHttpClient =
        client
            .newBuilder()
            .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.isRedirect) {
                    val location = response.header("Location")
                    val targetUrl =
                        location
                            ?.let {
                                response.request.url
                                    .resolve(it)
                                    ?.toString() ?: it
                            }.orEmpty()
                    // Line check 2: Redirect URL validation
                    if (targetUrl.isEmpty() || !isAllowedDownloadUrl(targetUrl)) {
                        throw SecurityException("Disallowed redirect: $targetUrl")
                    }
                }
                response
            }.build()

    private fun streamToFile(
        response: Response,
        asset: ReleaseAsset,
        partFile: File,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val body = response.body
        body.byteStream().use { input ->
            FileOutputStream(partFile).use { out ->
                val transferred = copyWithThrottling(input, out, asset.sizeBytes, onProgress)
                if (transferred != asset.sizeBytes) {
                    partFile.delete()
                    return DownloadResult.Failure(UpdateFailure.SizeMismatch)
                }
            }
        }
        return finalizeDownloadedFile(asset, partFile, targetFile, onProgress)
    }

    private fun copyWithThrottling(
        input: InputStream,
        out: FileOutputStream,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Long {
        var downloaded = 0L
        var lastProgressTime = 0L
        var lastPercent = -1
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int

        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
            downloaded += read
            val percent = if (totalBytes > 0) ((downloaded * PERCENT_MULTIPLIER) / totalBytes).toInt() else 0
            val now = clock()
            if (now - lastProgressTime >= PROGRESS_UPDATE_INTERVAL_MS && percent > lastPercent) {
                onProgress(downloaded, totalBytes)
                lastProgressTime = now
                lastPercent = percent
            }
        }
        out.flush()
        return downloaded
    }
}

private fun prepareFiles(
    partFile: File,
    targetFile: File,
) {
    if (partFile.exists()) partFile.delete()
    if (targetFile.exists()) targetFile.delete()
}

private fun finalizeDownloadedFile(
    asset: ReleaseAsset,
    partFile: File,
    targetFile: File,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
): DownloadResult {
    val shaValid = verifyChecksum(asset.sha256, partFile)
    if (!shaValid) {
        partFile.delete()
        return DownloadResult.Failure(UpdateFailure.ChecksumMismatch)
    }
    return moveToTarget(asset.sizeBytes, partFile, targetFile, onProgress)
}

private fun verifyChecksum(
    expectedSha: String?,
    file: File,
): Boolean = expectedSha == null || computeFileSha256(file).equals(expectedSha, ignoreCase = true)

private fun moveToTarget(
    size: Long,
    partFile: File,
    targetFile: File,
    onProgress: (Long, Long) -> Unit,
): DownloadResult {
    if (!partFile.renameTo(targetFile)) {
        partFile.delete()
        return DownloadResult.Failure(UpdateFailure.DownloadFailed)
    }
    onProgress(size, size)
    return DownloadResult.Success(targetFile)
}

private fun computeFileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { java.lang.String.format(Locale.US, "%02x", it) }
}
