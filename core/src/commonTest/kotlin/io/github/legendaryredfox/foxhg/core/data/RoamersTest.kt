package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamer
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoamersTest {
    /** The vanilla bytes of the four cases at [AT], with the species word of Latios 8 bytes after the last case. */
    private fun arm9(): ByteArray {
        val d = ByteArray(0x200)
        val code = intArrayOf(
            0xF3, 0x26, 0x28, 0x25, 0x0D, 0xE0, 0xF4, 0x26, 0x28, 0x25, 0x0A, 0xE0, 0x5F, 0x26, 0xB6, 0x00, 0x23, 0x25, 0x06, 0xE0,
            0x08, 0x4E, 0x23, 0x25, 0x03, 0xE0,
        )
        code.forEachIndexed { i, b -> d[AT + i] = b.toByte() }
        // ldr at AT + 20: pc = (AT + 24) rounded down to a word, plus 8 * 4
        val literal = ((AT + 24) and 3.inv()) + 8 * 4
        d[literal] = 0x7D; d[literal + 1] = 0x01
        return d
    }

    private fun Roamers.ok(arm9: ByteArray) = (applyTo(arm9) as FoxResult.Ok).value

    @Test
    fun theVanillaCodeReadsAsTheFourRoamers() {
        assertEquals(Roamers.VANILLA, (Roamers.parse(arm9()) as FoxResult.Ok).value)
    }

    @Test
    fun writingTheVanillaValuesChangesNothing() {
        assertContentEquals(arm9(), Roamers.VANILLA.ok(arm9()))
    }

    @Test
    fun anEditReadsBackAndTouchesOnlyTheCases() {
        val edited = Roamers(listOf(Roamer(150, 70), Roamer(255, 1), Roamer(493 - 1, 50).copy(species = 488), Roamer(493, 99)))
        val bytes = edited.ok(arm9())
        assertEquals(edited, (Roamers.parse(bytes) as FoxResult.Ok).value)
        val changed = bytes.indices.filter { bytes[it] != arm9()[it] }
        assertTrue(changed.all { it in AT until AT + 26 || it in 0x40 until 0x50 + 0x40 }, "only the cases and the species word changed")
    }

    @Test
    fun aSpeciesThePlaceCanNotHoldIsRefused() {
        val base = Roamers.VANILLA.roamers
        assertIs<FoxResult.Err>(Roamers(listOf(Roamer(256, 40)) + base.drop(1)).applyTo(arm9()))
        assertIs<FoxResult.Err>(Roamers(base.take(2) + Roamer(381, 35) + base[3]).applyTo(arm9()), "381 is odd, so no byte shifted left makes it")
        assertIs<FoxResult.Err>(Roamers(base.take(3) + Roamer(494, 35)).applyTo(arm9()))
        assertIs<FoxResult.Err>(Roamers(listOf(Roamer(243, 101)) + base.drop(1)).applyTo(arm9()))
        assertFalse(Roamers.canHold(2, 381))
        assertTrue(Roamers.canHold(2, 380) && Roamers.canHold(3, 381) && Roamers.canHold(0, 255))
    }

    @Test
    fun codeThatIsNotThereIsAnError() {
        assertIs<FoxResult.Err>(Roamers.parse(ByteArray(0x200)))
        assertNull(Roamers.byteAndShift(0))
    }

    private companion object { const val AT = 0x40 }
}
