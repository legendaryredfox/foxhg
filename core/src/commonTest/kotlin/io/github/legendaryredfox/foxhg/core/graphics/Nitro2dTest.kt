package io.github.legendaryredfox.foxhg.core.graphics

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import kotlin.test.Test
import kotlin.test.assertIs

class Nitro2dTest {
    /** An NCGR with one RAHC section whose header says [heightTiles] x [widthTiles] tiles and [length] bytes of pixels at [offset]. */
    private fun ncgr(heightTiles: Int, widthTiles: Int, length: Int, offset: Int): ByteArray {
        val b = ByteArray(0x50)
        "RGCN".encodeToByteArray().copyInto(b, 0)
        b.putU16(12, 16); b.putU16(14, 1)
        val s = 0x10
        "RAHC".encodeToByteArray().copyInto(b, s)
        b.putU32(s + 4, (b.size - s).toLong())
        b.putU16(s + 8, heightTiles); b.putU16(s + 10, widthTiles)
        b.putU32(s + 12, 3)
        b.putU32(s + 24, length.toLong()); b.putU32(s + 28, offset.toLong())
        return b
    }

    @Test
    fun aHeaderThatClaimsAHugePictureIsAnErrorNotAnAllocation() {
        assertIs<FoxResult.Err>(Ncgr.parse(ncgr(0x7FFF, 0x7FFF, 0x20, 0x18)))
        assertIs<FoxResult.Err>(Ncgr.parse(ncgr(2048, 2048, 0x20, 0x18)))
    }

    @Test
    fun pixelDataOutsideTheFileIsAnError() {
        assertIs<FoxResult.Err>(Ncgr.parse(ncgr(1, 1, 0x20, 0x7FFFFFF0)))
        assertIs<FoxResult.Err>(Ncgr.parse(ncgr(1, 1, -1, 0x18)))
    }

    @Test
    fun aSmallPictureStillParses() {
        assertIs<FoxResult.Ok<Ncgr>>(Ncgr.parse(ncgr(1, 1, 0x20, 0x18)))
    }
}
