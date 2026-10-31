package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.DownloadItem
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.model.GalleryState

const val GALLERY_FILE_NAME_MAX_CHARS = 120
const val GALLERY_RELATIVE_PATH = "Movies/Nexus Stream"
private const val DEFAULT_VIDEO_NAME = "Nexus Stream video"
private const val DEFAULT_EXTENSION = "mp4"

private val ILLEGAL_CHARS_REGEX = Regex("""[/\\:*?"<>|\x00-\x1F\x7F]""")
private val MULTIPLE_SPACES_REGEX = Regex("""\s+""")
private val EXTENSION_REGEX = Regex("""^[a-z0-9]{1,5}$""")

enum class GalleryAction {
    SAVE,
    SAVING,
    REMOVE,
    TRY_AGAIN,
    NONE,
}

fun galleryFileName(
    title: String?,
    metaName: String?,
    season: Int?,
    episode: Int?,
    extension: String,
): String {
    val cleanExt =
        extension
            .trim()
            .lowercase()
            .removePrefix(".")
            .takeIf { EXTENSION_REGEX.matches(it) }
            ?: DEFAULT_EXTENSION

    val rawBaseName =
        if (season != null && episode != null) {
            val series = metaName?.ifBlank { null } ?: title?.ifBlank { null }.orEmpty()
            val s = season.toString().padStart(2, '0')
            val e = episode.toString().padStart(2, '0')
            if (series.isNotBlank()) "$series - S${s}E$e" else "S${s}E$e"
        } else {
            title?.ifBlank { null } ?: metaName?.ifBlank { null }.orEmpty()
        }

    val sanitized =
        rawBaseName
            .replace(ILLEGAL_CHARS_REGEX, " ")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim { it <= ' ' || it == '.' }

    val capped =
        if (sanitized.length > GALLERY_FILE_NAME_MAX_CHARS) {
            sanitized.take(GALLERY_FILE_NAME_MAX_CHARS).trim { it <= ' ' || it == '.' }
        } else {
            sanitized
        }

    val finalBaseName = capped.ifBlank { DEFAULT_VIDEO_NAME }
    return "$finalBaseName.$cleanExt"
}

fun galleryMimeType(extension: String): String {
    val cleanExt = extension.trim().lowercase().removePrefix(".")
    return when (cleanExt) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "ts" -> "video/mp2t"
        "wmv" -> "video/x-ms-wmv"
        else -> "video/mp4"
    }
}

fun hasEnoughStorageForExport(
    availableBytes: Long,
    fileSizeBytes: Long,
): Boolean = availableBytes >= fileSizeBytes + STORAGE_RESERVE_BYTES

fun galleryActionFor(item: DownloadItem): GalleryAction =
    if (item.state != DownloadState.COMPLETED) {
        GalleryAction.NONE
    } else {
        when (item.galleryState) {
            GalleryState.NONE -> GalleryAction.SAVE
            GalleryState.SAVING -> GalleryAction.SAVING
            GalleryState.SAVED -> GalleryAction.REMOVE
            GalleryState.FAILED -> GalleryAction.TRY_AGAIN
        }
    }
