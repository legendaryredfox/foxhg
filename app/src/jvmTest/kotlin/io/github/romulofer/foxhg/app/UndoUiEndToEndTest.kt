package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.item.ItemData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Undo and redo through the app: the menu, the model, and the open editor following the project. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class UndoUiEndToEndTest {
    private fun price(project: io.github.romulofer.foxhg.core.project.RomProject) = (ItemData.load(project) as FoxResult.Ok).value.records[4].price

    @Test
    fun undoAndRedoFromTheMenuUpdateTheOpenEditor() = withHeartGoldApp { model, project ->
        val original = price(project)
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("321")
        waitForIdle()
        assertEquals(321, price(project))
        assertTrue(model.canUndo)

        onNodeWithText("Project  ▾").performClick()
        waitForIdle()
        onNodeWithText("Undo", substring = true).performClick()
        waitForIdle()
        assertEquals(original, price(project))
        assertFalse(project.isDirty)
        assertEquals(original.toString(), onNodeWithTag("number:Price").editableText(), "the editor shows the restored value, still on the same item")
        onNodeWithText("No changes").assertIsDisplayed()
        assertTrue(model.canRedo)

        model.redo()
        waitLoaded()
        assertEquals(321, price(project))
        assertEquals("321", onNodeWithTag("number:Price").editableText())
        assertFalse(model.canRedo)
    }

    @Test
    fun undoKeepsTheSelectedPokemonAndRestoresItsStats() = withHeartGoldApp { model, project ->
        val hp = { (io.github.romulofer.foxhg.core.data.personal.PersonalTable.load(project) as FoxResult.Ok).value.records[25].baseHp }
        val original = hp()
        open("Personal data")
        onNodeWithTag("search:Search species…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU")
        onNodeWithTag("number:stat:HP").performTextReplacement("99")
        waitForIdle()
        assertEquals(99, hp())
        model.undo()
        waitLoaded()
        assertEquals(original, hp())
        assertEquals(original.toString(), onNodeWithTag("number:stat:HP").editableText(), "still on Pikachu, with its old HP")
        assertEquals(25, model.selectionMemory[EditorKind.PERSONAL])
    }

    @Test
    fun undoWorksForTextArchivesAndTheMapHeadersInTheArm9() = withHeartGoldApp { model, project ->
        val text = { (io.github.romulofer.foxhg.core.text.TextArchive.load(project) as FoxResult.Ok).value.records[222].entries[3].text() }
        val original = text()
        open("Text archives")
        onNodeWithTag("search:Search banks and messages…").performTextInput("great ball")
        waitForIdle()
        clickFirstText("Item names")
        clickFirstText("Great Ball")
        onNodeWithTag("textarea").performTextReplacement("Mega Ball")
        waitForIdle()
        assertEquals("Mega Ball", text())
        model.undo()
        waitLoaded()
        assertEquals(original, text(), "the message is back")
        assertEquals(original, onNodeWithTag("textarea").editableText(), "the editor shows it, still on the same message")
        assertEquals(222 * 100000 + 3, model.selectionMemory[EditorKind.TEXT])

        open("Map headers")
        onNodeWithTag("search:Search maps…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)
        onNodeWithTag("number:Weather").performTextReplacement("7")
        waitForIdle()
        assertTrue(project.editedArm9 != null, "headers live in the ARM9")
        model.undo()
        waitLoaded()
        assertEquals(null, project.editedArm9, "undo takes the ARM9 edit away")
        assertTrue(model.selectionMemory[EditorKind.HEADERS]!! > 2, "the header stays selected")
    }
}
