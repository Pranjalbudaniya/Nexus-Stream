package com.nexus.stream.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.model.GalleryFailure
import com.nexus.stream.domain.repository.GalleryExportResult
import com.nexus.stream.domain.repository.GalleryExporter
import com.nexus.stream.domain.usecase.GALLERY_RELATIVE_PATH
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreGalleryExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : GalleryExporter {
        override suspend fun export(
            file: File,
            displayName: String,
            mimeType: String,
            onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit,
        ): GalleryExportResult =
            withContext(ioDispatcher) {
                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Video.Media.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                val itemUri =
                    try {
                        resolver.insert(collection, values)
                    } catch (_: Exception) {
                        null
                    } ?: return@withContext GalleryExportResult.Failed(GalleryFailure.UNAVAILABLE)

                try {
                    val outputStream =
                        resolver.openOutputStream(itemUri)
                            ?: run {
                                resolver.delete(itemUri, null, null)
                                return@withContext GalleryExportResult.Failed(GalleryFailure.WRITE_FAILED)
                            }

                    val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
                    var copied = 0L
                    val total = file.length()

                    file.inputStream().use { input ->
                        outputStream.use { output ->
                            var bytesRead = input.read(buffer)
                            while (bytesRead != -1) {
                                output.write(buffer, 0, bytesRead)
                                copied += bytesRead
                                onProgress(copied, total)
                                bytesRead = input.read(buffer)
                            }
                            output.flush()
                        }
                    }

                    val publishValues =
                        ContentValues().apply {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                        }
                    resolver.update(itemUri, publishValues, null, null)
                    GalleryExportResult.Saved(itemUri.toString())
                } catch (e: CancellationException) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw e
                } catch (_: Exception) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    GalleryExportResult.Failed(GalleryFailure.WRITE_FAILED)
                }
            }

        override suspend fun exists(uri: String): Boolean =
            withContext(ioDispatcher) {
                val parsedUri = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
                val resolver = context.contentResolver
                try {
                    resolver
                        .query(
                            parsedUri,
                            arrayOf(MediaStore.Video.Media._ID),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            cursor.moveToFirst()
                        } ?: false
                } catch (_: Exception) {
                    false
                }
            }

        override suspend fun delete(uri: String): Boolean =
            withContext(ioDispatcher) {
                val parsedUri = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
                val resolver = context.contentResolver
                try {
                    resolver.delete(parsedUri, null, null) > 0
                } catch (_: Exception) {
                    false
                }
            }

        override suspend fun cleanupPending() {
            withContext(ioDispatcher) {
                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection =
                    "${MediaStore.Video.Media.IS_PENDING} = 1 AND ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("$GALLERY_RELATIVE_PATH%")
                runCatching {
                    resolver.delete(collection, selection, selectionArgs)
                }
            }
        }
    }
