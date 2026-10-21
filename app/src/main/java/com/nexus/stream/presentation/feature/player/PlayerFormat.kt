package com.nexus.stream.presentation.feature.player

import java.util.Locale

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR

fun formatPlaybackTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val remainingSeconds = totalSeconds % SECONDS_PER_HOUR
    val minutes = remainingSeconds / SECONDS_PER_MINUTE
    val seconds = remainingSeconds % SECONDS_PER_MINUTE

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private const val BYTES_IN_KB = 1024.0
private const val BYTES_IN_MB = 1024.0 * 1024.0
private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0

fun formatFileSize(bytes: Long): String =
    when {
        bytes >= BYTES_IN_GB -> String.format(Locale.US, "%.2f GB", bytes / BYTES_IN_GB)
        bytes >= BYTES_IN_MB -> String.format(Locale.US, "%.1f MB", bytes / BYTES_IN_MB)
        else -> String.format(Locale.US, "%.0f KB", bytes / BYTES_IN_KB)
    }
