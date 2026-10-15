package io.github.romulofer.foxhg.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsages
import io.github.romulofer.foxhg.core.data.personal.PersonalTable
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.script.ScriptEntry
import io.github.romulofer.foxhg.core.script.ScriptText
import io.github.romulofer.foxhg.core.text.TextArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end tests of the real window content: they open the ROM, click through the screens, type into the fields and
 * check what ended up in the project and in a rebuilt ROM. Needs roms/heartgold.nds (or FOXHG_ROM); each test passes
 * with a notice when it is absent. Run them under a display (`xvfb-run`), see CLAUDE.md.
 */
@OptIn(ExperimentalTestApi::class)
class UiEndToEndTest {
    private fun withApp(block: DesktopComposeUiTest.(AppModel, RomProject) -> Unit) = withHeartGoldApp(block = block)

    @Test
    fun everyScreenOpensWithRealDataFromTheRom() = withApp { _, _ ->
        val screens = listOf(
            "Personal data" to "BULBASAUR",
            "Learnsets" to "BULBASAUR",
            "Evolutions" to "BULBASAUR",
            "Egg moves" to "BULBASAUR",
            "TMs and HMs" to "Focus Punch",
            "Moves" to "Pound",
            "Items" to "Master Ball",
            "Trainer classes" to "Youngster",
            "Maps" to "New Bark Town",
            "Sounds" to "SEQ_PV001",
        )
        for ((screen, expected) in screens) {
            open(screen)
            onAllNodesWithText(expected, substring = true).onFirst().assertIsDisplayed()
        }
        for (screen in listOf("Trainers", "Wild encounters", "Map headers", "Matrices", "Events", "Scripts", "Text archives")) {
            open(screen)
            onNodeWithTag("nav:$screen").assertIsDisplayed()
        }
    }

    @Test
    fun editingAnItemInItsScreenIsRecordedInTheProjectAndTheBuiltRom() = withApp { model, project ->
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("321")
        waitForIdle()
        assertTrue(project.isDirty, "typing a price edits the project")
        onNodeWithText("1 file changed").assertIsDisplayed()
        assertEquals(321, (ItemData.load(project) as FoxResult.Ok).value.records[4].price)
        buildAndReopen(model) { rebuilt ->
            assertEquals(321, (ItemData.load(rebuilt) as FoxResult.Ok).value.records[4].price)
        }
    }

    @Test
    fun aValueOutsideTheAllowedRangeIsNotWritten() = withApp { _, project ->
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("99999")
        waitForIdle()
        assertTrue(!project.isDirty, "65536 and more does not fit in a price, so nothing is written")
    }

    @Test
    fun searchingForASpeciesAndEditingItsStats() = withApp { _, project ->
        open("Personal data")
        onNodeWithTag("search:Search species…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU")
        onNodeWithTag("number:stat:HP").performTextReplacement("77")
        waitForIdle()
        onNodeWithText("1 file changed").assertIsDisplayed()
        assertEquals(77, (PersonalTable.load(project) as FoxResult.Ok).value.records[25].baseHp)
    }

    @Test
    fun findingAMessageThroughTheBankSearchAndEditingIt() = withApp { model, project ->
        open("Text archives")
        onNodeWithTag("search:Search banks and messages…").performTextInput("great ball")
        waitForIdle()
        clickFirstText("Item names")
        clickFirstText("Great Ball")
        assertTrue(onNodeWithTag("textarea").editableText().contains("Great Ball"))
        onNodeWithTag("textarea").performTextReplacement("Mega Ball")
        waitForIdle()
        assertEquals("Mega Ball", (TextArchive.load(project) as FoxResult.Ok).value.records[222].entries[3].text())
        assertEquals("003  Mega Ball", RecordEditorModel.itemLabel(project.names)(3), "other screens see the new name")
        buildAndReopen(model) { rebuilt ->
            assertEquals("Mega Ball", (TextArchive.load(rebuilt) as FoxResult.Ok).value.records[222].entries[3].text())
        }
    }

