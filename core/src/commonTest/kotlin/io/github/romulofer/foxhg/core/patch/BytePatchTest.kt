package io.github.romulofer.foxhg.core.patch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BytePatchTest {
    @Test
    fun hexBytesParsesSpacedHex() {
        assertEquals(listOf<Byte>(0x0F, -1, 0), hexBytes("0F FF  00").toList())
    }

    @Test
    fun anEditKeepsTheLengthOfWhatItReplaces() {
        assertFailsWith<IllegalArgumentException> { edit(PatchTarget.Arm9, 0, "00", "00 01", "x") }
    }

    @Test
    fun anEditDescribesItself() {
        assertEquals("ARM9 0x10: limit (01 -> 02)", edit(PatchTarget.Arm9, 0x10, "01", "02", "limit").describe())
        assertEquals("overlay 3 0x0: n (AA -> BB)", edit(PatchTarget.Overlay(3), 0, "AA", "BB", "n").describe())
    }
}
