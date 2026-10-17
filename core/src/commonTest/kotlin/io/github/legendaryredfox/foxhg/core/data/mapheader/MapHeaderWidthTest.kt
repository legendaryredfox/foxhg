package io.github.legendaryredfox.foxhg.core.data.mapheader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapHeaderWidthTest {
    private val header = MapHeader(
        wildEncounterFile = 255, areaData = 12, coordinates = 0xFFFF, matrix = 3, script = 4, levelScript = 5, textArchive = 6,
        musicDay = 7, musicNight = 8, eventFile = 9, locationName = 10, areaProperties = 0xFF, flags32 = 0xFFFFFFFFL,
    )

    @Test
    fun aHeaderThatFitsHasNoTooWideField() {
        assertEquals(emptyList(), header.tooWide())
    }

    @Test
    fun aValueTooWideForItsPlaceIsNamed() {
        assertEquals(listOf("area data 256 (largest is 255)"), header.copy(areaData = 256).tooWide())
        assertTrue(header.copy(eventFile = 70000).tooWide().single().startsWith("event file 70000"))
        assertTrue(header.copy(matrix = -1).tooWide().isNotEmpty())
        assertTrue(header.copy(flags32 = 0x1_0000_0000L).tooWide().isNotEmpty())
    }
}
