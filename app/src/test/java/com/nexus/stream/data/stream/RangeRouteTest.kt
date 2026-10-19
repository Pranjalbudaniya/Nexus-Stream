package com.nexus.stream.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

class RangeRouteTest {
    private class FakeResponseWriter : HttpResponseWriter {
        var status: Int? = null
        val headers = mutableListOf<Pair<String, String>>()
        val bodyBytes = ByteArrayOutputStream()
        val writeLengths = mutableListOf<Int>()
        var throwOnWriteCall: Int? = null
        private var writeCallCount = 0

        override var headWritten: Boolean = false
            private set

        override suspend fun head(
            status: Int,
            headers: List<Pair<String, String>>,
        ) {
            check(!headWritten) { "Head already written" }
            this.status = status
            this.headers.addAll(headers)
            headWritten = true
        }

        override suspend fun body(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            check(headWritten) { "Head not written yet" }
            writeCallCount++
            if (throwOnWriteCall == writeCallCount) {
                throw ClientGoneException()
            }
            bodyBytes.write(buffer, offset, length)
            writeLengths.add(length)
        }
    }

    private class InMemorySource(
        private val data: ByteArray,
        override val contentType: String = "video/mp4",
    ) : RangeByteSource {
        override val sizeBytes: Long = data.size.toLong()
        val readCalls = mutableListOf<Triple<Long, Int, Int>>() // position, offset, length
        var throwOnCall: Int? = null
        var returnNegativeOneOnCall: Int? = null
        var cancelOnCall: Int? = null
        var callCount = 0

        override suspend fun read(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            callCount++
            readCalls.add(Triple(position, offset, length))

            if (cancelOnCall == callCount) throw CancellationException("Cancelled")
            if (throwOnCall == callCount) throw SourceStallException()

            val pos = position.toInt()
            val atEof = returnNegativeOneOnCall == callCount || pos >= data.size
            return if (atEof) {
                -1
            } else {
                val bytesToRead = minOf(length, data.size - pos)
                System.arraycopy(data, pos, buffer, offset, bytesToRead)
                bytesToRead
            }
        }
    }

    private class IoExceptionSource(
        private val data: ByteArray,
        private val throwOnCall: Int,
    ) : RangeByteSource {
        override val sizeBytes: Long = data.size.toLong()
        override val contentType: String = "video/mp4"
        var callCount = 0

        override suspend fun read(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            callCount++
            if (callCount == throwOnCall) {
                throw SourceIoException()
            }
            val pos = position.toInt()
            val toRead = minOf(length, data.size - pos)
            System.arraycopy(data, pos, buffer, offset, toRead)
            return toRead
        }
    }

    private fun generateData(size: Int): ByteArray = ByteArray(size) { (it % 128).toByte() }

