package io.github.legendaryredfox.foxhg.core.compression

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlzTest {
    private fun roundTrip(data: ByteArray, rawPrefix: Int = 0): ByteArray {
        val result = Blz.compress(data, rawPrefix)
        val packed = (result as? FoxResult.Ok)?.value ?: error("compress failed: ${(result as FoxResult.Err).message}")
        assertTrue(Blz.looksCompressed(packed))
        assertTrue(Blz.isInPlaceSafe(packed), "stream must be safe to decompress in place")
        assertEquals(0, packed.size % 4)
        assertContentEquals(data, (Blz.decompress(packed) as FoxResult.Ok).value)
        return packed
    }

    @Test
    fun repetitiveDataShrinksAndRoundTrips() {
        val data = ByteArray(10_000) { (it % 7).toByte() }
        assertTrue(roundTrip(data).size < data.size / 4)
    }

    @Test
    fun zerosRoundTrip() {
        roundTrip(ByteArray(5000))
    }

    @Test
    fun textLikeDataRoundTrips() {
        val words = listOf("pokemon", "heart", "gold", "soul", "silver", "route", "city")
        val random = Random(7)
        val text = buildString { repeat(2000) { append(words[random.nextInt(words.size)]).append(' ') } }
        roundTrip(text.encodeToByteArray())
    }

    @Test
    fun incompressibleHeadIsKeptRawSoInPlaceDecodingStaysSafe() {
        val random = Random(1)
        val head = ByteArray(3000) { random.nextInt(256).toByte() }
        val tail = ByteArray(20_000) { (it % 5).toByte() }
        roundTrip(head + tail)
    }

    @Test
    fun rawPrefixIsLeftUntouched() {
        val data = ByteArray(8000) { (it % 11).toByte() }
        val packed = roundTrip(data, rawPrefix = 0x400)
        assertContentEquals(data.copyOf(0x400), packed.copyOf(0x400))
    }

    @Test
    fun randomDataIsRejectedNotCorrupted() {
        val random = Random(3)
        val data = ByteArray(4000) { random.nextInt(256).toByte() }
        assertIs<FoxResult.Err>(Blz.compress(data))
    }

    @Test
    fun rejectsGarbage() {
        assertIs<FoxResult.Err>(Blz.decompress(ByteArray(4)))
        assertIs<FoxResult.Err>(Blz.decompress(ByteArray(64) { 0x55 }))
    }

    @Test
    fun aHeaderThatClaimsAHugeGrowthIsAnError() {
        val packed = (Blz.compress(ByteArray(64) { (it % 7).toByte() }) as FoxResult.Ok).value
        val huge = packed.copyOf()
        // the last four bytes are the growth: 0x7FFF0000
        huge[huge.size - 4] = 0; huge[huge.size - 3] = 0; huge[huge.size - 2] = 0xFF.toByte(); huge[huge.size - 1] = 0x7F
        assertIs<FoxResult.Err>(Blz.decompress(huge))
        assertTrue(!Blz.looksCompressed(huge))
    }
}
