package io.github.legendaryredfox.foxhg.core.patch

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShinyRatesTest {
    @Test
    fun percentsAndBasisPointsConvertBothWays() {
        assertEquals(6554, ShinyRates.fromPercent(10.0))
        assertEquals(65536, ShinyRates.fromPercent(100.0))
        assertEquals(0, ShinyRates.fromPercent(0.0))
        assertEquals(6554, ShinyRates.fromBasisPoints(1000))
        assertEquals(1000, ShinyRates.toBasisPoints(6554))
        assertEquals(10000, ShinyRates.toBasisPoints(65536))
        assertEquals(8, ShinyRates.fromPercent(8 * 100.0 / 65536))
    }

    @Test
    fun theTableIsFourLittleEndianWordsAndReadsBack() {
        val rates = ShinyRates(1, 2, 65536, 0x1234)
        val bytes = rates.toBytes()
        assertContentEquals(byteArrayOf(1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1, 0, 0x34, 0x12, 0, 0), bytes)
        assertEquals(rates, ShinyRates.parse(bytes, 0))
        assertFailsWith<IllegalArgumentException> { ShinyRates(65537, 0, 0, 0) }
    }
}
