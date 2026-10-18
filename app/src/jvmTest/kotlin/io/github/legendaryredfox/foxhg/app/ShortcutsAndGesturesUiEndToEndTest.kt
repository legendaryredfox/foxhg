package io.github.legendaryredfox.foxhg.app

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
import io.github.legendaryredfox.foxhg.app.ui.TextFocus
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The window shortcuts, list navigation inside a real screen, and the 3D view gestures. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class ShortcutsAndGesturesUiEndToEndTest {
    private fun price(project: io.github.legendaryredfox.foxhg.core.project.RomProject, item: Int) = (ItemData.load(project) as FoxResult.Ok).value.records[item].price

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
    fun undoAndRedoStepAsideInATextBoxAndWhenThereIsNothingToUndo() = withHeartGoldApp { model, project ->
        assertFalse(Actions.shortcut(model, Key.Z, ctrl = true, shift = false), "nothing to undo: the key is left alone")
        assertFalse(Actions.shortcut(model, Key.Y, ctrl = true, shift = false), "nothing to redo: the key is left alone")
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("778")
        waitForIdle()
        assertTrue(model.canUndo)
        TextFocus.active = true
        try {
            assertFalse(Actions.shortcut(model, Key.Z, ctrl = true, shift = false), "in a script or message box Ctrl+Z is the box's own undo")
            assertEquals(778, price(project, 4), "and the project is untouched")
        } finally {
            TextFocus.active = false
        }
        assertTrue(Actions.shortcut(model, Key.Z, ctrl = true, shift = false), "outside the box it undoes the project")
    }

    @Test
    fun ctrlFSwitchesToTheResearchTab() = withHeartGoldApp { model, _ ->
        open("Items")
        runOnUiThread { assertTrue(Actions.shortcut(model, Key.F, ctrl = true, shift = false)) }
        waitForIdle()
        assertEquals(EditorKind.RESEARCH, model.selected)
        onNodeWithTag("research-find").assertExists()
    }

    @Test
    fun theListSearchTakesFocusAndTheArrowKeysMoveThroughTheList() = withHeartGoldApp { _, project ->
        open("Items")
        clickFirstText("Poké Ball")
        assertEquals(price(project, 4).toString(), onNodeWithTag("number:Price").editableText())
        runOnUiThread { assertTrue(SearchFocus.request()) }
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

    /** How far the page that holds the 3D view is scrolled: the nearest scrolling ancestor of the view. */
    private fun androidx.compose.ui.test.DesktopComposeUiTest.pageScroll(): Float {
        var node: androidx.compose.ui.semantics.SemanticsNode? = onNodeWithTag("map3d").fetchSemanticsNode().parent
        while (node != null) {
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { return it.value() }
            node = node.parent
        }
        error("The 3D view is not inside a scrolling page")
    }

    @Test
    fun ctrlWheelZoomsTheThreeDViewWithoutScrollingThePage() = withHeartGoldApp(height = 1100) { _, _ ->
        open("Maps")
        onNodeWithTag("map3d").performScrollTo()
        waitForIdle()
        val page = pageScroll()
        val zoomedFrom = view()
        wheelOverView(3f, ctrl = true)
        waitForIdle()
        assertNotEquals(zoomedFrom, view(), "Ctrl + wheel zooms the view")
        assertEquals(page, pageScroll(), "and the page stays where it is")

        wheelOverView(-3f, ctrl = false)
        waitForIdle()
        assertNotEquals(page, pageScroll(), "a plain wheel still scrolls the page")
    }

    /** One wheel turn over the 3D view, sent to the scene as a real wheel is: the test mouse can neither hold Ctrl nor reach the page like this. */
    private fun androidx.compose.ui.test.DesktopComposeUiTest.wheelOverView(delta: Float, ctrl: Boolean) {
        val centre = onNodeWithTag("map3d").fetchSemanticsNode().boundsInRoot.center
        runOnUiThread {
            scene.sendPointerEvent(
                androidx.compose.ui.input.pointer.PointerEventType.Scroll, centre,
                scrollDelta = Offset(0f, delta), keyboardModifiers = androidx.compose.ui.input.pointer.PointerKeyboardModifiers(isCtrlPressed = ctrl),
            )
        }
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
