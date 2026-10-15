package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.encounter.EncounterData
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.hiddenitem.HiddenItems
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashItems
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashSite
import io.github.romulofer.foxhg.core.data.starter.Starters
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.data.matrix.GameMatrix
import io.github.romulofer.foxhg.core.data.trainer.TrainerTable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Widget-level end-to-end tests of the trainer, wild encounter, map header and matrix screens: type into the fields,
 * click the buttons, then check the project and a rebuilt ROM. Same setup and skip rule as [UiEndToEndTest].
 */
@OptIn(ExperimentalTestApi::class)
class ListEditorsUiEndToEndTest {
    @Test
    fun replacingAPokemonAndEditingAPartyOnTheTrainersScreen() = withHeartGoldApp { model, project ->
        val names = project.names!!
        val trainers = { (TrainerTable.load(project) as FoxResult.Ok).value.trainers }
        val falkner = names.trainerNames.indexOfFirst { it.equals("Falkner", ignoreCase = true) }
        val pidgey = names.species.indexOf("PIDGEY")
        val rattata = names.species.indexOf("RATTATA")
        val before = trainers()[falkner].party
        val users = trainers().sumOf { t -> t.party.count { it.species == pidgey } }
        assertTrue(users > 0)

        open("Trainers")
        // the card starts on Pidgey -> Rattata
        tap(onNodeWithTag("replace-toggle"), inScrollPane = false)
        tap(onNodeWithTag("replace-open"), inScrollPane = false)
        onNodeWithText("${plural(users, "Pokémon")} in trainer parties will become RATTATA. Levels and moves stay. Ctrl+Z undoes it.").assertIsDisplayed()
        tap(onNodeWithTag("confirm"), inScrollPane = false)
        onNodeWithText("Replaced $users Pokémon").assertIsDisplayed()
        assertTrue(trainers().none { t -> t.party.any { it.species == pidgey } })

        onNodeWithTag("search:Name, class or Pokémon…").performTextInput("falkner")
        waitForIdle()
        clickFirstText("Falkner", substring = true)
        onAllNodesWithTag("number:Level")[1].typeNumber(40)
        waitForIdle()
        assertEquals(40, trainers()[falkner].party[1].level)

        tap(onNodeWithText("Add Pokémon"))
        assertEquals(before.size + 1, trainers()[falkner].party.size)
        tap(onAllNodesWithText("Remove")[before.size])
        assertEquals(before.size, trainers()[falkner].party.size)

        buildAndReopen(model) { rebuilt ->
            val back = (TrainerTable.load(rebuilt) as FoxResult.Ok).value.trainers[falkner].party
            assertEquals(listOf(rattata, before[1].species), back.map { it.species })
            assertEquals(listOf(before[0].level, 40), back.map { it.level })
        }
    }

    @Test
    fun editingAnAreaAndCopyingSoulSilverOnTheWildEncountersScreen() = withHeartGoldApp { model, project ->
        val route30 = EncounterEditorModel(project).let { m -> m.editor.rows().first { m.editor.label(it).contains("Route 30") } }
        open("Wild encounters")
        clickFirstText("Route 30", substring = true)
        onNodeWithTag("number:Walking").typeNumber(77)
        waitForIdle()
        assertEquals(77, (EncounterData.loadHeartGold(project) as FoxResult.Ok).value.records[route30].rateWalking)
        onNodeWithTag("number:Walking").typeNumber(999)
        waitForIdle()
        assertEquals(77, (EncounterData.loadHeartGold(project) as FoxResult.Ok).value.records[route30].rateWalking, "999 does not fit a rate, so nothing is written")

        tap(onNodeWithTag("copy-all-open"), inScrollPane = false)
        tap(onNodeWithTag("confirm"), inScrollPane = false)
        onAllNodesWithText("Copied into", substring = true).onFirst().assertIsDisplayed()
        onNodeWithText("Every area matches SoulSilver.").assertIsDisplayed()

        buildAndReopen(model) { rebuilt ->
            assertContentEquals(rebuilt.read("a/1/3/6"), rebuilt.read("a/0/3/7"), "HeartGold now equals the SoulSilver table")
        }
    }

