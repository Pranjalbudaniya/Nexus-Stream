package com.nexus.stream.data.torrent

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nexus.stream.MainActivity
import com.nexus.stream.R
import com.nexus.stream.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class TorrentStreamService : Service() {
    @Inject
    lateinit var sessionControl: TorrentSessionControl

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                sessionControl.stopAll()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.torrent_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val contentPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val stopIntent =
            Intent(this, TorrentStreamService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_torrent)
            .setContentTitle(getString(R.string.torrent_service_title))
            .setContentText(getString(R.string.torrent_service_text))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                getString(R.string.torrent_service_action_stop),
                stopPendingIntent,
            ).build()
    }

    companion object {
        const val ACTION_STOP = "com.nexus.stream.action.STOP_TORRENT_STREAM"
        private const val CHANNEL_ID = "torrent_stream_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

@Singleton
class AndroidServiceLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ServiceLauncher {
        @Suppress("SwallowedException")
        override fun start(): Boolean {
            val intent = Intent(context, TorrentStreamService::class.java)
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                false
            }
        }

        override fun stop() {
            context.stopService(Intent(context, TorrentStreamService::class.java))
        }
    }
