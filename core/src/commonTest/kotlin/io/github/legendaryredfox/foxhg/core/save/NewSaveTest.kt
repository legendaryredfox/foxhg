package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewSaveTest {
    private val place = Location(68, -1, 3, 6, 2)
    private fun made() = (NewSave.create("FOX", 1, place) as FoxResult.Ok).value

    @Test
    fun theSaveIsWholeAndReadsBack() {
        val save = made()
        assertEquals(listOf(false, false, true, true), save.blocks.map { it.valid })
        assertTrue(save.bytes.copyOf(SaveFormat.SLOT_OFFSETS[1]).all { it == 0xFF.toByte() }, "slot 0 is blank")
        val player = (PlayerData.read(save) as FoxResult.Ok).value
        assertEquals(listOf("FOX", 1, 3000L, 7, 2), listOf(player.name, player.gender, player.money, player.version, player.language))
        assertEquals(place, (LocalFieldData.read(save) as FoxResult.Ok).value.current)
        assertEquals(0, (PartyData.read(save) as FoxResult.Ok).value.count)
        val pc = (PcStorageData.read(save) as FoxResult.Ok).value
        assertEquals(0, pc.total)
        assertEquals("BOX 18", pc.boxName(17))
        assertEquals(0, (BagData.read(save) as FoxResult.Ok).value.slots.count { !it.isEmpty })
        val general = save.current(BlockKind.GENERAL)!!
        val flags = save.chunk(general, SaveChunks.byId(SaveChunks.FLAGS))
        assertTrue(NewSave.START_MENU_FLAGS.all { SaveVarsFlags.isSet(flags, it) })
        assertTrue(SaveVarsFlags.isSet(flags, SaveVarsFlags.CONTINUE_AT_DYNAMIC_WARP))
        assertTrue(SaveFile.parse(save.toFileBytes()) is FoxResult.Ok)
    }

    @Test
    fun aBadNameIsRefused() {
        assertTrue(NewSave.create("", 0, place) is FoxResult.Err)
        assertTrue(NewSave.create("EIGHTCHAR", 0, place) is FoxResult.Err)
    }
}
