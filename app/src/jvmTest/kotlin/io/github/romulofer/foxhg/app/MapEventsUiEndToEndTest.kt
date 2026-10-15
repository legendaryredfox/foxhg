package io.github.romulofer.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.map.MapUsages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Selecting, dragging, duplicating and removing an event by clicking the map canvas and the event panel. */
@OptIn(ExperimentalTestApi::class)
class MapEventsUiEndToEndTest {
    private val tilePx = 18f

    private fun center(tile: Int) = tile * tilePx + tilePx / 2

    @Test
    fun anEventIsSelectedDraggedDuplicatedAndRemovedOnTheMapCanvas() = withHeartGoldApp(height = 1500) { model, project ->
        val finder = MapEditorModel(project)
        // a map whose own cell holds an NPC, as in MapEventsEndToEndTest
        val map = (0 until finder.maps.recordCount).first { m ->
            val u = finder.usage(m)
            val f = finder.eventsOf(u)
            u != null && f != null && f.overworlds.any { !it.isAlias && it.x / 32 == u.x && it.y / 32 == u.y }
        }
        val usage = finder.usage(map)!!
        val eventFile = MapUsages(project).headers[usage.header].eventFile
        val files = { (EventFile.load(project) as FoxResult.Ok).value.records }
        val before = files()[eventFile]
        val npc = before.overworlds.first { !it.isAlias && it.x / 32 == usage.x && it.y / 32 == usage.y }
        val (kind, index) = before.at(npc.x, npc.y).first()
        val tile = npc.x % 32 to npc.y % 32
        val step = if (tile.first < 31) 1 else -1

        open("Maps")
        onNodeWithTag("search:Search…").performTextInput("%03d".format(map))
        waitForIdle()
        clickFirstText(nameOf(finder.maps.label(map)))
        tap(onNodeWithTag("chip:Edit events"))
        onNodeWithText("No event selected.").assertIsDisplayed()

        onNodeWithTag("map-canvas").performMouseInput {
            moveTo(Offset(center(tile.first), center(tile.second)))
            press()
            moveTo(Offset(center(tile.first + step), center(tile.second)))
            release()
        }
        waitForIdle()
        assertEquals((npc.x + step) to npc.y, files()[eventFile].positionOf(kind, index), "the dragged event moved one tile")
        assertEquals(before.count, files()[eventFile].count, "dragging adds nothing")

        tap(onNodeWithText("Duplicate"))
        assertEquals(before.count(kind) + 1, files()[eventFile].count(kind))
        tap(onNodeWithText("Remove"))
        assertEquals(before.count(kind), files()[eventFile].count(kind))
        onNodeWithText("No event selected.").assertIsDisplayed()

        tap(onNodeWithText("Add warp"))
        assertEquals(before.warps.size + 1, files()[eventFile].warps.size)
        val expected = files()[eventFile]
        assertNotNull(expected.positionOf(kind, index))
        buildAndReopen(model) { rebuilt ->
            assertEquals(expected, (EventFile.load(rebuilt) as FoxResult.Ok).value.records[eventFile])
        }
    }
}
