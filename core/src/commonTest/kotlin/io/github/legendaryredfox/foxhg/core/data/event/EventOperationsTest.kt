package io.github.legendaryredfox.foxhg.core.data.event

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertEquals

class EventOperationsTest {
    private val base = EventFile(
        overworlds = listOf(OverworldEvent(5, 1, 0, 0, 0, 100, 1, 0, 0, 0, 0, 0, 40, 41, 0)),
        warps = listOf(Warp(40, 41, 7, 1, 0)),
        triggers = listOf(Trigger(9, 40, 40, 3, 2, 0, 0, 0)),
        spawnables = listOf(Spawnable(1, 0, 33, 0, 35, 0, 0, 0, 0)),
    )

    @Test
    fun eventsAtATileIncludeTriggersThatCoverIt() {
        assertEquals(listOf(EventKind.OVERWORLD to 0, EventKind.WARP to 0, EventKind.TRIGGER to 0), base.at(40, 41))
        assertEquals(listOf(EventKind.TRIGGER to 0), base.at(42, 40), "a 3 x 2 trigger covers its whole area")
        assertEquals(emptyList(), base.at(43, 40))
    }

    @Test
    fun moveRemoveDuplicateAndAddKeepTheFileWritable() {
        val moved = base.moved(EventKind.OVERWORLD, 0, 50, 51)
        assertEquals(50 to 51, moved.positionOf(EventKind.OVERWORLD, 0))
        assertEquals(40 to 41, moved.positionOf(EventKind.WARP, 0), "other events stay")
        assertEquals(0, base.removed(EventKind.WARP, 0).warps.size)
        val dup = base.duplicated(EventKind.OVERWORLD, 0)
        assertEquals(listOf(5, 6), dup.overworlds.map { it.id }, "the copy gets the next object id")
        assertEquals(41, dup.overworlds[1].x)
        val added = base.added(EventKind.WARP, 3, 4)
        assertEquals(2, added.warps.size)
        assertEquals(3 to 4, added.positionOf(EventKind.WARP, 1))
        assertEquals(7, added.warps[1].header, "a new warp starts like the last one")
        val empty = EventFile().added(EventKind.OVERWORLD, 1, 2).added(EventKind.TRIGGER, 1, 2).added(EventKind.SIGN, 1, 2)
        assertEquals(3, empty.count)
        assertEquals(empty, (EventFile.parse(empty.toBytes()) as FoxResult.Ok).value)
    }
}
