package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ApkFacts
import com.nexus.stream.domain.model.InstalledFacts
import com.nexus.stream.domain.model.UpdateFailure

const val INSTALL_STORAGE_MARGIN_BYTES = 50L * 1024 * 1024

@Suppress("LongParameterList")
fun verifyApk(
    apk: ApkFacts?,
    installed: InstalledFacts,
    expectedSize: Long,
    actualSize: Long,
    expectedSha256: String?,
    actualSha256: String?,
): UpdateFailure? =
    when {
        apk == null -> UpdateFailure.InvalidApk

        actualSize != expectedSize -> UpdateFailure.SizeMismatch

        expectedSha256 != null &&
            !expectedSha256.equals(
                actualSha256,
                ignoreCase = true,
            )
        -> UpdateFailure.ChecksumMismatch

        apk.packageName != installed.packageName -> UpdateFailure.PackageMismatch

        apk.versionCode <= installed.versionCode -> UpdateFailure.NotNewer

        apk.signerCertSha256.none { installed.trustsCert(it) } -> UpdateFailure.SignatureMismatch

        else -> null
    }

fun hasEnoughSpace(
    availableBytes: Long,
    apkSize: Long,
): Boolean {
    val requiredBytes = 2 * apkSize + INSTALL_STORAGE_MARGIN_BYTES
    return availableBytes >= requiredBytes
}

fun canSelfUpdate(
    installedPackage: String,
    releasePackage: String = installedPackage.removeSuffix(".debug"),
): Boolean {
    if (installedPackage.endsWith(".debug")) return false
    return installedPackage == releasePackage
}
