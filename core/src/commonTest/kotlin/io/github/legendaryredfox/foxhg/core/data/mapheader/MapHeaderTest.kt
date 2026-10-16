package io.github.legendaryredfox.foxhg.core.data.mapheader

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapHeaderTest {
    private fun header(random: Random) = MapHeader.parse(ByteArray(MapHeader.SIZE) { random.nextInt(256).toByte() }, 0)

    @Test
    fun anyHeaderRoundTrips() {
        val random = Random(3)
        repeat(100) {
            val bytes = ByteArray(MapHeader.SIZE) { random.nextInt(256).toByte() }
            assertContentEquals(bytes, MapHeader.parse(bytes, 0).toBytes())
        }
    }

    @Test
    fun packedFieldsAreIndependent() {
        val base = header(Random(8))
        // Setting a field to a value and reading it back never disturbs the others.
        val changed = base.withWeather(33).withCamera(41).withBattleBackground(17).withLocationType(9).withFollowMode(2)
            .withWorldMap(55, 12).withAreaIcon(7).withMomCallIntro(5).withKanto(true).withPermission(MapHeader.Permission.FLY, true)
        assertEquals(listOf(33, 41, 17, 9, 2), listOf(changed.weather, changed.camera, changed.battleBackground, changed.locationType, changed.followMode))
        assertEquals(listOf(55, 12), listOf(changed.worldMapX, changed.worldMapY))
        assertEquals(listOf(7, 5), listOf(changed.areaIcon, changed.momCallIntro))
        assertTrue(changed.isKanto)
        assertTrue(changed.permissions and (1 shl MapHeader.Permission.FLY.bit) != 0)
        // Unrelated fields of the original are untouched.
        assertEquals(base.coordinateUnknown, changed.coordinateUnknown)
        assertEquals(base.wildEncounterFile, changed.wildEncounterFile)
        assertEquals(base.musicDay, changed.musicDay)
        // Clearing a permission clears only that bit.
        val cleared = changed.withPermission(MapHeader.Permission.FLY, false)
        assertFalse(cleared.permissions and (1 shl MapHeader.Permission.FLY.bit) != 0)
        assertEquals(changed.permissions and (1 shl MapHeader.Permission.FLY.bit).inv(), cleared.permissions)
    }

    @Test
    fun valuesAreMaskedToTheirFieldWidth() {
        val h = header(Random(1)).withWeather(0xFFFF).withWorldMap(1000, 1000).withAreaIcon(0xFF)
        assertEquals(0x7F, h.weather)
        assertEquals(1000 and 63, h.worldMapX) // out-of-range bits are dropped, never spilled into neighbours
        assertEquals(1000 and 63, h.worldMapY)
        assertEquals(0xF, h.areaIcon)
    }
}
