package com.nexus.stream.data.torrent

import java.io.File

interface TorrentFileLocator {
    fun fileOf(
        infoHash: String,
        relativePath: String,
    ): File
}

fun resolveInside(
    root: File,
    infoHash: String,
    relativePath: String,
): File? {
    if (relativePath.isEmpty() || relativePath.startsWith('/') || relativePath.startsWith('\\')) {
        return null
    }
    return runCatching {
        val targetDir = File(root, infoHash).canonicalFile
        val targetFile = File(targetDir, relativePath).canonicalFile
        val prefix = targetDir.path + File.separator
        if (targetFile.path.startsWith(prefix)) targetFile else null
    }.getOrNull()
}
