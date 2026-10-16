package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putAscii
import io.github.legendaryredfox.foxhg.core.putU32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NdsHeaderTest {
    private fun syntheticHeader(gameCode: String): ByteArray {
        val h = ByteArray(NdsHeader.SIZE)
        h.putAscii(0x00, "POKEMON HG")
        h.putAscii(0x0C, gameCode)
        h.putAscii(0x10, "01")
        h.putU32(0x20, 0x4000)
        h.putU32(0x2C, 0xD7F00)
        h.putU32(0x40, 0x1000000)
        h.putU32(0x44, 0x7000)
        h.putU32(0x48, 0x1007000)
        h.putU32(0x4C, 0x9000)
        return h
    }

    @Test
    fun parsesFieldsAndIdentifiesHeartGoldEnglish() {
        val header = (NdsHeader.parse(syntheticHeader("IPKE")) as FoxResult.Ok).value
        assertEquals("POKEMON HG", header.gameTitle)
        assertEquals("IPKE", header.gameCode)
        assertEquals(Game.HEARTGOLD_EN, header.game)
        assertEquals(RomRegion(0x4000, 0xD7F00), header.arm9)
        assertEquals(RomRegion(0x1000000, 0x7000), header.fileNameTable)
        assertEquals(RomRegion(0x1007000, 0x9000), header.fileAllocationTable)
    }

    @Test
    fun identifiesSoulSilverAndOtherLanguages() {
        assertEquals(Game.SOULSILVER_EN, Game.fromGameCode("IPGE"))
        assertEquals(Game.HEARTGOLD_OTHER, Game.fromGameCode("IPKS"))
        assertEquals(Game.UNKNOWN, Game.fromGameCode("BPEE"))
    }

    @Test
    fun rejectsShortInput() {
        assertIs<FoxResult.Err>(NdsHeader.parse(ByteArray(16)))
    }

    @Test
    fun rejectsInvalidGameCode() {
        assertIs<FoxResult.Err>(NdsHeader.parse(ByteArray(NdsHeader.SIZE)))
    }
}