    @Test
    fun editingAScriptAsTextAndRejectingAMistake() = withApp { model, project ->
        open("Scripts")
        // script files 6 to 10 all belong to Mt. Moon; file 7 is a plain script of one map (the list rows come before the header)
        onAllNodesWithText("Mt. Moon")[1].performClick()
        waitForIdle()
        val original = onNodeWithTag("textarea").editableText()
        assertTrue(original.startsWith("script_1:"), original.take(60))

        onNodeWithTag("textarea").performTextReplacement(original + "\n    NotACommand 1\n")
        waitForIdle()
        onAllNodesWithText("Not applied", substring = true).onFirst().assertIsDisplayed()
        assertTrue(!project.isDirty, "a script that does not assemble is not written")

        onNodeWithTag("textarea").performTextReplacement(original.replaceFirst("\n    End\n", "\n    SetFlag 999\n    End\n"))
        waitForIdle()
        assertTrue(project.isDirty)
        val index = 7
        val written = (ScriptEntry.load(project) as FoxResult.Ok).value.records[index] as ScriptEntry.Script
        assertTrue(ScriptText.toText(written.file).contains("SetFlag 999"))
        buildAndReopen(model) { rebuilt ->
            val back = (ScriptEntry.load(rebuilt) as FoxResult.Ok).value.records[index] as ScriptEntry.Script
            assertTrue(ScriptText.toText(back.file).contains("SetFlag 999"))
        }
    }

    @Test
    fun paintingATileAndAddingAWarpOnTheMap() = withApp { model, project ->
        open("Maps")
        clickFirstText("New Bark Town")
        val mapBefore = (MapFile.load(project) as FoxResult.Ok).value.records[0]

        onNodeWithTag("chip:Paint collision").performClick()
        onNodeWithTag("chip:Block").performClick()
        onNodeWithTag("map-canvas").performMouseInput { click(Offset(16 * 18f + 9f, 16 * 18f + 9f)) } // the middle of tile (16, 16): 18 px per tile
        waitForIdle()
        val mapAfter = (MapFile.load(project) as FoxResult.Ok).value.records[0]
        assertEquals(0x80, mapAfter.collision(16, 16), "the clicked tile changed")
        assertEquals(mapBefore.collision(15, 15), mapAfter.collision(15, 15), "only the clicked tile changed")

        val usage = MapUsages(project).of(0).first()
        val eventFile = MapUsages(project).headers[usage.header].eventFile
        val warpsBefore = (EventFile.load(project) as FoxResult.Ok).value.records[eventFile].warps.size
        onNodeWithTag("chip:Edit events").performClick()
        waitForIdle()
        onNodeWithText("Add warp").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(warpsBefore + 1, (EventFile.load(project) as FoxResult.Ok).value.records[eventFile].warps.size)
        onNodeWithText("Duplicate").assertExists() // the selected event's buttons are below the fold

        buildAndReopen(model) { rebuilt ->
            assertEquals(0x80, (MapFile.load(rebuilt) as FoxResult.Ok).value.records[0].collision(16, 16))
            assertEquals(warpsBefore + 1, (EventFile.load(rebuilt) as FoxResult.Ok).value.records[eventFile].warps.size)
        }
    }

    @Test
    fun theTileTypePickerShowsAndChangesTheBrush() = withApp { _, _ ->
        open("Maps")
        clickFirstText("New Bark Town")
        onNodeWithTag("picker:Type").performClick()
        waitForIdle()
        // the popup is a second root of the semantics tree; its rows read "6  Headbutt" exactly
        onAllNodesWithText("6  Headbutt").onFirst().performClick()
        waitForIdle()
        onAllNodesWithText("6  Headbutt").onFirst().assertIsDisplayed()
    }

    @Test
    fun playingASoundNeverCrashesWhateverTheAudioDevice() = withApp { _, _ ->
        open("Sounds")
        onNodeWithTag("play:seq1").performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithText("▶").fetchSemanticsNodes().isNotEmpty() || onAllNodesWithText("Cannot play", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("play:seq1").assertIsDisplayed()
    }

    @Test
    fun theSaveIndicatorAndStatusBarFollowEdits() = withApp { _, project ->
        open("Items")
        onNodeWithText("No changes").assertIsDisplayed()
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("5")
        waitForIdle()
        assertTrue(project.isDirty)
        onNodeWithText("1 file changed").assertIsDisplayed()
        onAllNodesWithText("Not saved", substring = true).onFirst().assertIsDisplayed()
    }
}
