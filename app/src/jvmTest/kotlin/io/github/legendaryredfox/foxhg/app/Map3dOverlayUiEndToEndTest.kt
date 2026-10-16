package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.event.EventKind
import io.github.legendaryredfox.foxhg.core.data.map.MapUsages
import io.github.legendaryredfox.foxhg.core.graphics.Camera3d
import io.github.legendaryredfox.foxhg.core.graphics.MapScenes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Events and collision drawn over the 3D view: the markers are where the model says, and clicking one selects the event. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class Map3dOverlayUiEndToEndTest {
    @Test
    fun theMarkersSitOnTheEventsAndClickingOneSelectsIt() = withHeartGoldApp(height = 1100) { _, project ->
        val scenes = MapScenes(project)
        val usage = MapUsages(project).of(0).first()
        val events = (EventFile.load(project) as FoxResult.Ok).value.records[MapUsages(project).headers[usage.header].eventFile]
        val picture = scenes.render3d(0, Camera3d(), 640, 400)!!
        val markers = eventMarkers(picture, events, usage, scenes.heightGrid(0))
        assertTrue(markers.any { it.kind == EventKind.OVERWORLD }, "some NPCs of New Bark Town are in view")
        assertTrue(markers.any { it.kind == EventKind.WARP }, "and some warps")
        // every marker belongs to an event of this map's cell and none lies outside the picture
        assertTrue(markers.all { it.x in 0.0..640.0 && it.y in 0.0..400.0 })
        val npc = markers.first { it.kind == EventKind.OVERWORLD }

        open("Maps")
        onNodeWithTag("map3d").performScrollTo()
        waitUntil(timeoutMillis = 30_000) {
            onNodeWithTag("map3d").performScrollTo().performMouseInput { click(Offset(npc.x.toFloat(), npc.y.toFloat())) }
            onAllNodesWithText("Object ${npc.index + 1} of", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, onAllNodesWithText("Object ${npc.index + 1} of ${events.overworlds.size}").fetchSemanticsNodes().size, "the clicked object is the selected event")
    }

    @Test
    fun theOverlayChipsToggleWithoutBreakingTheView() = withHeartGoldApp(height = 1100) { _, _ ->
        open("Maps")
        onNodeWithTag("map3d").performScrollTo()
        tap(onNodeWithTag("chip:3D collision"))
        tap(onNodeWithTag("chip:3D surface"))
        tap(onNodeWithTag("chip:3D events"))
        tap(onNodeWithTag("chip:3D collision"))
        onNodeWithTag("map3d").performScrollTo()
        waitForIdle()
    }
}
