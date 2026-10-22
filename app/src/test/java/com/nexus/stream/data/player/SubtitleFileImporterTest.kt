package com.nexus.stream.data.player

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleFileImporterTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private lateinit var tempCacheDir: File
    private lateinit var importer: AndroidSubtitleFileImporter

    @Before
    fun setUp() {
        io.mockk.mockkStatic(Uri::class)
        tempCacheDir = Files.createTempDirectory("test_cache").toFile()
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns tempCacheDir
        importer = AndroidSubtitleFileImporter(context, testDispatcher)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(Uri::class)
        tempCacheDir.deleteRecursively()
    }

    private fun mockContentUri(
        displayName: String,
        content: InputStream,
    ): String {
        val uriStr = "content://media/external/$displayName"
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { uri.lastPathSegment } returns displayName
        every { Uri.parse(uriStr) } returns uri

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getString(0) } returns displayName

        every {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } returns cursor
        every { contentResolver.openInputStream(uri) } returns content

        return uriStr
    }

    @Test
    fun SI1_allowedNameAndSmallContent_importsFileWithSameBytes() =
        runTest(testDispatcher) {
            val bytes = "1\n00:00:01,000 --> 00:00:04,000\nHello World\n".toByteArray()
            val uri = mockContentUri("captions.srt", ByteArrayInputStream(bytes))

            val result = importer.import(uri)
            assertTrue(result is ImportResult.Imported)

            val path = (result as ImportResult.Imported).path
            val importedFile = File(path)
            assertTrue(importedFile.exists())
            assertEquals(File(tempCacheDir, "subtitles"), importedFile.parentFile)
            assertEquals(bytes.toList(), importedFile.readBytes().toList())
        }

    @Test
    fun SI2_disallowedExtension_returnsBadTypeAndCreatesNoFile() =
        runTest(testDispatcher) {
            val bytes = "some binary".toByteArray()
            val uri = mockContentUri("malicious.exe", ByteArrayInputStream(bytes))

            val result = importer.import(uri)
            assertEquals(ImportResult.BadType, result)

            val subtitlesDir = File(tempCacheDir, "subtitles")
            val files = subtitlesDir.listFiles().orEmpty()
            assertTrue(files.isEmpty())
        }

    @Test
    fun SI3_over5MB_returnsTooLargeAndLeavesNoFile() =
        runTest(testDispatcher) {
            val size = 5L * 1024 * 1024 + 1024
            // Stream that generates zeroes up to size
            val largeStream =
                object : InputStream() {
                    private var bytesRead = 0L

                    override fun read(): Int =
                        if (bytesRead < size) {
                            bytesRead++
                            0
                        } else {
                            -1
                        }

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int {
                        if (bytesRead >= size) return -1
                        val toRead = minOf(len.toLong(), size - bytesRead).toInt()
                        b.fill(0, off, off + toRead)
                        bytesRead += toRead
                        return toRead
                    }
                }

            val uri = mockContentUri("large.srt", largeStream)

            val result = importer.import(uri)
            assertEquals(ImportResult.TooLarge, result)

            val subtitlesDir = File(tempCacheDir, "subtitles")
            val files = subtitlesDir.listFiles().orEmpty()
            assertTrue(files.isEmpty())
        }

    @Test
    fun SI4_cleanup_removesCreatedFiles_andCanBeCalledTwice() =
        runTest(testDispatcher) {
            val uri1 = mockContentUri("s1.srt", ByteArrayInputStream("sub1".toByteArray()))
            val uri2 = mockContentUri("s2.vtt", ByteArrayInputStream("sub2".toByteArray()))

            val r1 = importer.import(uri1) as ImportResult.Imported
            val r2 = importer.import(uri2) as ImportResult.Imported

            val f1 = File(r1.path)
            val f2 = File(r2.path)
            assertTrue(f1.exists())
            assertTrue(f2.exists())

            importer.cleanup()
            assertFalse(f1.exists())
            assertFalse(f2.exists())

            // Second call is harmless
            importer.cleanup()
            assertFalse(f1.exists())
            assertFalse(f2.exists())
        }
}
