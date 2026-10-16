package io.github.romulofer.foxhg.core.data.encounter

import io.github.romulofer.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncounterRosterTest {
    private fun area(): EncounterData {
        val blank = (EncounterData.parse(ByteArray(EncounterData.SIZE)) as FoxResult.Ok).value
        // Pidgey 16 in slots 0, 1 (40%), Sentret 161 in 2 to 5 (40%), Rattata 19 in 6 to 9 (18%), Hoothoot 163 in 10, 11 (2%)
        val land = listOf(16, 16, 161, 161, 161, 161, 19, 19, 19, 19, 163, 163)
        return blank.copy(
            landMorning = land, landDay = land, landNight = land,
            surf = listOf(72, 72, 73, 73, 73).mapIndexed { i, s -> EncounterSlot(10 + i, 20 + i, s) },
        )
    }

    @Test
    fun rosterSumsSlotChancesAndSkipsEmptySlots() {
        val roster = EncounterGroup.GRASS_DAY.roster(area())
        assertEquals(listOf(16, 161, 19, 163), roster.map { it.species }.sortedBy { listOf(16, 161, 19, 163).indexOf(it) })
        assertEquals(40, roster.first { it.species == 16 }.percent)
        assertEquals(2, roster.first { it.species == 163 }.percent)
        assertEquals(100, roster.sumOf { it.percent })
        assertTrue(EncounterGroup.ROCK_SMASH.roster(area()).isEmpty())
    }

    @Test
    fun removeHandsTheSlotsToTheOthersAndKeepsTheTotal() {
        val after = assertNotNull(EncounterGroup.GRASS_DAY.remove(area(), 19))
        val roster = EncounterGroup.GRASS_DAY.roster(after)
        assertTrue(roster.none { it.species == 19 })
        assertEquals(100, roster.sumOf { it.percent })
        assertTrue(roster.all { it.species in listOf(16, 161, 163) })
        // the other times of day and the levels are not touched
        assertEquals(area().landMorning, after.landMorning)
        assertEquals(area().landLevels, after.landLevels)
    }

    @Test
    fun removeFromSlotsCopiesTheLevelRangeOfTheNewOwner() {
        val after = assertNotNull(EncounterGroup.SURF.remove(area(), 72))
        assertTrue(after.surf.none { it.species == 72 })
        // 73 is the only one left: both freed slots become a copy of its first slot, levels included
        val from = area().surf
        assertEquals(from[2], after.surf[0])
        assertEquals(from[2], after.surf[1])
        assertEquals(from.drop(2), after.surf.drop(2))
        assertEquals(100, EncounterGroup.SURF.roster(after).sumOf { it.percent })
    }

    @Test
    fun theLastPokemonAndAbsentOnesCannotBeRemoved() {
        val one = area().copy(landDay = List(12) { 16 })
        assertNull(EncounterGroup.GRASS_DAY.remove(one, 16))
        assertNull(EncounterGroup.GRASS_DAY.remove(area(), 999))
    }

    @Test
    fun addTakesSlotsFromTheBiggestOwnersAndNeverEmptiesOne() {
        val after = assertNotNull(EncounterGroup.GRASS_DAY.add(area(), 129, 10))
        val roster = EncounterGroup.GRASS_DAY.roster(after)
        assertEquals(10, roster.first { it.species == 129 }.percent)
        assertEquals(100, roster.sumOf { it.percent })
        assertEquals(setOf(16, 161, 19, 163, 129), roster.map { it.species }.toSet())
    }

    @Test
    fun addKeepsTheOldSlotLevelsAndOnlyChangesTheSpecies() {
        val after = assertNotNull(EncounterGroup.SURF.add(area(), 129, 5))
        val changed = after.surf.indices.filter { after.surf[it].species == 129 }
        assertEquals(1, changed.size)
        assertEquals(area().surf[changed.single()].copy(species = 129), after.surf[changed.single()])
    }

    @Test
    fun addRefusesDuplicatesAndFullRosters() {
        assertNull(EncounterGroup.GRASS_DAY.add(area(), 16, 10))
        assertNull(EncounterGroup.GRASS_DAY.add(area(), 0, 10))
        val allSingle = area().copy(surf = listOf(1, 2, 3, 4, 5).map { EncounterSlot(5, 5, it) })
        assertNull(EncounterGroup.SURF.add(allSingle, 9, 5))
    }

    @Test
    fun addToAnEmptyGroupTakesEverySlot() {
        val after = assertNotNull(EncounterGroup.ROCK_SMASH.add(area(), 74, 10))
        assertEquals(listOf(74, 74), after.rockSmash.map { it.species })
    }

    @Test
    fun addWithATinyPercentStillGivesOneSlot() {
        val after = assertNotNull(EncounterGroup.GRASS_NIGHT.add(area(), 129, 0))
        assertEquals(1, EncounterGroup.GRASS_NIGHT.roster(after).first { it.species == 129 }.percent)
    }
}
