package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.GalleryFailure
import java.io.File

sealed interface GalleryExportResult {
    data class Saved(
        val uri: String,
    ) : GalleryExportResult

    data class Failed(
        val failure: GalleryFailure,
    ) : GalleryExportResult
}

interface GalleryExporter {
    suspend fun export(
        file: File,
        displayName: String,
        mimeType: String,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): GalleryExportResult

    suspend fun exists(uri: String): Boolean

    suspend fun delete(uri: String): Boolean

    suspend fun cleanupPending()
}
