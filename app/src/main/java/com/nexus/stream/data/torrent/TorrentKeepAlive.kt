package com.nexus.stream.data.torrent

import javax.inject.Inject
import javax.inject.Singleton

interface TorrentKeepAlive {
    fun acquire()

    fun release()
}

interface ServiceLauncher {
    fun start(): Boolean

    fun stop()
}

@Singleton
class RefCountedKeepAlive
    @Inject
    constructor(
        private val launcher: ServiceLauncher,
    ) : TorrentKeepAlive {
        private val lock = Any()
        private var count = 0

        override fun acquire() {
            synchronized(lock) {
                if (count == 0) {
                    launcher.start()
                }
                count++
            }
        }

        override fun release() {
            synchronized(lock) {
                if (count > 0) {
                    count--
                    if (count == 0) {
                        launcher.stop()
                    }
                }
            }
        }
    }
