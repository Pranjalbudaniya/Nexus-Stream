package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.EpisodeHint
import com.nexus.stream.domain.model.TorrentFile

private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts")
private val SAMPLE_REGEX = Regex("""(^|[/\\._ -])samples?([/\\._ -]|$)""", RegexOption.IGNORE_CASE)

fun isVideoFile(path: String): Boolean {
    val ext = path.substringAfterLast('.', "")
    return ext.lowercase() in VIDEO_EXTENSIONS
}

fun pickFile(
    files: List<TorrentFile>,
    hint: EpisodeHint?,
): Int? {
    val videos = files.filter { isVideoFile(it.path) }
    if (videos.isEmpty()) return null

    val nonSampleVideos = videos.filterNot { SAMPLE_REGEX.containsMatchIn(it.path.lowercase()) }
    val pool = nonSampleVideos.ifEmpty { videos }

    return if (hint == null) {
        pool
            .minWithOrNull(
                compareByDescending<TorrentFile> { it.sizeBytes }.thenBy { it.index },
            )?.index
    } else {
        val s = hint.season
        val e = hint.episode
        val patternA = Regex("""(?<!\d)S0*$s[ ._-]?E0*$e(?!\d)""", RegexOption.IGNORE_CASE)
        val patternB = Regex("""(?<!\d)0*${s}x0*$e(?!\d)""", RegexOption.IGNORE_CASE)
        val patternC = Regex("""[ ._]-[ ._]0*$e(?!\d)""", RegexOption.IGNORE_CASE)

        val matches =
            pool.filter { file ->
                val fileName = file.path.substringAfterLast('/').substringAfterLast('\\')
                patternA.containsMatchIn(fileName) ||
                    patternB.containsMatchIn(fileName) ||
                    patternC.containsMatchIn(fileName)
            }

        when {
            matches.isNotEmpty() -> {
                matches
                    .minWithOrNull(
                        compareByDescending<TorrentFile> { it.sizeBytes }.thenBy { it.index },
                    )?.index
            }

            videos.size == 1 -> {
                videos.first().index
            }

            else -> {
                null
            }
        }
    }
}
