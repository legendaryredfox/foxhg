package io.github.romulofer.foxhg.core.data

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashItems
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashSite
import io.github.romulofer.foxhg.core.game.HgssLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RockSmashTest {
    @Test
    fun aSiteIsFourBytesOddsThenTable() {
        val bytes = byteArrayOf(30, 0, 2, 0)
        val site = (RockSmashSite.parse(bytes) as FoxResult.Ok).value
        assertEquals(RockSmashSite(30, RockSmashSite.CLIFF_CAVE), site)
        assertContentEquals(bytes, site.toBytes())
        assertTrue(RockSmashSite.parse(ByteArray(3)) is FoxResult.Err)
    }

    @Test
    fun theThreeTablesSitBackToBackAndOnlyTheirSlotsChange() {
        val overlay = ByteArray(0x24000) { 0x77 }
        val items = (RockSmashItems.parse(overlay.also { o ->
            // Ruins at 0x23D04, default at 0x23D14, cliff at 0x23D24: item ids 1..8, 11..18, 21..28
            for (t in 0 until 3) for (s in 0 until 8) o[listOf(0x23D04, 0x23D14, 0x23D24)[t] + 2 * s] = (10 * (if (t == 1) 0 else if (t == 0) 1 else 2) + s + 1).toByte().also { _ -> o[listOf(0x23D04, 0x23D14, 0x23D24)[t] + 2 * s + 1] = 0 }
        }) as FoxResult.Ok).value
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), items.tables[RockSmashSite.DEFAULT])
        assertEquals(listOf(11, 12, 13, 14, 15, 16, 17, 18), items.tables[RockSmashSite.RUINS_OF_ALPH])
        val changed = items.with(RockSmashSite.CLIFF_CAVE, 7, 0x1234).applyTo(overlay)
        assertEquals(0x34, changed[0x23D24 + 14].toInt() and 0xFF)
        assertEquals(0x12, changed[0x23D24 + 15].toInt())
        assertEquals(0x77, changed[0x23D24 + 16].toInt(), "what follows the tables is left alone")
        assertTrue(RockSmashItems.parse(ByteArray(10)) is FoxResult.Err)
        assertEquals(100, RockSmashItems.SLOT_CHANCE.sum())
    }
}
