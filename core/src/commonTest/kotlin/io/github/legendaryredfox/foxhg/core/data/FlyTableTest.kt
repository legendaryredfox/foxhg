package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fly.FlyEntry
import io.github.legendaryredfox.foxhg.core.data.fly.FlyTable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlyTableTest {
    private val entry = FlyEntry(7, isBlackoutSpawn = true, isFlyPoint = false, blackoutHeader = 0x1234, localX = 9, localY = 200, flyHeader = 0xABC, flyX = 300, flyY = 65535, unlockHeader = 5, unlockX = 1, unlockY = 2)

    private fun arm9(): ByteArray {
        val bytes = ByteArray(0x110000) { 0x5A }
        FlyTable(List(HgssEnglishLayout.flyTableEntries) { entry.copy(flagIndex = it) }).applyTo(bytes, HgssEnglishLayout).copyInto(bytes)
        return bytes
    }

    private fun parse(bytes: ByteArray) = (FlyTable.parse(bytes, HgssEnglishLayout) as FoxResult.Ok).value

    @Test
    fun entriesRoundTrip() {
        val bytes = arm9()
        val table = parse(bytes)
        assertEquals(HgssEnglishLayout.flyTableEntries, table.entries.size)
        assertEquals(entry.copy(flagIndex = 3), table.entries[3])
        assertContentEquals(bytes, table.applyTo(bytes, HgssEnglishLayout))
    }

    @Test
    fun anEditTouchesOnlyItsOwnEighteenBytes() {
        val bytes = arm9()
        val out = parse(bytes).with(2, entry.copy(flagIndex = 2, isFlyPoint = true, flyX = 11)).applyTo(bytes, HgssEnglishLayout)
        val start = HgssEnglishLayout.flyTableArm9 + 2 * FlyEntry.SIZE
        for (i in bytes.indices) if (i !in start until start + FlyEntry.SIZE) assertEquals(bytes[i], out[i], "byte $i")
        assertEquals(true, parse(out).entries[2].isFlyPoint)
        assertEquals(11, parse(out).entries[2].flyX)
    }

    @Test
    fun aShortArm9IsRefused() {
        assertTrue(FlyTable.parse(ByteArray(16), HgssEnglishLayout) is FoxResult.Err)
    }
}
