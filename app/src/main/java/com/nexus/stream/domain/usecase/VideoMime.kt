package com.nexus.stream.domain.usecase

fun videoMimeType(path: String): String {
    val fileName = path.substringAfterLast('/')
    val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
    return when (ext) {
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        "m4v" -> "video/x-m4v"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }
}
