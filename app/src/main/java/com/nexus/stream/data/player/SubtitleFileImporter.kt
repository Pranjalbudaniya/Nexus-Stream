package com.nexus.stream.data.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.usecase.MAX_SUBTITLE_BYTES
import com.nexus.stream.domain.usecase.isAllowedSubtitleFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Imported(
        val path: String,
    ) : ImportResult

    data object BadType : ImportResult

    data object TooLarge : ImportResult

    data object Failed : ImportResult
}

interface SubtitleFileImporter {
    suspend fun import(uri: String): ImportResult

    fun cleanup()
}

@Singleton
class AndroidSubtitleFileImporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : SubtitleFileImporter {
        private val importedFiles = Collections.synchronizedSet(mutableSetOf<File>())

        override suspend fun import(uri: String): ImportResult =
            withContext(io) {
                val parsedUri =
                    try {
                        Uri.parse(uri)
                    } catch (_: IllegalArgumentException) {
                        return@withContext ImportResult.Failed
                    }

                val displayName = resolveDisplayName(parsedUri)
                if (!isAllowedSubtitleFile(displayName)) {
                    return@withContext ImportResult.BadType
                }

                val ext = displayName?.substringAfterLast('.', "srt") ?: "srt"
                val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                val destFile = File(subDir, "${UUID.randomUUID()}.$ext")

                try {
                    val inputStream =
                        context.contentResolver.openInputStream(parsedUri)
                            ?: return@withContext ImportResult.Failed

                    inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var totalBytes = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                totalBytes += read
                                if (totalBytes > MAX_SUBTITLE_BYTES) {
                                    destFile.delete()
                                    return@withContext ImportResult.TooLarge
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    importedFiles.add(destFile)
                    ImportResult.Imported(destFile.absolutePath)
                } catch (_: IOException) {
                    destFile.delete()
                    ImportResult.Failed
                } catch (_: SecurityException) {
                    destFile.delete()
                    ImportResult.Failed
                }
            }

        private fun resolveDisplayName(parsedUri: Uri): String? {
            if (parsedUri.scheme != "content") {
                return parsedUri.lastPathSegment
            }
            return queryContentDisplayName(parsedUri) ?: parsedUri.lastPathSegment
        }

        private fun queryContentDisplayName(uri: Uri): String? =
            try {
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val hasRow = cursor.moveToFirst()
                        val nameIndex = if (hasRow) cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) else -1
                        if (nameIndex != -1) cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } else null
                    }
            } catch (_: Exception) {
                null
            }

        override fun cleanup() {
            val toDelete =
                synchronized(importedFiles) {
                    val copy = importedFiles.toList()
                    importedFiles.clear()
                    copy
                }
            for (file in toDelete) {
                try {
                    file.delete()
                } catch (_: Exception) {
                    // Ignore cleanup failures
                }
            }
        }
    }
