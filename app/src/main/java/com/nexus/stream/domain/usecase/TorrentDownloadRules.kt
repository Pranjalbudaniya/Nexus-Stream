package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadPhase
import com.nexus.stream.domain.model.DownloadSource
import com.nexus.stream.domain.model.TorrentFile
import java.io.File

const val MIN_CANDIDATE_BYTES: Long = 50L * 1024 * 1024
const val STALL_AFTER_MS: Long = 5L * 60 * 1000
const val METADATA_TIMEOUT_MS: Long = 120L * 1000
const val TORRENT_DOWNLOAD_POLL_INTERVAL_MS: Long = 1000L
const val TORRENT_SUBDIR: String = "torrents"

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm", "ts", "wmv")
private val SAMPLE_REGEX = Regex("""(^|[/\\._ -])samples?([/\\._ -]|$)""", RegexOption.IGNORE_CASE)

enum class TorrentSessionState {
    CHECKING_FILES,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    UNKNOWN,
}

typealias TorrentState = TorrentSessionState

sealed interface FileChoice {
    data class Chosen(
        val index: Int,
    ) : FileChoice

    data class NeedsUser(
        val candidates: List<TorrentFile>,
    ) : FileChoice

    data object NoVideo : FileChoice
}

fun chooseTorrentFile(
    files: List<TorrentFile>,
    requestedIndex: Int? = null,
    season: Int? = null,
    episode: Int? = null,
): FileChoice {
    val candidates =
        files.filter { file ->
            val ext = file.path.substringAfterLast('.', "").lowercase()
            ext in VIDEO_EXTENSIONS &&
                file.sizeBytes >= MIN_CANDIDATE_BYTES &&
                !SAMPLE_REGEX.containsMatchIn(file.path.lowercase())
        }

    val match = requestedIndex?.let { req -> candidates.firstOrNull { it.index == req } }
    if (match != null) {
        return FileChoice.Chosen(match.index)
    }

    return when {
        candidates.isEmpty() -> FileChoice.NoVideo
        candidates.size == 1 -> FileChoice.Chosen(candidates.first().index)
        else -> resolveMultiCandidateChoice(candidates, season, episode)
    }
}

private fun resolveMultiCandidateChoice(
    candidates: List<TorrentFile>,
    season: Int?,
    episode: Int?,
): FileChoice {
    if (season != null && episode != null) {
        val patternA = Regex("""(?<!\d)S0*$season[ ._-]?E0*$episode(?!\d)""", RegexOption.IGNORE_CASE)
        val patternB = Regex("""(?<!\d)0*${season}x0*$episode(?!\d)""", RegexOption.IGNORE_CASE)
        val patternC = Regex("""[ ._]-[ ._]0*$episode(?!\d)""", RegexOption.IGNORE_CASE)

        val matches =
            candidates.filter { file ->
                val fileName = file.path.substringAfterLast('/').substringAfterLast('\\')
                patternA.containsMatchIn(fileName) ||
                    patternB.containsMatchIn(fileName) ||
                    patternC.containsMatchIn(fileName)
            }

        if (matches.size == 1) {
            return FileChoice.Chosen(matches.first().index)
        }
    }

    return FileChoice.NeedsUser(
        candidates.sortedWith(
            compareByDescending<TorrentFile> { it.sizeBytes }.thenBy { it.index },
        ),
    )
}

fun torrentProgress(
    fileBytesDone: Long,
    fileBytesTotal: Long,
): Float? =
    if (fileBytesTotal <= 0L) {
        null
    } else {
        (fileBytesDone.toFloat() / fileBytesTotal.toFloat()).coerceIn(0f, 1f)
    }

@Suppress("LongParameterList")
fun phaseOf(
    state: TorrentSessionState,
    hasMetadata: Boolean,
    peers: Int,
    bytesDone: Long,
    lastProgressAtMs: Long,
    nowMs: Long,
): DownloadPhase =
    when {
        !hasMetadata -> DownloadPhase.FETCHING_METADATA
        state == TorrentSessionState.CHECKING_FILES -> DownloadPhase.CHECKING_FILES
        nowMs - lastProgressAtMs >= STALL_AFTER_MS -> DownloadPhase.STALLED
        peers == 0 && bytesDone == 0L -> DownloadPhase.CONNECTING
        else -> DownloadPhase.TRANSFERRING
    }

fun metadataTimedOut(
    startedAtMs: Long,
    nowMs: Long,
    hasMetadata: Boolean,
): Boolean = !hasMetadata && (nowMs - startedAtMs >= METADATA_TIMEOUT_MS)

fun torrentSaveDir(
    root: File,
    downloadId: String,
): File = File(File(root, TORRENT_SUBDIR), downloadId)

fun completedTorrentPath(
    saveDir: File,
    backendPath: String,
): File? {
    val invalid =
        backendPath.isEmpty() ||
            backendPath.startsWith("/") ||
            backendPath.startsWith("\\") ||
            backendPath.split('/', '\\').any { it == ".." }

    if (invalid) return null

    val target = File(saveDir, backendPath).normalize()
    val baseCanonical = runCatching { saveDir.canonicalFile }.getOrNull() ?: saveDir.absoluteFile
    val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: target.absoluteFile
    return if (targetCanonical.path.startsWith(baseCanonical.path)) target else null
}

fun runnableTorrentIds(
    candidates: List<DownloadItem>,
    runningHashes: Set<String>,
): List<String> {
    val runningAndPicked = runningHashes.map { it.lowercase() }.toMutableSet()
    val result = mutableListOf<String>()
    for (item in candidates) {
        val source = item.source
        if (source is DownloadSource.Torrent) {
            val hash = source.infoHash.lowercase()
            if (hash !in runningAndPicked) {
                runningAndPicked.add(hash)
                result.add(item.id)
            }
        } else {
            result.add(item.id)
        }
    }
    return result
}
