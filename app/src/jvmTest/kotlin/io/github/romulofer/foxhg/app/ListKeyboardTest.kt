package io.github.romulofer.foxhg.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ListKeyboardTest {
    @Test
    fun stepSelectionMovesAndClamps() {
        val rows = (0..24).toList()
        assertEquals(6, stepSelection(rows, 5, Key.DirectionDown))
        assertEquals(4, stepSelection(rows, 5, Key.DirectionUp))
        assertEquals(0, stepSelection(rows, 0, Key.DirectionUp))
        assertEquals(24, stepSelection(rows, 24, Key.DirectionDown))
        assertEquals(15, stepSelection(rows, 5, Key.PageDown))
        assertEquals(0, stepSelection(rows, 5, Key.PageUp))
        assertEquals(30, stepSelection(listOf(7, 30, 31), 7, Key.DirectionDown))
        assertEquals(0, stepSelection(listOf(0, 1), -1, Key.DirectionDown))
        assertNull(stepSelection(emptyList(), 0, Key.DirectionDown))
        assertNull(stepSelection(rows, 5, Key.A))
    }

    @Test
    fun arrowKeysMoveTheSelectionAndCtrlFFocusesSearch() = runDesktopComposeUiTest(800, 600) {
        var selected by mutableIntStateOf(2)
        setContent {
            var search by remember { mutableStateOf("") }
            ListPane(
                rows = (0..30).toList(),
                selected = selected,
                search = search,
                onSearch = { search = it },
                placeholder = "Find",
                label = { "$it  Row $it" },
                onSelect = { selected = it },
                isEdited = { false },
            )
        }
        assertTrue(SearchFocus.request())
        waitForIdle()
        onNodeWithTag("search:Find").assertIsFocused()
        onNodeWithTag("search:Find").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(3, selected)
        onNodeWithTag("search:Find").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(1, selected)
        onNodeWithTag("search:Find").performKeyInput { pressKey(Key.PageDown) }
        waitForIdle()
        assertEquals(11, selected)
    }
}
