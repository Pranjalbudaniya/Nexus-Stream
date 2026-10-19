package com.nexus.stream.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMimeTest {
    @Test
    fun VM1_standardExtensions() {
        assertEquals("video/x-matroska", videoMimeType("a/b/Movie.MKV"))
        assertEquals("video/mp4", videoMimeType("x.mp4"))
        assertEquals("video/webm", videoMimeType("x.webm"))
    }

    @Test
    fun VM2_multipleDots() {
        assertEquals("video/x-matroska", videoMimeType("a.b.mkv"))
    }

    @Test
    fun VM3_unrecognizedOrNoExtension() {
        assertEquals("application/octet-stream", videoMimeType("x.xyz"))
        assertEquals("application/octet-stream", videoMimeType("noext"))
        assertEquals("application/octet-stream", videoMimeType("dir.d/noext"))
        assertEquals("application/octet-stream", videoMimeType(""))
    }

    @Test
    fun VM4_otherSupportedVideoExtensions() {
        assertEquals("video/mp2t", videoMimeType("x.ts"))
        assertEquals("video/x-msvideo", videoMimeType("x.avi"))
        assertEquals("video/quicktime", videoMimeType("x.mov"))
        assertEquals("video/x-m4v", videoMimeType("x.m4v"))
    }
}
