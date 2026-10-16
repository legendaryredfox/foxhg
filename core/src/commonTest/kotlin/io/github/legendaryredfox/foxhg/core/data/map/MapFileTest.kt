package io.github.legendaryredfox.foxhg.core.data.map

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MapFileTest {
    private fun blank(): MapFile {
        val sound = byteArrayOf(0x34, 0x12, 0, 0)
        return MapFile(sound, ByteArray(MapFile.PERMISSION_BYTES), emptyList(), ByteArray(0), ByteArray(0))
    }

    @Test
    fun fileWritesAndReadsBack() {
        val m = blank().withTile(3, 4, type = 2, collision = 0x80)
        val back = (MapFile.parse(m.toBytes()) as FoxResult.Ok).value
        assertEquals(m, back)
        assertEquals(2, back.type(3, 4))
        assertEquals(0x80, back.collision(3, 4))
    }

    @Test
    fun floodFillStopsAtDifferentTiles() {
        // A wall of blocked tiles down column 5 splits the map.
        var m = blank()
        for (y in 0 until 32) m = m.withTile(5, y, collision = 0x80)
        val painted = m.filled(0, 0, collision = 0, type = 7)
        assertEquals(7, painted.type(4, 31))
        assertEquals(0, painted.type(6, 0), "the blocked column cuts the map in two")
    }

    @Test
    fun fillWithAFieldThatMatchesDoesNothing() {
        val m = blank()
        assertSame(m, m.filled(1, 1, type = 0))
    }

    @Test
    fun fillRespectsTheComparedField() {
        var m = blank()
        for (y in 0 until 32) m = m.withTile(5, y, type = 9)
        val painted = m.filled(0, 0, type = 7)
        assertEquals(7, painted.type(4, 0))
        assertEquals(9, painted.type(5, 0), "the wall keeps its own type")
        assertEquals(0, painted.type(6, 0), "tiles behind the wall are not reached")
    }
}
