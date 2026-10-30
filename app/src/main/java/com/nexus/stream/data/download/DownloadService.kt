package com.nexus.stream.data.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexus.stream.R
import com.nexus.stream.domain.model.DownloadState
import com.nexus.stream.domain.repository.DownloadManager
import com.nexus.stream.domain.repository.DownloadNotifier
import com.nexus.stream.domain.repository.NOTIFICATION_CHANNEL_PROGRESS
import com.nexus.stream.domain.usecase.aggregateNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NOTIFICATION_THROTTLE_MS = 1000L

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var downloadNotifier: DownloadNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotificationTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInitialForeground()
        observeDownloads()
    }

    private fun startInitialForeground() {
        val initialNotif =
            NotificationCompat
                .Builder(this, NOTIFICATION_CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.download_notif_public))
                .setOngoing(true)
                .build()

        val foregroundType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }

        ServiceCompat.startForeground(this, NOTIFICATION_ID_AGGREGATE, initialNotif, foregroundType)
    }

    private fun observeDownloads() {
        serviceScope.launch {
            combine(downloadManager.items, downloadManager.live) { items, live ->
                Pair(items, live)
            }.collect { (items, live) ->
                val hasActive =
                    items.any {
                        it.state == DownloadState.DOWNLOADING ||
                            it.state == DownloadState.QUEUED ||
                            it.state == DownloadState.WAITING_FOR_WIFI ||
                            it.state == DownloadState.WAITING_FOR_NETWORK
                    }

                if (!hasActive) {
                    downloadNotifier.cancelAggregate()
                    stopSelf()
                    return@collect
                }

                val model = aggregateNotification(items, live)
                val now = System.currentTimeMillis()
                if (model != null && now - lastNotificationTime >= NOTIFICATION_THROTTLE_MS) {
                    lastNotificationTime = now
                    downloadNotifier.showAggregate(model)
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> downloadManager.pauseAll()
            ACTION_RESUME_ALL -> downloadManager.resumeAll()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        serviceScope.launch {
            downloadManager.onServiceTimeout()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
