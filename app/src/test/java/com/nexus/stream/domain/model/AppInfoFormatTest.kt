package com.nexus.stream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInfoFormatTest {
    @Test
    fun AI1_versionAndBuild_areCombined() {
        assertEquals("1.0.0 (10000)", formatAppVersion("1.0.0", 10000L))
    }

    @Test
    fun AI2_missingName_showsOnlyTheBuild() {
        assertEquals("10000", formatAppVersion(null, 10000L))
    }
}
