package com.nexus.stream.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nexus.stream.domain.repository.DownloadServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadServiceControllerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadServiceController {
        override fun ensureRunning() {
            val intent = Intent(context, DownloadService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Refused foreground service start is swallowed
            }
        }

        override fun stop() {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }
    }
