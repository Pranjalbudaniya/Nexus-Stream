package com.nexus.stream.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.ApkInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstallerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ApkInstaller {
        constructor(
            context: Context,
        ) : this(context, Dispatchers.IO)

        override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        override fun abandonStaleSessions() {
            val installer = context.packageManager.packageInstaller
            val sessions = installer.mySessions
            for (session in sessions) {
                try {
                    installer.abandonSession(session.sessionId)
                } catch (_: IllegalStateException) {
                    // Ignore if session already closed
                }
            }
        }

        override suspend fun install(file: File) {
            withContext(ioDispatcher) {
                abandonStaleSessions()
                val installer = context.packageManager.packageInstaller
                val params =
                    PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                        setSize(file.length())
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }

                val sessionId = installer.createSession(params)
                var session: PackageInstaller.Session? = null
                var error: Throwable? = null
                try {
                    session = installer.openSession(sessionId)
                    writeApkToSession(session, file)
                    val intent =
                        Intent(context, InstallResultReceiver::class.java).apply {
                            setPackage(context.packageName)
                        }
                    val pendingIntent =
                        PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            intent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    session.commit(pendingIntent.intentSender)
                } catch (e: IOException) {
                    abandonSessionSafely(session)
                    error = e
                } catch (e: SecurityException) {
                    abandonSessionSafely(session)
                    error = e
                } catch (e: IllegalStateException) {
                    abandonSessionSafely(session)
                    error = e
                } finally {
                    session?.close()
                }
                error?.let { throw it }
            }
        }

        private fun abandonSessionSafely(session: PackageInstaller.Session?) {
            try {
                session?.abandon()
            } catch (_: IllegalStateException) {
                // Ignore if session already closed
            }
        }

        private fun writeApkToSession(
            session: PackageInstaller.Session,
            file: File,
        ) {
            session.openWrite("package", 0, file.length()).use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }
        }
    }
