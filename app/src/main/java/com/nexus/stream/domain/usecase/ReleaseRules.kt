package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Decision
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.ReleaseInfo
import com.nexus.stream.domain.model.UpdateCandidate
import com.nexus.stream.domain.model.UpdateFailure
import java.net.URI

const val DEFAULT_MAX_NOTES_CHARS = 1500

fun pickApkAsset(
    assets: List<ReleaseAsset>,
    supportedAbis: List<String> = emptyList(),
): ReleaseAsset? {
    val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    return when (apkAssets.size) {
        0 -> null
        1 -> apkAssets.first()
        else -> pickFromMultiple(apkAssets, supportedAbis)
    }
}

private fun pickFromMultiple(
    apkAssets: List<ReleaseAsset>,
    supportedAbis: List<String>,
): ReleaseAsset? {
    for (abi in supportedAbis) {
        val matching = apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
        if (matching != null) return matching
    }
    val universal = apkAssets.filter { it.name.contains("universal", ignoreCase = true) }
    return if (universal.size == 1) universal.first() else null
}

fun isAllowedDownloadUrl(url: String): Boolean =
    try {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        val isHttps = "https".equals(uri.scheme, ignoreCase = true)
        val isAllowedHost = host == "github.com" || host.endsWith(".githubusercontent.com")
        isHttps && uri.userInfo == null && isAllowedHost
    } catch (_: Exception) {
        false
    }

private fun isHex(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

fun parseDigest(digest: String?): String? =
    if (digest != null && digest.startsWith("sha256:", ignoreCase = true)) {
        val hex = digest.substringAfter("sha256:")
        if (isHex(hex)) hex.lowercase() else null
    } else {
        null
    }

fun releaseNotesText(
    body: String?,
    maxChars: Int = DEFAULT_MAX_NOTES_CHARS,
): String? {
    if (body.isNullOrBlank()) return null
    val cleaned = cleanMarkdown(body)
    return when {
        cleaned.isBlank() -> null
        cleaned.length > maxChars -> cleaned.take(maxChars - 1) + "…"
        else -> cleaned
    }
}

private fun cleanMarkdown(raw: String): String {
    var text = raw.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
    text = text.replace(Regex("`([^`]+)`"), "$1")
    text = text.replace(Regex("(?m)^#+\\s*"), "")
    text = text.replace(Regex("(?m)^[-*]\\s+"), "• ")
    text = text.replace(Regex("\\n{3,}"), "\n\n")
    return text.trim()
}

fun decideUpdate(
    currentName: String,
    release: ReleaseInfo,
    supportedAbis: List<String> = emptyList(),
): Decision {
    if (release.isDraft || release.isPrerelease) {
        return Decision.UpToDate
    }
    return evaluateCandidate(currentName, release, supportedAbis)
}

private fun evaluateCandidate(
    currentName: String,
    release: ReleaseInfo,
    supportedAbis: List<String>,
): Decision {
    val candidateVersion = parseVersion(release.tag)
    val currentVersion = parseVersion(currentName)
    if (candidateVersion == null || currentVersion == null) {
        return Decision.Failure(UpdateFailure.BadResponse)
    }
    return when {
        compareVersions(candidateVersion, currentVersion) <= 0 -> {
            Decision.UpToDate
        }

        else -> {
            val asset = pickApkAsset(release.assets, supportedAbis)
            if (asset != null) {
                Decision.Available(
                    UpdateCandidate(
                        tag = release.tag,
                        title = release.title,
                        notes = releaseNotesText(release.notes),
                        releaseUrl = release.htmlUrl,
                        asset = asset,
                    ),
                )
            } else {
                Decision.Failure(UpdateFailure.NoApk)
            }
        }
    }
}
