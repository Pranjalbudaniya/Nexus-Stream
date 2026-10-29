package com.nexus.stream.domain.model

data class ParsedVersion(
    val parts: List<Int>,
    val pre: String? = null,
)

data class ReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String? = null,
)

data class ReleaseInfo(
    val tag: String,
    val title: String,
    val notes: String? = null,
    val htmlUrl: String,
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

data class UpdateCandidate(
    val tag: String,
    val title: String,
    val notes: String? = null,
    val releaseUrl: String,
    val asset: ReleaseAsset,
)

sealed interface UpdateFailure {
    data object Network : UpdateFailure

    data object Timeout : UpdateFailure

    data class RateLimited(
        val resetAtMs: Long? = null,
    ) : UpdateFailure

    data object NoRelease : UpdateFailure

    data object NoApk : UpdateFailure

    data object BadResponse : UpdateFailure

    data object InsufficientStorage : UpdateFailure

    data object DownloadFailed : UpdateFailure

    data object SizeMismatch : UpdateFailure

    data object ChecksumMismatch : UpdateFailure

    data object InvalidApk : UpdateFailure

    data object PackageMismatch : UpdateFailure

    data object NotNewer : UpdateFailure

    data object SignatureMismatch : UpdateFailure

    data object InstallBlocked : UpdateFailure

    data object InstallIncompatible : UpdateFailure

    data object InstallConflict : UpdateFailure

    data object InstallStorage : UpdateFailure

    data object InstallInvalid : UpdateFailure

    data object InstallFailed : UpdateFailure

    data object Unsupported : UpdateFailure
}

sealed interface UpdateState {
    data class Idle(
        val lastCheckedAt: Long? = null,
    ) : UpdateState

    data object Checking : UpdateState

    data class UpToDate(
        val checkedAt: Long,
    ) : UpdateState

    data class Available(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class ConfirmMetered(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class Downloading(
        val candidate: UpdateCandidate,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState

    data class Verifying(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class NeedsInstallPermission(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class Installing(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class AwaitingConfirmation(
        val candidate: UpdateCandidate,
    ) : UpdateState

    data class Failed(
        val failure: UpdateFailure,
        val candidate: UpdateCandidate? = null,
    ) : UpdateState

    data object Unsupported : UpdateState
}

sealed interface UpdateEffect {
    data class OpenUrl(
        val url: String,
    ) : UpdateEffect

    data class Updated(
        val versionName: String,
    ) : UpdateEffect
}

data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    val signerCertSha256: Set<String>,
)

class InstalledFacts(
    val packageName: String,
    val versionCode: Long,
    val trustsCert: (String) -> Boolean,
)

sealed interface Decision {
    data object UpToDate : Decision

    data class Available(
        val candidate: UpdateCandidate,
    ) : Decision

    data class Failure(
        val failure: UpdateFailure,
    ) : Decision
}

typealias UpdateDecision = Decision