    @Test
    fun RR1_fullGetFourWrites() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertEquals(200, writer.status)
            assertEquals("1000", writer.headers.find { it.first == "Content-Length" }?.second)
            assertArrayEquals(data, writer.bodyBytes.toByteArray())
            assertEquals(listOf(300, 300, 300, 100), writer.writeLengths)
        }

    @Test
    fun RR2_partialGetRange100To199() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=100-199")), writer)

            assertEquals(206, writer.status)
            assertEquals("bytes 100-199/1000", writer.headers.find { it.first == "Content-Range" }?.second)
            assertEquals("100", writer.headers.find { it.first == "Content-Length" }?.second)
            val expected = data.copyOfRange(100, 200)
            assertArrayEquals(expected, writer.bodyBytes.toByteArray())
        }

    @Test
    fun RR3_headWithAndWithoutRange_neverCallsRead() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source, chunkBytes = 300)

            // Without Range
            val writer1 = FakeResponseWriter()
            route.handle(HttpRequest("HEAD", "/test", emptyMap()), writer1)
            assertEquals(200, writer1.status)
            assertEquals("1000", writer1.headers.find { it.first == "Content-Length" }?.second)
            assertEquals(0, writer1.bodyBytes.size())
            assertEquals(0, source.callCount)

            // With Range
            val writer2 = FakeResponseWriter()
            route.handle(HttpRequest("HEAD", "/test", mapOf("range" to "bytes=0-99")), writer2)
            assertEquals(206, writer2.status)
            assertEquals("bytes 0-99/1000", writer2.headers.find { it.first == "Content-Range" }?.second)
            assertEquals("100", writer2.headers.find { it.first == "Content-Length" }?.second)
            assertEquals(0, writer2.bodyBytes.size())
            assertEquals(0, source.callCount)
        }

    @Test
    fun RR4_unsatisfiableRange_returns416() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=1000-")), writer)

            assertEquals(416, writer.status)
            assertEquals("bytes */1000", writer.headers.find { it.first == "Content-Range" }?.second)
            assertEquals("0", writer.headers.find { it.first == "Content-Length" }?.second)
            assertEquals(0, source.callCount)
        }

    @Test
    fun RR5_unsupportedMethods_return405() =
        runTest {
            val data = generateData(1000)
            val methods = listOf("POST", "PUT", "DELETE")
            methods.forEachIndexed { index, method ->
                val source = InMemorySource(data)
                val route = RangeRoute(source)
                val writer = FakeResponseWriter()
                route.handle(HttpRequest(method, "/test", emptyMap()), writer)
                assertEquals("Failed at index $index", 405, writer.status)
                assertEquals("GET, HEAD", writer.headers.find { it.first == "Allow" }?.second)
                assertEquals("0", writer.headers.find { it.first == "Content-Length" }?.second)
                assertEquals(0, source.callCount)
            }
        }

    @Test
    fun RR6_requiredCommonHeadersPresent() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data, contentType = "video/webm")
            val route = RangeRoute(source)

            // Test 200
            val w200 = FakeResponseWriter()
            route.handle(HttpRequest("GET", "/test", emptyMap()), w200)
            assertCommonHeaders(w200.headers, "video/webm")

            // Test 206
            val w206 = FakeResponseWriter()
            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=0-9")), w206)
            assertCommonHeaders(w206.headers, "video/webm")

            // Test 416
            val w416 = FakeResponseWriter()
            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=1000-")), w416)
            assertCommonHeaders(w416.headers, "video/webm")
        }

    private fun assertCommonHeaders(
        headers: List<Pair<String, String>>,
        contentType: String,
    ) {
        assertEquals("bytes", headers.find { it.first == "Accept-Ranges" }?.second)
        assertEquals(contentType, headers.find { it.first == "Content-Type" }?.second)
        assertEquals("no-store", headers.find { it.first == "Cache-Control" }?.second)
        assertEquals("close", headers.find { it.first == "Connection" }?.second)
    }

    @Test
    fun RR7_stallOnFirstCall_returns504() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data).apply { throwOnCall = 1 }
            val route = RangeRoute(source)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertEquals(504, writer.status)
            assertEquals("0", writer.headers.find { it.first == "Content-Length" }?.second)
            assertEquals(0, writer.bodyBytes.size())
        }

    @Test
    fun RR8_stallOnSecondCall_headAlreadyWrittenStopsQuietly() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data).apply { throwOnCall = 2 }
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertEquals(200, writer.status)
            assertEquals(300, writer.bodyBytes.size())
        }

    @Test
    fun RR9_ioExceptionOnFirstCallAndLaterCall() =
        runTest {
            val data = generateData(1000)

            // First call: 502
            val source1 = IoExceptionSource(data, throwOnCall = 1)
            val w1 = FakeResponseWriter()
            RangeRoute(source1, chunkBytes = 300).handle(HttpRequest("GET", "/test", emptyMap()), w1)
            assertEquals(502, w1.status)
            assertEquals("0", w1.headers.find { it.first == "Content-Length" }?.second)

            // Later call: stops quietly
            val source2 = IoExceptionSource(data, throwOnCall = 2)
            val w2 = FakeResponseWriter()
            RangeRoute(source2, chunkBytes = 300).handle(HttpRequest("GET", "/test", emptyMap()), w2)
            assertEquals(200, w2.status)
            assertEquals(300, w2.bodyBytes.size())
        }

    @Test
    fun RR10_eofBeforeRangeComplete_stopsQuietly() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data).apply { returnNegativeOneOnCall = 2 }
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertEquals(200, writer.status)
            assertEquals(300, writer.bodyBytes.size())
        }

    @Test
    fun RR11_clientGoneOnSecondWrite_returnsNormallyAndReadNotCalledAgain() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter().apply { throwOnWriteCall = 2 }

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertEquals(200, writer.status)
            assertEquals(2, source.callCount) // call 1 succeeded, call 2 read succeeded but write threw ClientGone
        }

    @Test
    fun RR12_sizeZero_getAndRange() =
        runTest {
            val source = InMemorySource(ByteArray(0))
            val route = RangeRoute(source)

            val wGet = FakeResponseWriter()
            route.handle(HttpRequest("GET", "/test", emptyMap()), wGet)
            assertEquals(200, wGet.status)
            assertEquals("0", wGet.headers.find { it.first == "Content-Length" }?.second)
            assertEquals(0, source.callCount)

            val wRange = FakeResponseWriter()
            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=0-")), wRange)
            assertEquals(416, wRange.status)
            assertEquals(0, source.callCount)
        }

    @Test
    fun RR13_exactBytePositionsAndLengths() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val route = RangeRoute(source, chunkBytes = 300)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", mapOf("range" to "bytes=250-899")), writer)

            assertEquals(206, writer.status)
            assertEquals(650, writer.bodyBytes.size())
            assertEquals(3, source.readCalls.size)
            assertEquals(Triple(250L, 0, 300), source.readCalls[0])
            assertEquals(Triple(550L, 0, 300), source.readCalls[1])
            assertEquals(Triple(850L, 0, 50), source.readCalls[2])
        }

    @Test
    fun RR14_cancellationPropagates() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data).apply { cancelOnCall = 1 }
            val route = RangeRoute(source)
            val writer = FakeResponseWriter()

            try {
                route.handle(HttpRequest("GET", "/test", emptyMap()), writer)
                fail("Expected CancellationException")
            } catch (e: CancellationException) {
                assertEquals("Cancelled", e.message)
            }
        }

    @Test
    fun RR15_readBufferOffsetZeroAndLengthAtMostChunkBytes() =
        runTest {
            val data = generateData(1000)
            val source = InMemorySource(data)
            val chunkBytes = 256
            val route = RangeRoute(source, chunkBytes = chunkBytes)
            val writer = FakeResponseWriter()

            route.handle(HttpRequest("GET", "/test", emptyMap()), writer)

            assertTrue(source.readCalls.isNotEmpty())
            for (call in source.readCalls) {
                assertEquals(0, call.second) // offset 0
                assertTrue(call.third <= chunkBytes) // length <= chunkBytes
            }
        }
}
