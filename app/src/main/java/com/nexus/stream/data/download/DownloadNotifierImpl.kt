package com.nexus.stream.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nexus.stream.MainActivity
import com.nexus.stream.R
import com.nexus.stream.domain.repository.DownloadNotifier
import com.nexus.stream.domain.repository.EXTRA_OPEN_DOWNLOADS
import com.nexus.stream.domain.repository.NOTIFICATION_CHANNEL_EVENTS
import com.nexus.stream.domain.repository.NOTIFICATION_CHANNEL_PROGRESS
import com.nexus.stream.domain.usecase.AggregateNotificationModel
import com.nexus.stream.domain.usecase.DownloadEventNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val NOTIFICATION_ID_AGGREGATE = 10001
private const val NOTIFICATION_ID_PAUSED_BY_SYSTEM = 10002
private const val MAX_PROGRESS_PERCENT = 100
const val ACTION_PAUSE_ALL = "com.nexus.stream.download.PAUSE_ALL"
const val ACTION_RESUME_ALL = "com.nexus.stream.download.RESUME_ALL"

@Singleton
class DownloadNotifierImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadNotifier {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            createChannels()
        }

        private fun createChannels() {
            val progressChannel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_PROGRESS,
                    context.getString(R.string.download_notification_channel_progress),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }

            val eventsChannel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_EVENTS,
                    context.getString(R.string.download_notification_channel_events),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )

            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(eventsChannel)
        }

        override fun showAggregate(model: AggregateNotificationModel) {
            val title = formatString(model.titleRes, model.titleArgs)
            val text = model.textRes?.let { formatString(it, model.textArgs) }

            val publicNotif =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_PROGRESS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(model.publicTitleRes))
                    .build()

            val builder =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_PROGRESS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotif)
                    .setContentIntent(createContentIntent())

            if (model.indeterminate) {
                builder.setProgress(0, 0, true)
            } else if (model.percent != null) {
                builder.setProgress(MAX_PROGRESS_PERCENT, model.percent, false)
            }

            if (model.pauseAllAction) {
                builder.addAction(
                    0,
                    context.getString(R.string.download_notif_pause_all),
                    createServicePendingIntent(ACTION_PAUSE_ALL),
                )
            } else if (model.resumeAllAction) {
                builder.addAction(
                    0,
                    context.getString(R.string.download_notif_resume_all),
                    createServicePendingIntent(ACTION_RESUME_ALL),
                )
            }

            notificationManager.notify(NOTIFICATION_ID_AGGREGATE, builder.build())
        }

        override fun showEvent(notification: DownloadEventNotification) {
            when (notification) {
                is DownloadEventNotification.Completed -> showCompletedEvent(notification)
                is DownloadEventNotification.Failed -> showFailedEvent(notification)
                is DownloadEventNotification.PausedBySystem -> showPausedBySystemEvent(notification)
            }
        }

        private fun showCompletedEvent(notification: DownloadEventNotification.Completed) {
            val publicNotif =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.publicTitleRes))
                    .build()

            val builder =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.titleRes))
                    .setContentText(notification.privateText)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotif)
                    .setContentIntent(createContentIntent())

            notificationManager.notify(notification.downloadId.hashCode(), builder.build())
        }

        private fun showFailedEvent(notification: DownloadEventNotification.Failed) {
            val publicNotif =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.publicTitleRes))
                    .build()

            val reasonText = context.getString(notification.reasonRes)
            val text = "${notification.privateText} · $reasonText"

            val builder =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.titleRes))
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotif)
                    .setContentIntent(createContentIntent())

            notificationManager.notify(notification.downloadId.hashCode(), builder.build())
        }

        private fun showPausedBySystemEvent(notification: DownloadEventNotification.PausedBySystem) {
            val publicNotif =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.publicTitleRes))
                    .build()

            val builder =
                NotificationCompat
                    .Builder(context, NOTIFICATION_CHANNEL_EVENTS)
                    .setSmallIcon(R.drawable.ic_download)
                    .setContentTitle(context.getString(notification.titleRes))
                    .setContentText(context.getString(notification.textRes))
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotif)
                    .setContentIntent(createContentIntent())

            if (notification.resumeActionAvailable) {
                builder.addAction(
                    0,
                    context.getString(R.string.download_notif_resume_all),
                    createServicePendingIntent(ACTION_RESUME_ALL),
                )
            }

            notificationManager.notify(NOTIFICATION_ID_PAUSED_BY_SYSTEM, builder.build())
        }

        override fun cancelAggregate() {
            notificationManager.cancel(NOTIFICATION_ID_AGGREGATE)
        }

        override fun cancelEvent(downloadId: String) {
            notificationManager.cancel(downloadId.hashCode())
        }

        private fun createContentIntent(): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_DOWNLOADS, true)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun createServicePendingIntent(action: String): PendingIntent {
            val intent = Intent(context, DownloadService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun formatString(
            resId: Int,
            args: List<Any>,
        ): String =
            when (args.size) {
                0 -> context.getString(resId)
                1 -> context.getString(resId, args[0])
                else -> context.getString(resId, args[0], args[1])
            }
    }
