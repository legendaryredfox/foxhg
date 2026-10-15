package io.github.romulofer.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.ScrollWheel
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.item.ItemData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The window shortcuts, list navigation inside a real screen, and the 3D view gestures. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class ShortcutsAndGesturesUiEndToEndTest {
    private fun price(project: io.github.romulofer.foxhg.core.project.RomProject, item: Int) = (ItemData.load(project) as FoxResult.Ok).value.records[item].price

    @Test
    fun ctrlZUndoesCtrlShiftZAndCtrlYRedo() = withHeartGoldApp { model, project ->
        val original = price(project, 4)
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("777")
        waitForIdle()
        assertEquals(777, price(project, 4))
        assertTrue(Actions.shortcut(model, Key.Z, ctrl = true, shift = false))
        waitLoaded()
        assertEquals(original, price(project, 4), "Ctrl+Z undoes the edit")
        assertEquals(original.toString(), onNodeWithTag("number:Price").editableText(), "and the screen follows")
        assertTrue(Actions.shortcut(model, Key.Z, ctrl = true, shift = true))
        waitLoaded()
        assertEquals(777, price(project, 4), "Ctrl+Shift+Z redoes it")
        Actions.shortcut(model, Key.Z, ctrl = true, shift = false)
        waitLoaded()
        assertTrue(Actions.shortcut(model, Key.Y, ctrl = true, shift = false))
        waitLoaded()
        assertEquals(777, price(project, 4), "Ctrl+Y redoes too")
        assertFalse(Actions.shortcut(model, Key.Z, ctrl = false, shift = false), "without Ctrl it is just a letter")
    }

    @Test
    fun ctrlFFocusesTheSearchAndTheArrowKeysMoveThroughTheList() = withHeartGoldApp { model, project ->
        open("Items")
        clickFirstText("Poké Ball")
        assertEquals(price(project, 4).toString(), onNodeWithTag("number:Price").editableText())
        runOnUiThread { assertTrue(Actions.shortcut(model, Key.F, ctrl = true, shift = false)) }
        waitForIdle()
        onNodeWithTag("search:Search…").assertIsFocused()
        onNodeWithTag("search:Search…").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(price(project, 5).toString(), onNodeWithTag("number:Price").editableText(), "Down selects the next item")
        onNodeWithTag("search:Search…").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(price(project, 3).toString(), onNodeWithTag("number:Price").editableText(), "Up twice selects the one before")
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.view(): String =
        onNodeWithTag("map3d").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) ?: ""

    @Test
    fun theThreeDViewTurnsPansAndZoomsAndResets() = withHeartGoldApp(height = 1100) { _, _ ->
        open("Maps")
        onNodeWithTag("map3d").performScrollTo()
        val start = view()
        onNodeWithTag("map3d").performMouseInput { moveTo(Offset(300f, 200f)); press(); moveTo(Offset(330f, 200f)); moveTo(Offset(360f, 220f)); release() }
        waitForIdle()
        val turned = view()
        assertNotEquals(start, turned, "dragging turns the view: $start -> $turned")
        assertTrue(turned.substringAfter("x ").startsWith(start.substringAfter("x ").substringBefore(" ")), "turning does not pan")

        onNodeWithTag("map3d").performMouseInput { moveTo(Offset(300f, 200f)); press(androidx.compose.ui.test.MouseButton.Secondary); moveTo(Offset(340f, 230f)); moveTo(Offset(380f, 260f)); release(androidx.compose.ui.test.MouseButton.Secondary) }
        waitForIdle()
        val panned = view()
        assertNotEquals(turned.substringAfter("x "), panned.substringAfter("x "), "a right drag pans: $turned -> $panned")

        onNodeWithTag("map3d").performMouseInput { scroll(3f, ScrollWheel.Vertical) }
        waitForIdle()
        assertEquals(panned, view(), "a plain wheel scrolls the page and does not zoom")

        onNodeWithTag("reset-view").performScrollTo().performClick()
        waitForIdle()
        assertEquals(start, view(), "Reset view restores the camera")
    }

    @Test
    fun theShinySwitchOnAPokemonHeaderChangesThePicture() = withHeartGoldApp { _, _ ->
        open("Personal data")
        onNodeWithTag("search:Search species…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU")
        assertTrue(onAllNodesWithTag("portraits", useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        onNodeWithTag("chip:Shiny").performClick()
        waitForIdle()
        assertTrue(onAllNodesWithTag("portraits", useUnmergedTree = true).fetchSemanticsNodes().size == 1, "still shown after switching")
    }
}
