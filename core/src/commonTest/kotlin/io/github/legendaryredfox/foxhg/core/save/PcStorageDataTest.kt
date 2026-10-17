package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcStorageDataTest {
    /** A PC as the game's own init leaves it: empty slots, `BOX n` names are not needed here, wallpapers 0 to 15 then again. */
    private fun fresh(): PcStorageData {
        val empty = Pokemon.EMPTY
        return PcStorageData(
            List(PcStorageData.BOXES) { List(PcStorageData.PER_BOX) { empty } }, List(PcStorageData.BOXES) { ByteArray(PcStorageData.TAIL) }, 0, 0,
            List(PcStorageData.BOXES) { box -> List(PcStorageData.NAME_SLOTS) { if (it == 0) PcStorageData.END else 0 } }.also { }, List(PcStorageData.BOXES) { it % 16 }, 0, ByteArray(PcStorageData.FILLER),
        )
    }

    @Test
    fun theLayoutFillsTheChunkExactly() {
        assertEquals(0x1000, PcStorageData.BOX_STRIDE)
        assertEquals(0x12000, PcStorageData.CURRENT_BOX)
        assertEquals(0x12008, PcStorageData.NAMES)
        assertEquals(0x122D8, PcStorageData.WALLPAPERS_AT)
        assertEquals(PcStorageData.SIZE, PcStorageData.WALLPAPERS_AT + PcStorageData.BOXES + 1 + PcStorageData.FILLER)
        assertEquals(SaveChunks.byId(SaveChunks.PCSTORAGE).size, PcStorageData.SIZE)
        assertEquals(0x88, Pokemon.BOX_SIZE)
    }

    @Test
    fun aBoxedPokemonRoundTripsAndKeepsItsBlocks() {
        val mon = Pokemon.EMPTY.withSpecies(25).withExperience(8000).withMove(0, 85, 15).withIv(2, 17).withHeldItem(17)
        val bytes = mon.toBoxBytes()
        assertEquals(Pokemon.BOX_SIZE, bytes.size)
        val back = (Pokemon.parseBox(bytes) as FoxResult.Ok).value
        assertEquals(listOf(25, 17), listOf(back.species, back.heldItem))
        assertEquals(8000L, back.experience)
        assertEquals(17, back.ivs[2])
        assertContentEquals(bytes, back.toBoxBytes())
        // the party form starts with the same bytes
        assertContentEquals(bytes, mon.toBytes().copyOf(Pokemon.BOX_SIZE))
        bytes[20] = (bytes[20] + 1).toByte()
        assertTrue(Pokemon.parseBox(bytes) is FoxResult.Err)
    }

    @Test
    fun thePcRoundTripsAndEditsStick() {
        val pc = fresh()
        val bytes = pc.toBytes()
        assertEquals(PcStorageData.SIZE, bytes.size)
        assertEquals(pc, (PcStorageData.parse(bytes) as FoxResult.Ok).value)
        assertContentEquals(bytes, (PcStorageData.parse(bytes) as FoxResult.Ok).value.toBytes())
        assertEquals(0, pc.total)

        val mon = Pokemon.EMPTY.withSpecies(16).withExperience(100)
        var edited = pc.placing(3, 29, mon).placing(0, 0, mon.withSpecies(25))
        edited = (edited.withBoxName(2, "FOX") as FoxResult.Ok).value
        edited = (edited.withWallpaper(2, 33) as FoxResult.Ok).value
        val again = (PcStorageData.parse(edited.toBytes()) as FoxResult.Ok).value
        assertEquals(edited, again)
        assertEquals(listOf(25), again.occupied(0).map { it.species })
        assertEquals(16, again.boxes[3][29].species)
        assertEquals(2, again.total)
        assertEquals("FOX", again.boxName(2))
        assertEquals(33, again.wallpapers[2])
        assertEquals(1, again.firstEmpty(0))
        assertEquals(0, again.clearing(0, 0).occupied(0).size)
    }

    @Test
    fun badEditsAreRefused() {
        val pc = fresh()
        assertTrue(pc.withBoxName(0, "") is FoxResult.Err)
        assertTrue(pc.withBoxName(0, "NINECHARS") is FoxResult.Err)
        assertTrue(pc.withWallpaper(0, 20) is FoxResult.Err)
        assertTrue(PcStorageData.parse(ByteArray(10)) is FoxResult.Err)
    }
}