    @Test
    fun editingRemovingAndAddingBackAHiddenItemOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (HiddenItems.load(project) as FoxResult.Ok).value }
        assertEquals(231, table().entries.size)
        open("Hidden items")
        onNodeWithTag("number:Quantity").typeNumber(3)
        waitForIdle()
        assertEquals(3, table().entries[0].quantity, "the quantity went into the ARM9")
        onNodeWithTag("number:Index").typeNumber(1)
        waitForIdle()
        assertEquals(0, table().entries[0].index, "index 1 belongs to the second spot, so it is refused")

        onNodeWithTag("hidden-add").assertIsNotEnabled()
        tap(onNodeWithTag("hidden-remove"), inScrollPane = false)
        waitForIdle()
        assertEquals(230, table().entries.size)
        onNodeWithTag("hidden-add").assertIsEnabled()
        tap(onNodeWithTag("hidden-add"), inScrollPane = false)
        waitForIdle()
        assertEquals(231, table().entries.size)
        assertEquals(0, table().entries.last().index, "the new spot takes the smallest free index (the removed one)")
        onNodeWithTag("hidden-add").assertIsNotEnabled()

        buildAndReopen(model) { rebuilt ->
            val reread = (HiddenItems.load(rebuilt) as FoxResult.Ok).value
            assertEquals(231, reread.entries.size)
            assertEquals(1, reread.entries[0].index, "the first spot was removed, so the old second one leads")
            assertEquals(0, reread.entries.last().index, "and the spot added back is last")
        }
    }

    @Test
    fun changingTheStartersOnTheirScreenUpdatesWhatFollowsAndRestoresCleanly() = withHeartGoldApp { model, project ->
        open("Starters")
        pick("Ball 1", "BULBASAUR")
        waitForIdle()
        assertEquals(listOf(1, 155, 158), Starters.load(project).let { (it as FoxResult.Ok).value.species })
        onNodeWithText("Updated the starters, the chooser cries, the rival's counter-pick in 7 scripts and the professor's text").assertIsDisplayed()
        onNodeWithTag("number:Level").typeNumber(9)
        waitForIdle()
        assertEquals(9, (Starters.load(project) as FoxResult.Ok).value.level)

        buildAndReopen(model) { rebuilt ->
            val reread = (Starters.load(rebuilt) as FoxResult.Ok).value
            assertEquals(Starters(listOf(1, 155, 158), 0, 9), reread, "the change survives a build")
        }

        tap(onNodeWithTag("starter-restore"), inScrollPane = false)
        onNodeWithTag("number:Level").typeNumber(5)
        waitForIdle()
        assertEquals(Starters.VANILLA, (Starters.load(project) as FoxResult.Ok).value)
        assertFalse(project.isDirty, "back to the original three, level 5: nothing is edited")
    }

    @Test
    fun editingRockSmashOddsTableAndItemsOnItsScreen() = withHeartGoldApp { model, project ->
        val route29 = EventLabels(project).let { labels -> (0 until 540).first { labels.mapName(it) == "Route 29" } }
        val sites = { (NarcRecords.load(project, RockSmashSite.SPEC) as FoxResult.Ok).value.records }
        val before = sites()[route29]
        open("Rock Smash")
        onNodeWithTag("search:Search…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)
        onNodeWithTag("number:Odds").typeNumber(41)
        waitForIdle()
        assertEquals(before.copy(odds = 41), sites()[route29], "the drop chance went into the map's file")
        onNodeWithTag("number:Odds").typeNumber(101)
        waitForIdle()
        assertEquals(41, sites()[route29].odds, "101 is not a chance, so nothing is written")

        pick("Item table", "Cliff Cave")
        assertEquals(RockSmashSite(41, RockSmashSite.CLIFF_CAVE), sites()[route29])

        pick("Slot 1 (25%)", "Revive", index = 0)
        assertEquals(project.names!!.items.indexOf("Revive"), (RockSmashItems.load(project) as FoxResult.Ok).value.tables[RockSmashSite.DEFAULT][0])

        buildAndReopen(model) { rebuilt ->
            assertEquals(RockSmashSite(41, RockSmashSite.CLIFF_CAVE), (NarcRecords.load(rebuilt, RockSmashSite.SPEC) as FoxResult.Ok).value.records[route29])
            assertEquals(project.names!!.items.indexOf("Revive"), (RockSmashItems.load(rebuilt) as FoxResult.Ok).value.tables[RockSmashSite.DEFAULT][0])
        }
    }

    @Test
    fun editingAMapHeaderOnTheMapHeadersScreen() = withHeartGoldApp { model, project ->
        val route29 = MapHeaderEditorModel(project).let { m -> m.search = "route 29"; m.rows().first() }
        val headers = { (MapHeaderTable.load(project) as FoxResult.Ok).value }
        val before = headers()[route29]
        assertTrue(before.permissions and (1 shl MapHeader.Permission.BICYCLE.bit) != 0, "bicycle starts allowed")

        open("Map headers")
        onNodeWithTag("search:Search maps…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)
        onNodeWithTag("number:Weather").typeNumber(7)
        onNodeWithTag("number:Day music").typeNumber(1234)
        tap(onNodeWithTag("chip:Bicycle"))
        waitForIdle()

        val edited = headers()[route29]
        assertEquals(7, edited.weather)
        assertEquals(1234, edited.musicDay)
        assertEquals(0, edited.permissions and (1 shl MapHeader.Permission.BICYCLE.bit), "the chip switched bicycle off")
        assertEquals(before.eventFile, edited.eventFile, "untouched fields stay")
        assertTrue(model.hasUnsavedWork)

        onNodeWithTag("number:Weather").typeNumber(200)
        waitForIdle()
        assertEquals(7, headers()[route29].weather, "200 is above the weather range, so nothing is written")

        buildAndReopen(model) { rebuilt ->
            val h = (MapHeaderTable.load(rebuilt) as FoxResult.Ok).value[route29]
            assertEquals(listOf(7, 1234, before.locationName), listOf(h.weather, h.musicDay, h.locationName))
            assertFalse(h.permissions and 1 != 0)
        }
    }

    @Test
    fun editingACellAndResizingOnTheMatricesScreen() = withHeartGoldApp { model, project ->
        val matrices = { (GameMatrix.load(project) as FoxResult.Ok).value.records }
        val before = matrices()[5]

        open("Matrices")
        clickFirstText("005")
        onNodeWithTag("number:Map file").typeNumber(1234)
        waitForIdle()
        assertEquals(1234, matrices()[5].map(0, 0))

        onNodeWithTag("number:Width").typeNumber(before.width + 1)
        waitForIdle()
        assertEquals(before.width, matrices()[5].width, "typing a width alone does not resize")
        tap(onNodeWithText("Resize"))
        assertEquals(before.width + 1, matrices()[5].width)
        assertEquals(GameMatrix.EMPTY, matrices()[5].map(before.width, 0), "the new column starts empty")
        assertEquals(1234, matrices()[5].map(0, 0), "resizing keeps the edited cell")

        buildAndReopen(model) { rebuilt ->
            val m = (GameMatrix.load(rebuilt) as FoxResult.Ok).value.records
            assertEquals(1234, m[5].map(0, 0))
            assertEquals(before.width + 1, m[5].width)
            assertEquals(matrices()[0].width, m[0].width, "other matrices are untouched")
        }
    }
}
