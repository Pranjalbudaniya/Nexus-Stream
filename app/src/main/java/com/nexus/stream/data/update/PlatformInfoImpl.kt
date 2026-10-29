package com.nexus.stream.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.nexus.stream.domain.repository.ApkFileStore
import com.nexus.stream.domain.repository.NetworkInfo
import com.nexus.stream.domain.repository.StorageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkInfoImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkInfo {
        override fun isMetered(): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        }
    }

@Singleton
class StorageInfoImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : StorageInfo {
        override fun availableBytes(): Long =
            try {
                val stat = StatFs(context.cacheDir.path)
                stat.availableBytes
            } catch (_: Exception) {
                0L
            }
    }

@Singleton
class ApkFileStoreImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ApkFileStore {
        private val updatesDir = File(context.cacheDir, "updates")

        override fun dir(): File {
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            return updatesDir
        }

        override fun cleanup(keep: String?) {
            if (!updatesDir.exists()) return
            val files = updatesDir.listFiles() ?: return
            for (file in files) {
                if (keep == null || file.name != keep) {
                    file.delete()
                }
            }
        }
    }
