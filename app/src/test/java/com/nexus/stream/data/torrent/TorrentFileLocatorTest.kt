package com.nexus.stream.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TorrentFileLocatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun TP1_nestedPath_resolvesInsideHashDirectory() {
        val root = tempFolder.root
        val hash = "h"
        val resolved = resolveInside(root, hash, "Movie/file.mkv")
        val expected = File(File(root, hash), "Movie/file.mkv").canonicalFile
        assertEquals(expected, resolved)
    }

    @Test
    fun TP2_directFileAndCurrentDirPrefix_resolveToSameFile() {
        val root = tempFolder.root
        val hash = "h"
        val expected = File(File(root, hash), "file.mkv").canonicalFile

        val resolvedDirect = resolveInside(root, hash, "file.mkv")
        val resolvedPrefixed = resolveInside(root, hash, "./file.mkv")

        assertEquals(expected, resolvedDirect)
        assertEquals(expected, resolvedPrefixed)
    }

    @Test
    fun TP3_parentDirectoryTraversal_returnsNull() {
        val root = tempFolder.root
        val hash = "h"

        assertNull(resolveInside(root, hash, "../x"))
        assertNull(resolveInside(root, hash, "a/../../x"))
        assertNull(resolveInside(root, hash, "a/../../../etc/passwd"))
    }

    @Test
    fun TP4_absolutePath_returnsNull() {
        val root = tempFolder.root
        val hash = "h"

        assertNull(resolveInside(root, hash, "/etc/passwd"))
    }

    @Test
    fun TP5_emptyPath_returnsNull() {
        val root = tempFolder.root
        val hash = "h"

        assertNull(resolveInside(root, hash, ""))
    }
}
