package com.nexus.stream.data.torrent

import com.nexus.stream.data.stream.SourceIoException
import com.nexus.stream.data.stream.SourceStallException
import com.nexus.stream.domain.model.TorrentFailReason
import com.nexus.stream.domain.usecase.piecesForRange
import com.nexus.stream.domain.usecase.windowPieces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class TorrentByteSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mib = 1024L * 1024L

    private fun createTempFileWithBytes(size: Int): File {
        val file = tempFolder.newFile()
        FileOutputStream(file).use { out ->
            val data = ByteArray(size) { (it % 100).toByte() }
            out.write(data)
        }
        return file
    }

    private fun createSparseTempFile(size: Long): File {
        val file = tempFolder.newFile()
        java.io.RandomAccessFile(file, "rw").use { it.setLength(size) }
        return file
    }

    @Test
    fun BS1_firstReadAtZero_allPiecesPresent_returnsExactBytesAndPrioritizes() =
        runTest {
            val file = createTempFileWithBytes(1000)
            val backend = FakeBackend()
            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 1000L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buffer = ByteArray(100)
            val readCount = source.read(position = 0L, buffer = buffer, offset = 0, length = 100)

            assertEquals(100, readCount)
            val expected = ByteArray(100) { (it % 100).toByte() }
            assertArrayEquals(expected, buffer)

            assertEquals(1, backend.prioritizeCalls.size)
            val expectedPieces =
                windowPieces(
                    fileOffset = 0L,
                    fileSize = 1000L,
                    pieceLength = 100L,
                    position = 0L,
                    windowBytes = 16L * mib,
                )
            assertEquals(expectedPieces, backend.prioritizeCalls[0].third)
        }

    @Test
    fun BS2_boundaryReads_returnsMinusOneOrClampedLength() =
        runTest {
            val file = createTempFileWithBytes(100)
            val backend = FakeBackend()
            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 100L,
                    pieceLength = 10L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buffer = ByteArray(100)
            val atEnd = source.read(position = 100L, buffer = buffer, offset = 0, length = 10)
            assertEquals(-1, atEnd)

            val partial = source.read(position = 90L, buffer = buffer, offset = 0, length = 100)
            assertEquals(10, partial)
        }

    @Test
    fun BS3_snapshotPolledUntilPiecesComplete() =
        runTest {
            val file = createTempFileWithBytes(200)
            val backend = FakeBackend()
            var pollCount = 0
            backend.snapshotProvider = { _, _ ->
                pollCount++
                val have =
                    when (pollCount) {
                        1 -> 0
                        2 -> 1
                        else -> 2
                    }
                TorrentSnapshot(piecesHave = have, downloadSpeedBps = 1024L, peers = 1)
            }

            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 200L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buffer = ByteArray(200)
            val readCount = source.read(position = 0L, buffer = buffer, offset = 0, length = 200)

            assertEquals(200, readCount)
            assertEquals(3, pollCount)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun BS4_stallTimeout_throwsSourceStallException() =
        runTest {
            val file = createTempFileWithBytes(200)
            val backend = FakeBackend()
            backend.snapshotProvider = { _, _ ->
                TorrentSnapshot(piecesHave = 0, downloadSpeedBps = 0L, peers = 0)
            }

            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 200L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                    config = StreamReadConfig(stallTimeoutMs = 30_000L, pollIntervalMs = 100L),
                )

            val buffer = ByteArray(200)
            try {
                source.read(position = 0L, buffer = buffer, offset = 0, length = 200)
                fail("Expected SourceStallException at 30s")
            } catch (_: SourceStallException) {
                assertEquals(30_000L, currentTime)
            }

            // Second case: growth at 20s, then none -> throws at 50s
            val startT = currentTime
            val backend2 = FakeBackend()
            backend2.snapshotProvider = { _, _ ->
                val elapsedSinceCase2 = currentTime - startT
                val have = if (elapsedSinceCase2 >= 20_000L) 1 else 0
                TorrentSnapshot(piecesHave = have, downloadSpeedBps = 0L, peers = 0)
            }
            val source2 =
                TorrentByteSource(
                    backend = backend2,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 200L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                    config = StreamReadConfig(stallTimeoutMs = 30_000L, pollIntervalMs = 100L),
                )

            try {
                source2.read(position = 0L, buffer = buffer, offset = 0, length = 200)
                fail("Expected SourceStallException at 50s")
            } catch (_: SourceStallException) {
                val case2Duration = currentTime - startT
                assertEquals(50_000L, case2Duration)
            }
        }

    @Test
    fun BS5_windowSlides_reprioritizesAtHalf() =
        runTest {
            val totalSize = 30L * mib
            val file = createSparseTempFile(totalSize)
            val backend = FakeBackend()
            val windowSize = 16L * mib

            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = totalSize,
                    pieceLength = 1L * mib,
                    localFile = file,
                    contentType = "video/mp4",
                    config = StreamReadConfig(windowBytes = windowSize),
                )

            val buf = ByteArray(10)
            source.read(position = 0L, buffer = buf, offset = 0, length = 10)
            assertEquals(1, backend.prioritizeCalls.size)

            source.read(position = 1L * mib, buffer = buf, offset = 0, length = 10)
            assertEquals(1, backend.prioritizeCalls.size)

            source.read(position = 8L * mib, buffer = buf, offset = 0, length = 10)
            assertEquals(2, backend.prioritizeCalls.size)

            source.read(position = 0L, buffer = buf, offset = 0, length = 10)
            assertEquals(3, backend.prioritizeCalls.size)
        }

    @Test
    fun BS6_fileOffsetAndPieceLengthMappedToSnapshot() =
        runTest {
            val offset = (10.5 * mib).toLong()
            val pieceLen = 2L * mib
            val totalSize = 100L * mib
            val file = createSparseTempFile(totalSize)
            val backend = FakeBackend()

            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = offset,
                    sizeBytes = totalSize,
                    pieceLength = pieceLen,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buf = ByteArray(10)
            source.read(position = 1L * mib, buffer = buf, offset = 0, length = 10)

            assertEquals(1, backend.snapshotCalls.size)
            val expectedPieces = piecesForRange(offset, pieceLen, 1L * mib, 10L)
            assertEquals(expectedPieces, backend.snapshotCalls[0].second)
        }

    @Test
    fun BS7_concurrentReadsDifferentPositions() =
        runTest {
            val file = createTempFileWithBytes(1000)
            val backend = FakeBackend()
            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 1000L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val b1 = ByteArray(50)
            val b2 = ByteArray(50)

            val r1 = async { source.read(position = 100L, buffer = b1, offset = 0, length = 50) }
            val r2 = async { source.read(position = 500L, buffer = b2, offset = 0, length = 50) }

            assertEquals(50, r1.await())
            assertEquals(50, r2.await())

            val expected1 = ByteArray(50) { ((100 + it) % 100).toByte() }
            val expected2 = ByteArray(50) { ((500 + it) % 100).toByte() }

            assertArrayEquals(expected1, b1)
            assertArrayEquals(expected2, b2)
        }

    @Test
    fun BS8_missingOrShortFile_throwsSourceIoException() =
        runTest {
            val missingFile = File(tempFolder.root, "non_existent.mkv")
            val backend = FakeBackend()
            val sourceMissing =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 1000L,
                    pieceLength = 100L,
                    localFile = missingFile,
                    contentType = "video/mp4",
                )

            val buf = ByteArray(100)
            try {
                sourceMissing.read(position = 0L, buffer = buf, offset = 0, length = 100)
                fail("Expected SourceIoException for missing file")
            } catch (_: SourceIoException) {
            }

            val shortFile = createTempFileWithBytes(50)
            val sourceShort =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 1000L,
                    pieceLength = 100L,
                    localFile = shortFile,
                    contentType = "video/mp4",
                )

            try {
                sourceShort.read(position = 0L, buffer = buf, offset = 0, length = 100)
                fail("Expected SourceIoException for short file")
            } catch (_: SourceIoException) {
            }
        }

    @Test
    fun BS9_readAfterClose_throwsSourceIoException() =
        runTest {
            val file = createTempFileWithBytes(200)
            val backend = FakeBackend()
            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 200L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            source.close()
            val buf = ByteArray(10)
            try {
                source.read(position = 0L, buffer = buf, offset = 0, length = 10)
                fail("Expected SourceIoException after close")
            } catch (_: SourceIoException) {
            }
        }

    @Test
    fun BS10_backendErrors_wrappedOrPropagated() =
        runTest {
            val file = createTempFileWithBytes(200)
            val backend = FakeBackend()
            backend.snapshotError = TorrentBackendException(TorrentFailReason.STORAGE)

            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 200L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buf = ByteArray(10)
            try {
                source.read(position = 0L, buffer = buf, offset = 0, length = 10)
                fail("Expected SourceIoException for STORAGE error")
            } catch (_: SourceIoException) {
            }

            backend.snapshotError = CancellationException("Cancelled")
            try {
                source.read(position = 0L, buffer = buf, offset = 0, length = 10)
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("Cancelled", e.message)
            }
        }

    @Test
    fun BS11_zeroSizeBytes_returnsMinusOne() =
        runTest {
            val file = createTempFileWithBytes(10)
            val backend = FakeBackend()
            val source =
                TorrentByteSource(
                    backend = backend,
                    infoHash = "h",
                    fileIndex = 0,
                    fileOffset = 0L,
                    sizeBytes = 0L,
                    pieceLength = 100L,
                    localFile = file,
                    contentType = "video/mp4",
                )

            val buf = ByteArray(10)
            assertEquals(-1, source.read(position = 0L, buffer = buf, offset = 0, length = 10))
        }

    private class FakeBackend : TorrentBackend {
        val prioritizeCalls = mutableListOf<Triple<String, Int, List<Int>>>()
        val snapshotCalls = mutableListOf<Pair<String, List<Int>>>()
        var snapshotProvider: ((String, List<Int>) -> TorrentSnapshot)? = null
        var snapshotError: Throwable? = null

        override suspend fun add(
            infoHash: String,
            magnetUri: String,
        ) {
            // No-op for test fake
        }

        override suspend fun awaitMetadata(infoHash: String): TorrentMetadata = TorrentMetadata(emptyList(), 1024L)

        override suspend fun prioritize(
            infoHash: String,
            fileIndex: Int,
            pieces: List<Int>,
        ) {
            prioritizeCalls.add(Triple(infoHash, fileIndex, pieces))
        }

        override suspend fun snapshot(
            infoHash: String,
            pieces: List<Int>,
        ): TorrentSnapshot {
            snapshotError?.let { throw it }
            snapshotCalls.add(infoHash to pieces)
            return snapshotProvider?.invoke(infoHash, pieces)
                ?: TorrentSnapshot(piecesHave = pieces.size, downloadSpeedBps = 0L, peers = 1)
        }

        override suspend fun remove(infoHash: String) {
            // No-op for test fake
        }
    }
}
