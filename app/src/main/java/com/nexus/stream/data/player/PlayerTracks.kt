package com.nexus.stream.data.player

data class PlayerTrack(
    val id: String,
    val name: String,
)

data class PlayerTracks(
    val audio: List<PlayerTrack> = emptyList(),
    val subtitles: List<PlayerTrack> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
)

enum class VideoScale { FIT, CROP, STRETCH, RATIO_16_9, RATIO_4_3, ORIGINAL }

fun VideoScale.next(): VideoScale =
    when (this) {
        VideoScale.FIT -> VideoScale.CROP
        VideoScale.CROP -> VideoScale.STRETCH
        VideoScale.STRETCH -> VideoScale.RATIO_16_9
        VideoScale.RATIO_16_9 -> VideoScale.RATIO_4_3
        VideoScale.RATIO_4_3 -> VideoScale.ORIGINAL
        VideoScale.ORIGINAL -> VideoScale.FIT
    }

fun parseVideoScale(raw: String?): VideoScale =
    if (raw == null) {
        VideoScale.FIT
    } else {
        try {
            VideoScale.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            VideoScale.FIT
        }
    }

private const val SPEED_HALF = 0.5f
private const val SPEED_THREE_QUARTERS = 0.75f
private const val SPEED_NORMAL = 1.0f
private const val SPEED_ONE_AND_QUARTER = 1.25f
private const val SPEED_ONE_AND_HALF = 1.5f
private const val SPEED_DOUBLE = 2.0f

val SPEED_OPTIONS: List<Float> =
    listOf(
        SPEED_HALF,
        SPEED_THREE_QUARTERS,
        SPEED_NORMAL,
        SPEED_ONE_AND_QUARTER,
        SPEED_ONE_AND_HALF,
        SPEED_DOUBLE,
    )

fun formatSpeed(rate: Float): String =
    when (rate) {
        SPEED_HALF -> {
            "0.5x"
        }

        SPEED_THREE_QUARTERS -> {
            "0.75x"
        }

        SPEED_NORMAL -> {
            "1x"
        }

        SPEED_ONE_AND_QUARTER -> {
            "1.25x"
        }

        SPEED_ONE_AND_HALF -> {
            "1.5x"
        }

        SPEED_DOUBLE -> {
            "2x"
        }

        else -> {
            val formatted = if (rate % 1f == 0f) rate.toInt().toString() else rate.toString()
            "${formatted}x"
        }
    }

fun trackLabel(
    track: PlayerTrack,
    index: Int,
): String {
    val trimmed = track.name.trim()
    return if (trimmed.isEmpty()) "Track ${index + 1}" else trimmed
}
