package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.ApkFacts
import com.nexus.stream.domain.model.InstalledFacts
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.ReleaseInfo
import com.nexus.stream.domain.model.UpdateFailure
import java.io.File

sealed interface ReleaseResult {
    data class Success(
        val release: ReleaseInfo,
    ) : ReleaseResult

    data class Failure(
        val failure: UpdateFailure,
    ) : ReleaseResult
}

sealed interface DownloadResult {
    data class Success(
        val file: File,
    ) : DownloadResult

    data class Failure(
        val failure: UpdateFailure,
    ) : DownloadResult
}

interface ReleaseSource {
    suspend fun fetchLatestRelease(): ReleaseResult
}

interface ApkFileStore {
    fun dir(): File

    fun cleanup(keep: String? = null)
}

interface ApkDownloader {
    suspend fun download(
        asset: ReleaseAsset,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): DownloadResult

    fun cleanup(keep: String? = null)
}

interface PackageArchiveReader {
    fun readArchiveFacts(file: File): ApkFacts?

    fun getInstalledFacts(): InstalledFacts
}

interface ApkInstaller {
    fun canInstall(): Boolean

    suspend fun install(file: File)

    fun abandonStaleSessions()
}

interface NetworkInfo {
    fun isMetered(): Boolean
}

interface StorageInfo {
    fun availableBytes(): Long

    fun availableBytes(root: File): Long = root.usableSpace
}

fun interface Clock {
    fun currentTimeMillis(): Long
}
