package io.github.legendaryredfox.foxhg.core.data.encounter

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EncounterTest {
    @Test
    fun anyRecordRoundTripsByteForByte() {
        val random = Random(11)
        repeat(100) {
            val bytes = ByteArray(EncounterData.SIZE) { random.nextInt(256).toByte() }
            assertContentEquals(bytes, (EncounterData.parse(bytes) as FoxResult.Ok).value.toBytes())
        }
        assertIs<FoxResult.Err>(EncounterData.parse(ByteArray(195)))
    }

    @Test
    fun fieldsLandWhereTheLayoutSaysTheyDo() {
        val b = ByteArray(EncounterData.SIZE)
        b[0] = 30; b[8] = 5; b[9] = 6
        b[20] = 16; b[44] = 19; b[68] = 21           // slot 1: morning, day, night species
        b[0x64] = 20; b[0x65] = 25; b[0x66] = 118    // first surf slot
        b[0xBC] = 0x2A                                  // land swarm
        val e = (EncounterData.parse(b) as FoxResult.Ok).value
        assertEquals(30, e.rateWalking)
        assertEquals(listOf(5, 6), e.landLevels.take(2))
        assertEquals(listOf(16, 19, 21), listOf(e.landMorning[0], e.landDay[0], e.landNight[0]))
        assertEquals(EncounterSlot(20, 25, 118), e.surf[0])
        assertEquals(0x2A, e.swarmLand)
    }

    @Test
    fun diffListsOnlyWhatDiffers() {
        val a = (EncounterData.parse(ByteArray(EncounterData.SIZE)) as FoxResult.Ok).value
        assertEquals(emptyList(), EncounterDiff.between(a, a))
        val b = a.copy(landDay = a.landDay.toMutableList().also { it[2] = 167 }, swarmFish = 129)
        val changes = EncounterDiff.between(a, b)
        assertEquals(listOf("land 3 day", "fishing swarm"), changes.map { it.what })
        assertEquals(167, changes[0].to)
    }

    @Test
    fun areaLabelsGroupMapsAndHandleUnused() {
        val areas = EncounterAreas(mapOf(7 to listOf(10, 11)), { if (it == 10) "Route 29" else "Route 30" })
        assertEquals("007  Route 29 +1", areas.label(7))
        assertEquals("008  (unused)", areas.label(8))
        assertTrue(areas.maps(7).containsAll(listOf(10, 11)))
    }
}
