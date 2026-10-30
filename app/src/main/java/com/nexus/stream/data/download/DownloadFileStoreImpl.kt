package com.nexus.stream.data.download

import android.content.Context
import com.nexus.stream.domain.repository.DownloadFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadFileStoreImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadFileStore {
        override val root: File
            get() {
                val external = context.getExternalFilesDir("downloads")
                val dir =
                    if (external != null && external.canWrite()) {
                        external
                    } else {
                        File(context.filesDir, "downloads")
                    }
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                return dir
            }

        override fun partFile(id: String): File = File(root, "$id.part")

        override fun finishedFile(
            id: String,
            extension: String,
        ): File = File(root, "$id.$extension")

        override fun fileOf(id: String): File? {
            val dir = root
            return dir
                .listFiles { _, name ->
                    name.startsWith("$id.") && !name.endsWith(".part")
                }?.firstOrNull()
        }

        override fun complete(
            id: String,
            extension: String,
        ): File {
            val part = partFile(id)
            val dest = finishedFile(id, extension)
            if (part.exists()) {
                part.renameTo(dest)
            }
            return dest
        }

        override fun delete(id: String) {
            val dir = root
            dir
                .listFiles { _, name ->
                    name == "$id.part" || (name.startsWith("$id.") && !name.endsWith(".part"))
                }?.forEach { it.delete() }
        }

        override fun deletePart(id: String) {
            val part = partFile(id)
            if (part.exists()) {
                part.delete()
            }
        }

        override fun listAllFiles(): List<File> = root.listFiles()?.toList().orEmpty()
    }
