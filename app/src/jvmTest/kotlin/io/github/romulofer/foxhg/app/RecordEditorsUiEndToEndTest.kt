package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.evolution.EvolutionData
import io.github.romulofer.foxhg.core.data.evolution.EvolutionMethod
import io.github.romulofer.foxhg.core.data.learnset.Learnset
import io.github.romulofer.foxhg.core.data.learnset.LearnsetEntry
import io.github.romulofer.foxhg.core.data.move.MoveData
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
import io.github.romulofer.foxhg.core.data.trade.NpcTrade
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassPrizes
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Widget-level end-to-end tests of the record editors that had only model-level coverage: learnsets, evolutions, egg
 * moves, TMs and HMs, moves (with CSV), trainer classes and the event files list, plus the sound browser.
 * Same setup and ROM rule as [UiEndToEndTest].
 */
@OptIn(ExperimentalTestApi::class)
class RecordEditorsUiEndToEndTest {
    @Test
    fun learnsetEditsOnTheLearnsetsScreen() = withHeartGoldApp { model, project ->
        val pikachu = 25
        val learnset = { (Learnset.load(project) as FoxResult.Ok).value.records[pikachu] }
        val before = learnset()
        val splash = project.names!!.moves.indexOf("Splash")

        open("Learnsets")
        onNodeWithTag("search:Search…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU")

        tap(onNodeWithText("Add move"))
        assertEquals(before.entries.size + 1, learnset().entries.size)
        onAllNodesWithTag("number:Level").onLast().typeNumber(1)
        waitForIdle()
        onAllNodesWithTag("number:Level").onFirst().typeNumber(150)
        waitForIdle()
        assertEquals(before.entries.first().level, learnset().entries.first().level, "150 is not a level, so nothing is written")

        tap(onNodeWithText("Sort by level"))
        val levels = learnset().entries.map { it.level }
        assertEquals(levels.sorted(), levels, "sorted by level")
        val added = learnset().entries.indexOfLast { it == LearnsetEntry(move = 1, level = 1) }
        assertTrue(added >= 0, "the new entry (Pound at level 1) is in the list")
        pick("Move", "Splash", index = added)
        assertEquals(LearnsetEntry(splash, 1), learnset().entries[added])

        buildAndReopen(model) { rebuilt ->
            assertEquals(learnset().entries, (Learnset.load(rebuilt) as FoxResult.Ok).value.records[pikachu].entries)
        }

        tap(onAllNodesWithText("Remove")[added])
        assertEquals(before.sorted().entries, learnset().entries, "removing the added move gives the original list back")
    }

    @Test
    fun evolutionEditsAndTradeConversionOnTheEvolutionsScreen() = withHeartGoldApp { model, project ->
        val names = project.names!!
        val records = { (EvolutionData.load(project) as FoxResult.Ok).value.records }
        val before = records()
        val trades = before.sumOf { r -> r.slots.count { it.known == EvolutionMethod.TRADE || it.known == EvolutionMethod.TRADE_ITEM } }
        assertTrue(trades > 0)
        val kadabra = names.species.indexOf("KADABRA")
        val kadabraSlot = before[kadabra].slots.indexOfFirst { it.known == EvolutionMethod.TRADE }
        assertTrue(kadabraSlot >= 0)

        open("Evolutions")
        onNodeWithTag("number:Trade becomes level").typeNumber(30, inScrollPane = false)
        waitForIdle()
        tap(onNodeWithText("Convert all"), inScrollPane = false)
        onNodeWithText("Converted ${plural(trades, "evolution")}").assertIsDisplayed()
        onNodeWithText("No trade evolutions left.", substring = true).assertIsDisplayed()
        assertEquals(EvolutionMethod.LEVEL, records()[kadabra].slots[kadabraSlot].known)
        assertEquals(30, records()[kadabra].slots[kadabraSlot].param)

        onNodeWithTag("search:Search…").performTextInput("bulbasaur")
        waitForIdle()
        clickFirstText("BULBASAUR")
        onNodeWithTag("number:Level").typeNumber(20)
        waitForIdle()
        assertEquals(20, records()[1].slots[0].param)
        pick("Method 1", "Friendship")
        assertEquals(EvolutionMethod.FRIENDSHIP.id, records()[1].slots[0].method)
        assertEquals(before[1].slots[0].target, records()[1].slots[0].target, "the target stays")

        buildAndReopen(model) { rebuilt ->
            val back = (EvolutionData.load(rebuilt) as FoxResult.Ok).value.records
            assertEquals(records(), back)
        }
    }

