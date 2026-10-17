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

    @Test
    fun flagsAndVariablesEditAndRead() {
        val chunk = ByteArray(SaveChunks.byId(SaveChunks.FLAGS).size)
        val flagged = SaveVarsFlags.with(SaveVarsFlags.with(chunk, 0x6A, true), 2911, true)
        assertEquals(listOf(0x6A, 2911), SaveVarsFlags.setFlags(flagged))
        val varred = SaveVarsFlags.withVariable(SaveVarsFlags.withVariable(flagged, 5, 0xABCD), 367, 1)
        assertEquals(listOf(5, 367), SaveVarsFlags.usedVariables(varred))
        assertEquals(0xABCD, SaveVarsFlags.variable(varred, 5))
        assertEquals(listOf(0x6A, 2911), SaveVarsFlags.setFlags(varred), "writing a variable leaves the flags alone")
        assertTrue(runCatching { SaveVarsFlags.withVariable(chunk, 368, 1) }.isFailure)
        assertTrue(runCatching { SaveVarsFlags.withVariable(chunk, 0, 65536) }.isFailure)
        val save = made()
        val written = (save.withFlagsChunk(varred) as FoxResult.Ok).value
        assertEquals(varred.toList(), (written.flagsChunk() as FoxResult.Ok).value.toList())
        assertTrue(written.block(1, BlockKind.GENERAL).valid)
    }

    @Test
    fun thePokedexIsFreshAndCaughtNeedsSeen() {
        val dex = (PokedexData.read(made()) as FoxResult.Ok).value
        assertEquals(PokedexData.fresh(), dex)
        assertEquals(0L, dex.seenCount.toLong())
        val caught = dex.withCaught(1, true).withCaught(493, true)
        assertTrue(caught.isSeen(1) && caught.isCaught(493))
        assertEquals(2, caught.caughtCount)
        val unseen = caught.withSeen(1, false)
        assertTrue(!unseen.isSeen(1) && !unseen.isCaught(1) && unseen.isCaught(493))
        assertEquals(PokedexData.SPECIES, dex.withAll(true).caughtCount)
        assertEquals(0, dex.withAll(true).cleared().seenCount)
        assertEquals(dex, dex.withAll(true).cleared(), "clearing a full Pokédex gives the fresh one")
        assertTrue(runCatching { dex.isSeen(0) }.isFailure)
        assertTrue(runCatching { dex.isSeen(494) }.isFailure)
        assertTrue(PokedexData.parse(ByteArray(PokedexData.SIZE)) is FoxResult.Err, "no magic")
    }
}
