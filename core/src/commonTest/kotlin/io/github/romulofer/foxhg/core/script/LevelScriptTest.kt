package io.github.romulofer.foxhg.core.script

import io.github.romulofer.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelScriptTest {
    private fun parse(vararg bytes: Int) = (LevelScript.parse(ByteArray(bytes.size) { bytes[it].toByte() }) as FoxResult.Ok).value

    @Test
    fun plainTriggersAndPadding() {
        val f = parse(0x02, 1, 0, 0, 0, 0x04, 6, 0, 0, 0, 0, 0)
        assertEquals(listOf(LevelTrigger(2, 1), LevelTrigger(4, 6)), f.triggers)
        assertEquals(1, f.padding)
    }

    @Test
    fun variableSectionPointsPastTheEntriesAfterIt() {
        val f = parse(0x01, 0x0B, 0, 0, 0, 0x03, 0x0C, 0, 0, 0, 0x02, 0x1A, 0, 0, 0, 0x00, 0xF7, 0x40, 1, 0, 0xA7, 0x28, 0, 0)
        assertEquals(listOf(VariableTrigger(0x40F7, 1, 0x28A7)), f.variableTriggers)
        assertEquals(listOf(1, 3, 2), f.triggers.map { it.type })
        assertContentEquals(f.toBytes(), parse(*f.toBytes().map { it.toInt() and 0xFF }.toIntArray()).toBytes())
    }

    @Test
    fun addingVariableTriggersAddsTheMarkerAndStaysParseable() {
        val f = parse(0x02, 1, 0, 0, 0, 0, 0, 0).withVariableTriggers(listOf(VariableTrigger(0x4000, 2, 3))).aligned()
        assertTrue(f.hasVariableSection)
        assertEquals(0, f.toBytes().size % 4)
        assertEquals(f, (LevelScript.parse(f.toBytes()) as FoxResult.Ok).value)
    }

    @Test
    fun garbageIsRefused() {
        assertTrue(LevelScript.parse(byteArrayOf(9, 9, 9)) is FoxResult.Err)
    }
}
