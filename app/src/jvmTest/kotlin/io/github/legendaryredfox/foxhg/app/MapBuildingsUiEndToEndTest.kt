package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.map.MapFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Editing the buildings of a map in the 3D view and the building list, through the widgets. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class MapBuildingsUiEndToEndTest {
    private fun buildings(project: io.github.legendaryredfox.foxhg.core.project.RomProject) = (MapFile.load(project) as FoxResult.Ok).value.records[0].buildings

    @Test
    fun selectingEditingAddingAndRemovingBuildingsInTheList() = withHeartGoldApp(height = 1100) { model, project ->
        val before = buildings(project)
        open("Maps")
        onNodeWithTag("building:2").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("decimal:X").performScrollTo().performTextReplacement("3.5")
        waitForIdle()
        assertEquals(3.5, buildings(project)[2].x.value, "the typed position is written into the map")
        assertEquals(before[2].z, buildings(project)[2].z, "only X changed")
        onNodeWithTag("number:Model").performScrollTo().performTextReplacement("26")
        waitForIdle()
        assertEquals(26L, buildings(project)[2].modelId)

        tap(onNodeWithText("Duplicate building"))
        assertEquals(before.size + 1, buildings(project).size)
        tap(onNodeWithText("Remove building"))
        assertEquals(before.size, buildings(project).size)
        tap(onNodeWithText("Add building"))
        assertEquals(before.size + 1, buildings(project).size)

        model.undo()
        waitForIdle()
        assertEquals(before.size, buildings(project).size, "undo takes the added building away again")
    }

    @Test
    fun clickingTheGroundIn3dMovesTheSelectedBuilding() = withHeartGoldApp(height = 1100) { _, project ->
        val before = buildings(project)
        open("Maps")
        onNodeWithTag("building:2").performScrollTo().performClick()
        tap(onNodeWithTag("chip:Move here"))
        waitUntil(timeoutMillis = 30_000) {
            // The view renders in the background; a click before the picture exists does nothing, so click until it lands.
            onNodeWithTag("map3d").performScrollTo().performMouseInput { click(Offset(320f, 250f)) }
            buildings(project)[2].let { it.x != before[2].x || it.z != before[2].z }
        }
        val moved = buildings(project)[2]
        assertTrue(kotlin.math.abs(moved.x.value) < 12 && kotlin.math.abs(moved.z.value) < 12, "it moved to the ground that was clicked: ${moved.x.value}, ${moved.z.value}")
        assertEquals(before[2].y, moved.y, "the height stays")
    }

    @Test
    fun clickingABuildingIn3dSelectsItAndTheModelBrowserChangesItsModel() = withHeartGoldApp(height = 1100) { _, project ->
        val before = buildings(project)
        // Find a pixel of the default view that shows a building, the way the view itself draws it.
        val picture = io.github.legendaryredfox.foxhg.core.graphics.MapScenes(project).render3d(0, io.github.legendaryredfox.foxhg.core.graphics.Camera3d(), 640, 400)!!
        val (px, py) = (0 until 400).flatMap { y -> (0 until 640).map { x -> x to y } }.first { (x, y) -> picture.tagAt(x, y) >= 0 }
        val building = picture.tagAt(px, py)
        open("Maps")
        waitUntil(timeoutMillis = 30_000) {
            onNodeWithTag("map3d").performScrollTo().performMouseInput { click(Offset(px.toFloat(), py.toFloat())) }
            onAllNodesWithTag("number:Model").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(before[building].modelId.toString(), onNodeWithTag("number:Model").editableText(), "the clicked building is selected")

        tap(onNodeWithTag("browse-models"))
        onNodeWithTag("model-browser").performScrollToNode(androidx.compose.ui.test.hasTestTag("model:30"))
        onNodeWithTag("model:30").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(30L, buildings(project)[building].modelId, "choosing a model in the browser changes the building")
        assertEquals("30", onNodeWithTag("number:Model").editableText())
    }
}