    @Test
    fun eggMovesCanBeAddedChangedAndRemovedOnTheEggMovesScreen() = withHeartGoldApp { model, project ->
        val pikachu = 25
        val eggs = { (EggMoves.load(project) as FoxResult.Ok).value.records.single() }
        val moves = project.names!!.moves
        assertTrue(eggs().movesOf(pikachu).isEmpty(), "Pikachu has no egg moves in the vanilla ROM")

        open("Egg moves")
        onNodeWithTag("search:Search…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU")
        onNodeWithText("No egg moves.").assertIsDisplayed()

        tap(onNodeWithText("Add move"))
        assertEquals(listOf(1), eggs().movesOf(pikachu), "a new slot starts on move 1")
        pick("Move", "Volt Tackle")
        assertEquals(listOf(moves.indexOf("Volt Tackle")), eggs().movesOf(pikachu))
        tap(onNodeWithText("Add move"))
        assertEquals(2, eggs().movesOf(pikachu).size)
        tap(onAllNodesWithText("Remove")[1])
        assertEquals(listOf(moves.indexOf("Volt Tackle")), eggs().movesOf(pikachu))

        buildAndReopen(model) { rebuilt ->
            val back = (EggMoves.load(rebuilt) as FoxResult.Ok).value.records.single()
            assertEquals(listOf(moves.indexOf("Volt Tackle")), back.movesOf(pikachu))
        }
    }

    @Test
    fun tmAndHmMovesCanBeChangedOnTheTmHmScreen() = withHeartGoldApp { model, project ->
        val moves = project.names!!.moves
        val table = { (TmHmTable.load(project) as FoxResult.Ok).value }
        val before = table()

        open("TMs and HMs")
        pick("TM01", "Pound")
        pick("HM01", "Splash")
        assertEquals(moves.indexOf("Pound"), table().moves[0])
        assertEquals(moves.indexOf("Splash"), table().moves[TmHmTable.TM_COUNT])
        assertEquals(before.moves.drop(1).take(TmHmTable.TM_COUNT - 1), table().moves.drop(1).take(TmHmTable.TM_COUNT - 1), "the other TMs stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table(), (TmHmTable.load(rebuilt) as FoxResult.Ok).value)
        }
    }

