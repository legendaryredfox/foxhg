package io.github.legendaryredfox.foxhg.core.data.matrix

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameMatrixTest {
    private fun sample(headers: Boolean = false, altitudes: Boolean = false) = GameMatrix(
        width = 3, height = 2, nameBytes = "map".encodeToByteArray(),
        headers = if (headers) intArrayOf(1, 2, 3, 4, 5, 6) else null,
        altitudes = if (altitudes) intArrayOf(0, 1, 2, 3, 4, 5) else null,
        maps = intArrayOf(10, 11, GameMatrix.EMPTY, 13, 14, 15),
    )

    @Test
    fun everySectionCombinationRoundTrips() {
        for (h in listOf(false, true)) for (a in listOf(false, true)) {
            val m = sample(h, a)
            val parsed = (GameMatrix.parse(m.toBytes()) as FoxResult.Ok).value
            assertEquals(m, parsed, "headers=$h altitudes=$a")
            assertContentEquals(m.toBytes(), parsed.toBytes())
        }
    }

    @Test
    fun cellsAreRowMajorAndEditsAreImmutable() {
        val m = sample(headers = true, altitudes = true)
        assertEquals(13, m.map(0, 1))
        assertEquals(GameMatrix.EMPTY, m.map(2, 0))
        assertEquals(6, m.header(2, 1))
        assertEquals(5, m.altitude(2, 1))
        val edited = m.withMap(2, 0, 99).withHeader(0, 0, 500).withAltitude(1, 1, 7)
        assertEquals(99, edited.map(2, 0))
        assertEquals(500, edited.header(0, 0))
        assertEquals(7, edited.altitude(1, 1))
        assertEquals(GameMatrix.EMPTY, m.map(2, 0), "the original is untouched")
        assertNull(sample().header(0, 0), "no header section")
    }

    @Test
    fun resizingKeepsOldCellsAndFillsNewOnesEmpty() {
        val m = sample(headers = true, altitudes = true)
        val bigger = m.resized(4, 3)
        assertEquals(4 * 3, bigger.maps.size)
        assertEquals(listOf(10, 11, GameMatrix.EMPTY, GameMatrix.EMPTY), (0 until 4).map { bigger.map(it, 0) })
        assertEquals(listOf(13, 14, 15, GameMatrix.EMPTY), (0 until 4).map { bigger.map(it, 1) })
        assertEquals(List(4) { GameMatrix.EMPTY }, (0 until 4).map { bigger.map(it, 2) })
        assertEquals(6, bigger.header(2, 1))
        assertEquals(0, bigger.header(3, 2))
        val smaller = m.resized(2, 1)
        assertEquals(listOf(10, 11), smaller.maps.toList())
        assertContentEquals((GameMatrix.parse(smaller.toBytes()) as FoxResult.Ok).value.maps, smaller.maps)
    }

    @Test
    fun sectionsCanBeAddedAndRemoved() {
        val m = sample()
        val withBoth = m.withSections(headers = true, altitudes = true)
        assertTrue(withBoth.hasHeaders && withBoth.hasAltitudes)
        assertEquals(List(6) { 0 }, withBoth.headers!!.toList())
        assertFalse(withBoth.withSections(false, false).hasHeaders)
    }

    @Test
    fun malformedDataIsRejected() {
        assertIs<FoxResult.Err>(GameMatrix.parse(ByteArray(3)))
        assertIs<FoxResult.Err>(GameMatrix.parse(byteArrayOf(0, 1, 0, 0, 0)))
        assertIs<FoxResult.Err>(GameMatrix.parse(sample().toBytes().copyOf(8)))
        assertFailsWith<IllegalArgumentException> { GameMatrix(2, 2, ByteArray(0), null, null, IntArray(3)) }
    }

    @Test
    fun nameIsKeptByteForByte() {
        val m = GameMatrix(1, 1, byteArrayOf(0x6D, 0xFF.toByte(), 0x61), null, null, intArrayOf(1))
        assertContentEquals(m.toBytes(), (GameMatrix.parse(m.toBytes()) as FoxResult.Ok).value.toBytes())
    }
}
