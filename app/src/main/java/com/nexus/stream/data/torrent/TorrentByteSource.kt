package com.nexus.stream.data.torrent

import com.nexus.stream.data.stream.RangeByteSource
import com.nexus.stream.data.stream.SourceIoException
import com.nexus.stream.data.stream.SourceStallException
import com.nexus.stream.domain.model.TorrentConfig
import com.nexus.stream.domain.usecase.needsReprioritize
import com.nexus.stream.domain.usecase.piecesForRange
import com.nexus.stream.domain.usecase.windowPieces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

data class StreamReadConfig(
    val windowBytes: Long = TorrentConfig.STREAM_WINDOW_BYTES,
    val pollIntervalMs: Long = TorrentConfig.STREAM_POLL_MS,
    val stallTimeoutMs: Long = TorrentConfig.STREAM_STALL_TIMEOUT_MS,
)

@Suppress("LongParameterList")
class TorrentByteSource(
    private val backend: TorrentBackend,
    private val infoHash: String,
    private val fileIndex: Int,
    private val fileOffset: Long,
    override val sizeBytes: Long,
    private val pieceLength: Long,
    private val localFile: File,
    override val contentType: String,
    private val config: StreamReadConfig = StreamReadConfig(),
) : RangeByteSource,
    Closeable {
    private val mutex = Mutex()
    private var windowStart: Long? = null

    @Volatile
    private var closed: Boolean = false

    private val channelLock = Any()
    private var channel: FileChannel? = null

    override suspend fun read(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (closed) throw SourceIoException()
        if (sizeBytes <= 0L || position >= sizeBytes) return -1

        val toRead = minOf(length.toLong(), sizeBytes - position).toInt()
        if (toRead > 0) {
            reprioritizeIfNeeded(position)
            awaitPieces(position, toRead.toLong())
            readFileBytes(position, buffer, offset, toRead)
        }
        return maxOf(0, toRead)
    }

    private suspend fun reprioritizeIfNeeded(position: Long) {
        mutex.withLock {
            if (needsReprioritize(position, windowStart, config.windowBytes)) {
                val pieces = windowPieces(fileOffset, sizeBytes, pieceLength, position, config.windowBytes)
                runBackendCall { backend.prioritize(infoHash, fileIndex, pieces) }
                windowStart = position
            }
        }
    }

    private suspend fun awaitPieces(
        position: Long,
        length: Long,
    ) {
        val need = piecesForRange(fileOffset, pieceLength, position, length)
        if (need.isEmpty()) return

        var lastPiecesHave = -1
        var stallElapsed = 0L

        while (true) {
            if (closed) throw SourceIoException()
            val snapshot = runBackendCall { backend.snapshot(infoHash, need) }

            if (snapshot.piecesHave >= need.size) {
                break
            }

            if (snapshot.piecesHave > lastPiecesHave) {
                lastPiecesHave = snapshot.piecesHave
                stallElapsed = 0L
            }

            delay(config.pollIntervalMs)
            stallElapsed += config.pollIntervalMs

            if (stallElapsed >= config.stallTimeoutMs) {
                throw SourceStallException()
            }
        }
    }

    private fun readFileBytes(
        filePosition: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ) {
        val ch = getOrCreateChannel()
        val byteBuffer = ByteBuffer.wrap(buffer, offset, length)
        var totalRead = 0
        var currentFilePos = filePosition

        while (totalRead < length) {
            val readCount =
                try {
                    ch.read(byteBuffer, currentFilePos)
                } catch (e: IOException) {
                    val ioEx = SourceIoException()
                    ioEx.initCause(e)
                    throw ioEx
                }
            if (readCount <= 0) {
                throw SourceIoException()
            }
            totalRead += readCount
            currentFilePos += readCount
        }
    }

    private fun getOrCreateChannel(): FileChannel {
        if (closed) throw SourceIoException()
        channel?.let { return it }
        return synchronized(channelLock) {
            if (closed || !localFile.exists()) throw SourceIoException()
            channel ?: openChannel().also { channel = it }
        }
    }

    private fun openChannel(): FileChannel =
        try {
            FileChannel.open(localFile.toPath(), StandardOpenOption.READ)
        } catch (e: IOException) {
            val ioEx = SourceIoException()
            ioEx.initCause(e)
            throw ioEx
        }

    override fun close() {
        closed = true
        synchronized(channelLock) {
            try {
                channel?.close()
            } catch (_: IOException) {
            }
            channel = null
        }
    }
}

private inline fun <T> runBackendCall(block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: TorrentBackendException) {
        val ioEx = SourceIoException()
        ioEx.initCause(e)
        throw ioEx
    }