    @Test
    fun moveStatsAndCsvOnTheMovesScreen() = withHeartGoldApp { model, project ->
        val names = project.names!!
        val pound = 1
        val move = { (MoveData.load(project) as FoxResult.Ok).value.records[pound] }
        val before = move()
        val dir = tempDir("foxhg-move-csv")
        try {
            open("Moves")
            clickFirstText("Pound")
            onNodeWithTag("number:Power").typeNumber(99)
            onNodeWithTag("number:Priority").typeNumber(-3)
            onNodeWithTag("number:Power").typeNumber(300)
            waitForIdle()
            assertEquals(99, move().power, "300 does not fit a byte, so the field keeps 99")
            assertEquals(-3, move().priority)
            val fire = names.types.indexOfFirst { it.equals("Fire", ignoreCase = true) }
            assertTrue(fire >= 0)
            pick("Type", names.types[fire])
            assertEquals(fire, move().type)
            pick("Category", "Special")
            assertEquals(1, move().category)
            assertEquals(before.pp, move().pp, "untouched fields stay")

            val exported = dir.resolve("moves.csv")
            withChoosers(file = { _, _, _ -> exported }) { tap(onNodeWithText("Export CSV")) }
            onNodeWithText("Exported to moves.csv").assertIsDisplayed()
            val lines = Files.readAllLines(exported)
            assertTrue(lines[0].startsWith("id,name,effect,category,power"), lines[0])
            assertEquals(listOf("1", "Pound", move().effect.toString(), "1", "99"), lines[2].split(",").take(5))

            val good = Files.writeString(dir.resolve("good.csv"), "id,power\r\n1,88\r\n")
            withChoosers(file = { _, _, _ -> good }) { tap(onNodeWithText("Import CSV")) }
            onNodeWithText("Imported good.csv").assertIsDisplayed()
            assertEquals(88, move().power)

            val bad = Files.writeString(dir.resolve("bad.csv"), "id,power\r\n1,999\r\n")
            withChoosers(file = { _, _, _ -> bad }) { tap(onNodeWithText("Import CSV")) }
            onAllNodesWithText("Import rejected", substring = true).onFirst().assertIsDisplayed()
            assertEquals(88, move().power, "a rejected file changes nothing")

            buildAndReopen(model) { rebuilt ->
                assertEquals(move(), (MoveData.load(rebuilt) as FoxResult.Ok).value.records[pound])
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun tradeEditsOnTheInGameTradesScreen() = withHeartGoldApp { model, project ->
        val names = project.names!!
        val trade = { (NpcTrade.load(project) as FoxResult.Ok).value.records[0] }
        val before = trade()
        assertEquals("ONIX", names.species[before.giveSpecies])

        open("In-game trades")
        clickFirstText("Rocky")
        onNodeWithTag("number:HP").typeNumber(31)
        onNodeWithTag("number:Speed").typeNumber(40)
        waitForIdle()
        assertEquals(31, trade().hpIv)
        assertEquals(before.speedIv, trade().speedIv, "40 is not an IV, so nothing is written")
        pick("NPC gives", "STEELIX")
        assertEquals(names.species.indexOf("STEELIX"), trade().giveSpecies)
        assertEquals(before.askSpecies, trade().askSpecies, "untouched fields stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(trade(), (NpcTrade.load(rebuilt) as FoxResult.Ok).value.records[0])
        }
    }

    @Test
    fun trainerClassGenderAndPrizeOnTheTrainerClassesScreen() = withHeartGoldApp { model, project ->
        val youngster = project.names!!.trainerClasses.indexOf("Youngster")
        val label = "%03d  Youngster".format(youngster)
        val gender = { (TrainerClassGenders.load(project) as FoxResult.Ok).value.genders[youngster] }
        val prize = { (TrainerClassPrizes.load(project) as FoxResult.Ok).value.entries.first { it.trainerClass == youngster }.multiplier }
        assertEquals(0, gender(), "Youngster is male")

        open("Trainer classes")
        onNodeWithTag("search:Search classes…").performTextInput("youngster")
        waitForIdle()
        pick(label, "Female")
        assertEquals(1, gender())
        onNodeWithTag("number:$label").typeNumber(9)
        waitForIdle()
        assertEquals(9, prize())

        buildAndReopen(model) { rebuilt ->
            assertEquals(1, (TrainerClassGenders.load(rebuilt) as FoxResult.Ok).value.genders[youngster])
            assertEquals(9, (TrainerClassPrizes.load(rebuilt) as FoxResult.Ok).value.entries.first { it.trainerClass == youngster }.multiplier)
        }
    }

    @Test
    fun eventsCanBeAddedEditedDuplicatedAndRemovedOnTheEventsScreen() = withHeartGoldApp { model, project ->
        val files = { (EventFile.load(project) as FoxResult.Ok).value.records }
        val labels = EventLabels(project)
        val route29 = files().indices.first { labels.label(it).contains("Route 29") }
        val before = files()[route29]

        open("Events")
        onNodeWithTag("search:Search…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)

        // the four "Add" buttons are overworld objects, warps, triggers, signs; the new row opens its form
        tap(onAllNodesWithText("Add")[1])
        assertEquals(before.warps.size + 1, files()[route29].warps.size)
        onNodeWithTag("number:Target header").typeNumber(5)
        waitForIdle()
        assertEquals(5, files()[route29].warps.last().header)

        val firstWarpRow = before.overworlds.size + before.warps.size
        tap(onAllNodesWithText("Duplicate")[firstWarpRow])
        assertEquals(before.warps.size + 2, files()[route29].warps.size)
        assertEquals(files()[route29].warps[before.warps.size], files()[route29].warps.last(), "the copy equals the original")
        tap(onAllNodesWithText("Remove")[firstWarpRow + 1])
        assertEquals(before.warps.size + 1, files()[route29].warps.size)
        assertEquals(before.overworlds, files()[route29].overworlds, "the other kinds stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(files()[route29], (EventFile.load(rebuilt) as FoxResult.Ok).value.records[route29])
        }
    }

    @Test
    fun theSoundBrowserSearchesAndDescribesSounds() = withHeartGoldApp { _, _ ->
        open("Sounds")
        onNodeWithTag("search:Search sounds…").performTextInput("zzzz-no-such-sound")
        waitForIdle()
        onNodeWithText("No match").assertIsDisplayed()
        onNodeWithTag("search:Search sounds…").performTextReplacement("SEQ_SE_")
        waitForIdle()
        onAllNodesWithText("SEQ_SE_", substring = true)[1].performClick() // [0] is the search box itself
        waitForIdle()
        onAllNodesWithText("Sound effect").onFirst().assertIsDisplayed()
        onAllNodesWithText("Sequence", substring = true).onFirst().assertIsDisplayed()
    }
}
