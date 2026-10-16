package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.starter.RivalScripts
import io.github.legendaryredfox.foxhg.core.data.starter.Starters
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartersTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** A made-up ARM9 with the starter places at known spots: species words, held item and level operands. */
    private fun arm9(): ByteArray {
        val a = ByteArray(0x960200) { 0x11 }
        // species literal pool at 0x1000: three words, then 13-3*4=1 byte, the signature
        val base = 0x1000
        listOf(152, 155, 158).forEachIndexed { i, s -> a[base + 4 * i] = s.toByte() }
        for (i in 0..3) a[base + 4 * i + 1] = 0
        a[base + 12] = 0
        bytes(0x03, 0x03, 0x1A, 0x12, 0x01, 0x23, 0x00, 0x00).copyInto(a, base + 13)
        // held item operand at the vanilla place, level 28 bytes before
        a[0x960FE] = 0
        bytes(0x20, 0x09, 0x90, 0x60, 0x19, 0x06, 0x21, 0x09, 0xAA, 0xD8, 0xF7, 0x9A, 0xFD, 0x06, 0x98, 0xEC, 0x35).copyInto(a, 0x960FF)
        a[0x960E2] = 5
        bytes(0x22, 0x20).copyInto(a, 0x960E3)
        return a
    }

    @Test
    fun theThreeValuesAreReadAndWrittenAtTheirPlaces() {
        val a = arm9()
        val s = (Starters.parse(a) as FoxResult.Ok).value
        assertEquals(Starters.VANILLA, s)
        val changed = Starters(listOf(1, 4, 7), heldItem = 17, level = 12).applyToArm9(a)!!
        assertEquals(Starters(listOf(1, 4, 7), 17, 12), (Starters.parse(changed) as FoxResult.Ok).value)
        assertContentEquals(a, Starters.VANILLA.applyToArm9(changed), "changing back restores the bytes")
    }

    @Test
    fun anArm9WithoutTheSignatureIsRefusedAndNothingIsWritten() {
        val a = ByteArray(0x1000)
        assertTrue(Starters.parse(a) is FoxResult.Err)
        assertNull(Starters.VANILLA.applyToArm9(a))
        assertNull(Starters.VANILLA.applyToOverlay(ByteArray(100)))
    }

    @Test
    fun aMissingHeldItemOrLevelPlaceLeavesThoseFieldsNull() {
        val a = arm9()
        a[0x960FF] = 0x55 // breaks the held item suffix
        val s = (Starters.parse(a) as FoxResult.Ok).value
        assertEquals(listOf(152, 155, 158), s.species)
        assertNull(s.heldItem)
        assertNull(s.level)
    }

    @Test
    fun theRivalScriptComparisonsFollowTheStarters() {
        // CompareVar 0x800C with 152, GoToIf not-equal to the second block, which compares with 155
        val data = ByteArray(80) { 0 }
        val first = bytes(0xCE, 0x00, 0x0C, 0x80, 0x11, 0x00, 0x0C, 0x80, 152, 0, 0x1C, 0x00, 0x05)
        first.copyInto(data, 10)
        // jump is relative to the end of the jump word (base 10 + 17 = 27); the second block sits at 40
        val jump = 40 - 27
        data[23] = jump.toByte()
        bytes(0x11, 0x00, 0x0C, 0x80, 155, 0).copyInto(data, 40)
        val out = assertNotNull(RivalScripts.patch(data, listOf(152, 155, 158), listOf(1, 4, 7)))
        assertEquals(1, out[18].toInt())
        assertEquals(4, out[44].toInt())
        assertNull(RivalScripts.patch(out, listOf(152, 155, 158), listOf(1, 4, 7)), "no longer matches the old species")
        assertNotNull(RivalScripts.patch(out, listOf(1, 4, 7), listOf(152, 155, 158)))
    }
}
