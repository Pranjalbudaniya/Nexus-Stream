package com.nexus.stream.data.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TorrentKeepAliveTest {
    private class FakeServiceLauncher(
        var startResult: Boolean = true,
    ) : ServiceLauncher {
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)

        override fun start(): Boolean {
            startCount.incrementAndGet()
            return startResult
        }

        override fun stop() {
            stopCount.incrementAndGet()
        }
    }

    @Test
    fun KA1_acquireTwiceStartsOnce() {
        val launcher = FakeServiceLauncher()
        val keepAlive = RefCountedKeepAlive(launcher)

        keepAlive.acquire()
        keepAlive.acquire()

        assertEquals(1, launcher.startCount.get())
        assertEquals(0, launcher.stopCount.get())
    }

    @Test
    fun KA2_acquireTwiceReleaseOnceNoStopThenReleaseStopsOnce() {
        val launcher = FakeServiceLauncher()
        val keepAlive = RefCountedKeepAlive(launcher)

        keepAlive.acquire()
        keepAlive.acquire()
        keepAlive.release()

        assertEquals(0, launcher.stopCount.get())

        keepAlive.release()

        assertEquals(1, launcher.stopCount.get())
    }

    @Test
    fun KA3_releaseWithCountZeroDoesNotStopAndNextAcquireStarts() {
        val launcher = FakeServiceLauncher()
        val keepAlive = RefCountedKeepAlive(launcher)

        keepAlive.release()

        assertEquals(0, launcher.stopCount.get())

        keepAlive.acquire()

        assertEquals(1, launcher.startCount.get())
    }

    @Test
    fun KA4_startReturnsFalseNoExceptionAndReleaseStillStops() {
        val launcher = FakeServiceLauncher(startResult = false)
        val keepAlive = RefCountedKeepAlive(launcher)

        keepAlive.acquire()

        assertEquals(1, launcher.startCount.get())

        keepAlive.release()

        assertEquals(1, launcher.stopCount.get())
    }

    @Test
    fun KA5_concurrentAcquireReleasePairsStartsEqualsStops() =
        runBlocking {
            val launcher = FakeServiceLauncher()
            val keepAlive = RefCountedKeepAlive(launcher)
            val iterations = 1000

            val jobs =
                List(iterations) {
                    launch(Dispatchers.Default) {
                        keepAlive.acquire()
                        keepAlive.release()
                    }
                }
            jobs.joinAll()

            assertEquals(launcher.startCount.get(), launcher.stopCount.get())

            // Final count should be 0; releasing at 0 does nothing
            val stopsBefore = launcher.stopCount.get()
            keepAlive.release()
            assertEquals(stopsBefore, launcher.stopCount.get())
        }

    @Test
    fun KA6_fullReleaseThenAcquireStartsAgain() {
        val launcher = FakeServiceLauncher()
        val keepAlive = RefCountedKeepAlive(launcher)

        keepAlive.acquire()
        assertEquals(1, launcher.startCount.get())

        keepAlive.release()
        assertEquals(1, launcher.stopCount.get())

        keepAlive.acquire()
        assertEquals(2, launcher.startCount.get())
    }
}
