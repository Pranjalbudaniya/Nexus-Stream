package com.nexus.stream

import android.app.Application
import com.nexus.stream.di.IoDispatcher
import com.nexus.stream.domain.repository.UpdateManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val AUTO_CHECK_DELAY_MS = 15_000L

@HiltAndroidApp
class NexusApp : Application() {
    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(ioDispatcher + SupervisorJob()).launch {
            try {
                updateManager.onAppStart()
                delay(AUTO_CHECK_DELAY_MS)
                updateManager.autoCheckIfDue()
            } catch (_: Exception) {
                // Must never crash app startup
            }
        }
    }
}
