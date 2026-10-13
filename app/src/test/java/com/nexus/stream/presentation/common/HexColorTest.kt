package com.nexus.stream.presentation.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {
    private val hash = "#"

    @Test
    fun parseHexColor_validInputs_returnsExpectedColor() {
        // 6 digits with hash
        val color1 = parseHexColor(hash + "3F51B5")
        assertNotNull(color1)
        assertEquals(1f, color1!!.alpha, 0.001f)
        assertEquals(0x3F / 255f, color1.red, 0.01f)
        assertEquals(0x51 / 255f, color1.green, 0.01f)
        assertEquals(0xB5 / 255f, color1.blue, 0.01f)

        // 6 digits without hash, lowercase, surrounding spaces
        val color2 = parseHexColor("  3f51b5  ")
        assertNotNull(color2)
        assertEquals(color1, color2)

        // 3 digits expanded: F0A -> FF00AA
        val color3 = parseHexColor(hash + "F0A")
        assertNotNull(color3)
        assertEquals(1f, color3!!.alpha, 0.001f)
        assertEquals(1f, color3.red, 0.01f)
        assertEquals(0f, color3.green, 0.01f)
        assertEquals(0xAA / 255f, color3.blue, 0.01f)

        // 8 digits: alpha ignored, result opaque
        val color4 = parseHexColor(hash + "003F51B5")
        assertNotNull(color4)
        assertEquals(1f, color4!!.alpha, 0.001f)
        assertEquals(color1, color4)
    }

    @Test
    fun parseHexColor_invalidInputs_returnsNull() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("   "))
        assertNull(parseHexColor(hash))
        assertNull(parseHexColor(hash + "12"))
        assertNull(parseHexColor(hash + "12345"))
        assertNull(parseHexColor(hash + "1234567"))
        assertNull(parseHexColor(hash + "ZZZZZZ"))
        assertNull(parseHexColor(hash + "123456789"))
    }

    @Test
    fun toHexString_roundTripsWithParseHexColor_andIsUppercase() {
        val original = Color(red = 0x3F / 255f, green = 0x51 / 255f, blue = 0xB5 / 255f, alpha = 1f)
        val hexString = original.toHexString()
        assertEquals(hash + "3F51B5", hexString)

        val parsed = parseHexColor(hexString)
        assertNotNull(parsed)
        assertEquals(original.red, parsed!!.red, 0.01f)
        assertEquals(original.green, parsed.green, 0.01f)
        assertEquals(original.blue, parsed.blue, 0.01f)
        assertEquals(1f, parsed.alpha, 0.001f)
    }
}
