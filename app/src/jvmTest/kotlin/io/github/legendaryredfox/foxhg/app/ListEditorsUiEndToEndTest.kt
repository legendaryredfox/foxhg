package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterData
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterGroup
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.fly.FlyTable
import io.github.legendaryredfox.foxhg.core.patch.PatchStatus
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.data.hiddenitem.HiddenItems
import io.github.legendaryredfox.foxhg.core.data.battledisplay.BattleDisplayTable
import io.github.legendaryredfox.foxhg.core.data.bugcontest.BugContestTable
import io.github.legendaryredfox.foxhg.core.data.growth.GrowthCurve
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.data.personal.TmHmBulk
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerTrainer
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.data.learnset.Learnset
import io.github.legendaryredfox.foxhg.core.data.pickup.PickupTable
import io.github.legendaryredfox.foxhg.core.data.typechart.TypeChart
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerFlagBulk
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttFile
import io.github.legendaryredfox.foxhg.core.data.safari.SafariZoneFile
import io.github.legendaryredfox.foxhg.core.data.rocksmash.RockSmashItems
import io.github.legendaryredfox.foxhg.core.data.rocksmash.RockSmashSite
import io.github.legendaryredfox.foxhg.core.data.starter.Starters
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.data.matrix.GameMatrix
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerMessages
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
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
    fun addingAndRemovingAWildPokemonOnTheWildEncountersScreen() = withHeartGoldApp { model, project ->
        val names = project.names!!
        val route30 = EncounterEditorModel(project).let { m -> m.editor.rows().first { m.editor.label(it).contains("Route 30") } }
        val area = { (EncounterData.loadHeartGold(project) as FoxResult.Ok).value.records[route30] }
        val mareep = names.species.indexOf("MAREEP")
        val before = EncounterGroup.GRASS_DAY.roster(area())
        assertTrue(before.none { it.species == mareep })
        val victim = before.last().species

        open("Wild encounters")
        clickFirstText("Route 30", substring = true)
        tap(onNodeWithTag("add:GRASS_DAY"))
        // the dialog's picker is the last "Species" one and sits outside the scroll pane
        tap(onAllNodesWithTag("picker:Species").onLast(), inScrollPane = false)
        onAllNodesWithTag("search:Search…").onLast().performTextInput("MAREEP")
        waitForIdle()
        onAllNodesWithText("MAREEP").onLast().performClick()
        waitForIdle()
        onNodeWithTag("number:add-percent").typeNumber(10, inScrollPane = false)
        tap(onNodeWithTag("confirm"), inScrollPane = false)
        val added = EncounterGroup.GRASS_DAY.roster(area())
        assertEquals(10, added.first { it.species == mareep }.percent)
        assertEquals(100, added.sumOf { it.percent })
        assertEquals(before.size, EncounterGroup.GRASS_NIGHT.roster(area()).size.coerceAtLeast(before.size), "other times of day are not touched")

        tap(onNodeWithTag("remove:GRASS_DAY:$victim"))
        val removed = EncounterGroup.GRASS_DAY.roster(area())
        assertTrue(removed.none { it.species == victim })
        assertEquals(100, removed.sumOf { it.percent })

        buildAndReopen(model) { rebuilt ->
            val rec = (EncounterData.loadHeartGold(rebuilt) as FoxResult.Ok).value.records[route30]
            assertTrue(EncounterGroup.GRASS_DAY.roster(rec).any { it.species == mareep })
            assertTrue(EncounterGroup.GRASS_DAY.roster(rec).none { it.species == victim })
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
        onNodeWithTag("hidden-index-notice").assertIsDisplayed()

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
    fun editingAnItemBallQuantityOnItsScreenWritesTheItemScript() = withHeartGoldApp { _, project ->
        val balls = { (io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItems.load(project) as FoxResult.Ok).value }
        assertEquals(listOf(17, 1), balls().first().let { listOf(it.item, it.quantity) })
        open("Ground items")
        onNodeWithTag("number:Quantity").typeNumber(4)
        waitForIdle()
        assertEquals(listOf(17, 4), balls().first().let { listOf(it.item, it.quantity) }, "the quantity went into the SetVar of script_1")
        assertEquals(255, balls().size)
        assertTrue(project.isDirty)
    }

    @Test
    fun editingATownOnTheTownMapScreenWritesOverlay101() = withHeartGoldApp { _, project ->
        val table = { (io.github.legendaryredfox.foxhg.core.data.townmap.TownMap.load(project) as FoxResult.Ok).value }
        assertEquals(0x20, table().spots[0].redX)
        open("Town Map")
        onNodeWithTag("number:Red X").typeNumber(33)
        waitForIdle()
        assertEquals(33, table().spots[0].redX, "the red X went into overlay 101")
        assertEquals(0x0B, table().spots[0].redY)
        assertTrue(project.isDirty)
    }

    @Test
    fun restoringTheHpBarSpeedOnItsScreenWritesOverlay12() = withHeartGoldApp { _, project ->
        val speed = { (io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed.load(project) as FoxResult.Ok).value }
        assertEquals(io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed.VANILLA, speed())
        assertTrue(io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed.save(project, io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed(10)) is FoxResult.Ok)
        open("HP bar speed")
        tap(onNodeWithTag("hpbar-restore"), inScrollPane = false)
        waitForIdle()
        assertEquals(io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed.VANILLA, speed(), "the restore button put the game's speed back")
    }

    @Test
    fun editingAPhotoLocationOnItsScreenWritesTheNarc() = withHeartGoldApp { _, project ->
        val photos = { (io.github.legendaryredfox.foxhg.core.data.photo.PhotoLocation.load(project) as FoxResult.Ok).value.records }
        val before = photos()
        open("Photo locations")
        onNodeWithTag("number:Player X").typeNumber(123)
        waitForIdle()
        assertEquals(123, photos()[0].x, "the position went into a/2/5/4")
        assertEquals(before[0].copy(x = 123), photos()[0])
        assertEquals(before.drop(1), photos().drop(1))
        assertTrue(project.isDirty)
    }

    @Test
    fun editingABattleRuleOnItsScreenWritesTheBattleScript() = withHeartGoldApp { _, project ->
        val burn = io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules.byId("burn-damage")!!
        val value = { (io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules.read(project, burn) as FoxResult.Ok).value }
        assertEquals(8, value())
        open("Battle rules")
        onNodeWithTag("number:Battle rule").typeNumber(16)
        waitForIdle()
        assertEquals(16, value(), "the burn divisor went into battle script 26")
        tap(onNodeWithTag("battle-rule-restore"), inScrollPane = false)
        waitForIdle()
        assertEquals(8, value(), "the restore button put the game's value back")
    }

    @Test
    fun theResearchScreenFindsTheUsesOfAFlagAndOpensTheScript() = withHeartGoldApp { model, _ ->
        open("Research")
        onNodeWithTag("number:Research number").typeNumber(579)
        waitForIdle()
        tap(onNodeWithTag("research-find"), inScrollPane = false)
        waitForIdle()
        onNodeWithTag("research-result:0").assertIsDisplayed()
        tap(onNodeWithTag("research-result:0"), inScrollPane = false)
        waitForIdle()
        assertEquals(EditorKind.SCRIPTS, model.selected, "a script use opens the Scripts editor")
        assertEquals(104, model.selectionMemory[EditorKind.SCRIPTS], "on the file of the Lugia script, the first use of flag 579")
    }

    @Test
    fun applyingToolboxPatchesOnTheirScreen() = withHeartGoldApp { model, project ->
        open("ROM toolbox")
        onNodeWithTag("apply:dynamic-camera").assertIsNotEnabled()
        onNodeWithTag("apply:trainer-names").assertIsEnabled()
        tap(onNodeWithTag("apply:arm9-expansion"), inScrollPane = false)
        tap(onNodeWithTag("confirm"), inScrollPane = false)
        waitForIdle()
        assertEquals(PatchStatus.Applied, DsprePatches.arm9Expansion.status(project))
        onNodeWithTag("apply:arm9-expansion").assertIsNotEnabled()
        onNodeWithTag("apply:dynamic-camera").assertIsEnabled()
        onNodeWithTag("toolbox-message").assertIsDisplayed()

        buildAndReopen(model) { rebuilt ->
            assertEquals(PatchStatus.Applied, DsprePatches.arm9Expansion.status(rebuilt), "the expansion is in the built ROM")
            assertEquals(PatchStatus.Available, DsprePatches.trainerNames.status(rebuilt))
        }
    }

    @Test
    fun editingATownOnTheFlyScreen() = withHeartGoldApp { model, project ->
        val table = { (FlyTable.load(project) as FoxResult.Ok).value }
        val vanilla = table().entries[0]
        open("Fly")
        onNodeWithTag("number:Fly X").typeNumber(700)
        waitForIdle()
        assertEquals(vanilla.copy(flyX = 700), table().entries[0], "only the landing X changed")
        onNodeWithTag("number:Blackout Y").typeNumber(9)
        waitForIdle()
        assertEquals(9, table().entries[0].localY)

        buildAndReopen(model) { rebuilt ->
            val reread = (FlyTable.load(rebuilt) as FoxResult.Ok).value
            assertEquals(vanilla.copy(flyX = 700, localY = 9), reread.entries[0])
            assertEquals(30, reread.entries.size)
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
    fun editingHeadbuttTablesAndTreesOnItsScreen() = withHeartGoldApp { model, project ->
        val route29 = EventLabels(project).let { labels -> (0 until 540).first { labels.mapName(it) == "Route 29" } }
        val files = { (NarcRecords.load(project, HeadbuttFile.SPEC) as FoxResult.Ok).value.records }
        val before = files()[route29]
        assertTrue(before.hasTrees, "Route 29 has Headbutt trees")
        val pidgey = project.names!!.species.indexOf("PIDGEY")

        open("Headbutt")
        onNodeWithTag("search:Search…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)
        pick("Common 1", "PIDGEY")
        assertEquals(pidgey, files()[route29].normal[0].species)
        onNodeWithTag("number:Common 1 max").typeNumber(9)
        onNodeWithTag("number:Common 1 min").typeNumber(4)
        waitForIdle()
        assertEquals(listOf(4, 9), files()[route29].normal[0].let { listOf(it.minLevel, it.maxLevel) })
        onNodeWithTag("number:Normal 1 tile 1 X").typeNumber(100)
        waitForIdle()
        assertEquals(100, files()[route29].normalGroups[0].trees[0].x)
        assertEquals(before.normalGroups[0].trees[0].y, files()[route29].normalGroups[0].trees[0].y, "only X moved")

        tap(onAllNodesWithText("Add tree")[0])
        waitForIdle()
        assertEquals(before.normalGroups.size + 1, files()[route29].normalGroups.size)
        assertTrue(files()[route29].normalGroups.last().trees.all { it.isUnused }, "a new group starts empty")
        assertEquals(before.specialGroups, files()[route29].specialGroups)

        buildAndReopen(model) { rebuilt ->
            val h = (NarcRecords.load(rebuilt, HeadbuttFile.SPEC) as FoxResult.Ok).value.records[route29]
            assertEquals(files()[route29], h)
            assertEquals(pidgey, h.normal[0].species)
        }
    }

    @Test
    fun editingABattleDisplayRecordOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (BattleDisplayTable.load(project) as FoxResult.Ok).value }
        val before = table()
        open("Battle display")
        onNodeWithTag("search:Search species…").performTextInput("pikachu")
        waitForIdle()
        clickFirstText("PIKACHU", substring = true)
        onNodeWithTag("number:Sprite Y").typeNumber(9)
        waitForIdle()
        onNodeWithTag("number:Shadow size").typeNumber(3)
        waitForIdle()
        assertEquals(before.get(25).copy(spriteY = 9, shadowSize = 3), table().get(25))
        assertEquals(before.get(24), table().get(24), "other species stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table().get(25), (BattleDisplayTable.load(rebuilt) as FoxResult.Ok).value.get(25))
        }
    }

    @Test
    fun editingAGrowthRateOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (NarcRecords.load(project, GrowthCurve.SPEC) as FoxResult.Ok).value.records }
        val before = table()
        open("Growth rates")
        tap(onNodeWithTag("chip:Fast"))
        waitForIdle()
        onNodeWithTag("number:Level 10").performScrollTo().typeNumber(before[4].exp[10] + 100)
        waitForIdle()
        assertEquals(before[4].withLevel(10, before[4].exp[10] + 100), table()[4], "later levels moved with it")
        assertEquals(before[0], table()[0], "other rates stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table()[4], (NarcRecords.load(rebuilt, GrowthCurve.SPEC) as FoxResult.Ok).value.records[4])
        }
    }

    @Test
    fun editingTmHmCompatibilityInBulkOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (PersonalTable.load(project) as FoxResult.Ok).value }
        val before = table()
        open("TM/HM compatibility")
        onNodeWithTag("search:Search species…").performTextInput("bulbasaur")
        waitForIdle()
        val had = before.records[1].learnsTmHm(0)
        tap(onNodeWithTag("tmhm:1"))
        waitForIdle()
        assertEquals(!had, table().records[1].learnsTmHm(0), "one species toggled")
        assertEquals(before.records[2], table().records[2], "the rest of the family is not touched by a single toggle")
        tap(onNodeWithTag("tmhm-family:1"))
        waitForIdle()
        assertEquals(List(3) { had }, listOf(1, 2, 3).map { table().records[it].learnsTmHm(0) }, "the family button gives the whole family the opposite of this species' new state")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table().records, (PersonalTable.load(rebuilt) as FoxResult.Ok).value.records)
        }
    }

    @Test
    fun editingTrainerBattleFlagsInBulkOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (TrainerTable.load(project) as FoxResult.Ok).value }
        val before = table()
        val target = (1 until before.trainers.size).first { !TrainerFlagBulk.has(before.trainers[it], TrainerFlagBulk.DOUBLE_BATTLE) }
        open("Battle flags")
        onNodeWithTag("picker:Flag").performClick() // the header is not in a scrolling list, so no scrolling into view
        waitForIdle()
        onAllNodesWithTag("search:Search…").onLast().performTextInput("Double Battle")
        waitForIdle()
        onAllNodesWithText("Double Battle").onLast().performClick()
        waitForIdle()
        onNodeWithTag("search:Search trainers or classes…").performTextInput("%03d".format(target))
        waitForIdle()
        tap(onNodeWithTag("flag:$target"))
        waitForIdle()
        assertTrue(TrainerFlagBulk.has(table().trainers[target], TrainerFlagBulk.DOUBLE_BATTLE), "the flag went on")
        assertEquals(2L, table().trainers[target].header.doubleBattle, "the game stores 2")
        assertEquals(before.trainers.indices.filter { it != target }.map { before.trainers[it] }, table().trainers.filterIndexed { i, _ -> i != target }, "other trainers stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table().trainers, (TrainerTable.load(rebuilt) as FoxResult.Ok).value.trainers)
        }
    }

    @Test
    fun editingBattleTowerTrainersAndSetsOnTheirScreens() = withHeartGoldApp { model, project ->
        val trainers = { (NarcRecords.load(project, BattleTowerTrainer.SPEC) as FoxResult.Ok).value.records }
        val sets = { (NarcRecords.load(project, BattleTowerSet.SPEC) as FoxResult.Ok).value.records }
        val beforeTrainers = trainers()
        val beforeSets = sets()

        model.openAt(EditorKind.BATTLE_TOWER_TRAINERS, 5) // the list shows ids and names in separate texts, so go there by number
        waitForIdle()
        waitLoaded()
        val count = beforeTrainers[5].setIds.size
        onNodeWithTag("number:Set to add").performScrollTo().typeNumber(7)
        waitForIdle()
        tap(onNodeWithTag("bt-add"))
        waitForIdle()
        assertEquals(beforeTrainers[5].addSet(7), trainers()[5], "the set was appended")
        tap(onNodeWithTag("bt-remove:0"))
        waitForIdle()
        assertEquals(beforeTrainers[5].addSet(7).removeSet(0), trainers()[5])
        assertEquals(count, trainers()[5].setIds.size)
        assertEquals(beforeTrainers[4], trainers()[4], "other trainers stay")

        model.openAt(EditorKind.BATTLE_TOWER_SETS, 2)
        waitForIdle()
        waitLoaded()
        tap(onNodeWithTag("chip:Speed"))
        waitForIdle()
        assertEquals(beforeSets[2].withEv(3, !beforeSets[2].hasEv(3)), sets()[2])
        assertEquals(beforeSets[1], sets()[1], "other sets stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(trainers(), (NarcRecords.load(rebuilt, BattleTowerTrainer.SPEC) as FoxResult.Ok).value.records)
            assertEquals(sets(), (NarcRecords.load(rebuilt, BattleTowerSet.SPEC) as FoxResult.Ok).value.records)
        }
    }

    @Test
    fun editingAPokeMartOnItsScreen() = withHeartGoldApp { model, project ->
        val marts = { (Marts.load(project) as FoxResult.Ok).value }
        val before = marts()
        open("Poké Marts")
        onNodeWithTag("search:Search marts or items…").performTextInput("Awakening")
        waitForIdle()
        onAllNodesWithText("Potion, Super Potion, Hyper Potion", substring = true).onFirst().performClick() // marts 3 and 18 are alike: the first is 3
        waitForIdle()
        tap(onNodeWithTag("mart-add"))
        waitForIdle()
        assertEquals(before.specials[3], marts().specials[3], "a full mart cannot take another item")
        tap(onNodeWithTag("mart-remove:0"))
        waitForIdle()
        assertEquals(before.specials[3].drop(1), marts().specials[3], "the first item is gone")
        tap(onNodeWithTag("mart-add"))
        waitForIdle()
        assertEquals(before.specials[3].drop(1) + 17, marts().specials[3], "it is back at the end as a Potion")
        assertEquals(before.specials.filterIndexed { i, _ -> i != 3 }, marts().specials.filterIndexed { i, _ -> i != 3 }, "other marts stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(marts().specials, (Marts.load(rebuilt) as FoxResult.Ok).value.specials)
        }
    }

    @Test
    fun aPokeMartGrowsPastItsPlaceOnceTheArm9IsExpanded() = withHeartGoldApp { model, project ->
        val marts = { (Marts.load(project) as FoxResult.Ok).value }
        val before = marts()
        open("Poké Marts")
        onAllNodesWithText("Air Mail, Heal Ball").onFirst().performClick()
        waitForIdle()
        onNodeWithText("Apply the ARM9 expansion patch", substring = true).assertExists()
        tap(onNodeWithTag("mart-add"))
        waitForIdle()
        assertEquals(before.specials[0], marts().specials[0], "no room before the expansion")

        assertTrue(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        model.openAt(EditorKind.TOOLBOX, 0) // leave and come back so the screen reads the expanded ROM
        waitForIdle()
        open("Poké Marts")
        onAllNodesWithText("Air Mail, Heal Ball").onFirst().performClick()
        waitForIdle()
        repeat(3) { tap(onNodeWithTag("mart-add")); waitForIdle() }
        assertEquals(5, marts().specials[0].size)
        assertTrue(marts().isMoved(0))
        onNodeWithText("Moved to the synthetic overlay").assertExists()
        assertEquals(before.specials.drop(1), marts().specials.drop(1), "other marts stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(marts().specials, (Marts.load(rebuilt) as FoxResult.Ok).value.specials)
        }
    }

    @Test
    fun editingTheTypeChartOnItsScreen() = withHeartGoldApp { model, project ->
        val chart = { (TypeChart.load(project) as FoxResult.Ok).value }
        val before = chart()
        open("Type chart")
        // Fire (10) against Grass (12) is 2x: three clicks cycle 2x -> 1/2x -> 0x -> 1x, which frees a slot
        tap(onNodeWithTag("type:10:12"))
        waitForIdle()
        assertEquals(5, chart().multiplier(10, 12))
        tap(onNodeWithTag("type:10:12")); waitForIdle()
        tap(onNodeWithTag("type:10:12")); waitForIdle()
        assertEquals(10, chart().multiplier(10, 12))
        assertEquals(before.slotsUsed - 1, chart().slotsUsed)
        // Normal (0) against Fire (10) is 1x: it takes the freed slot
        tap(onNodeWithTag("type:0:10"))
        waitForIdle()
        assertEquals(20, chart().multiplier(0, 10))
        // the table is full again: Normal against Water cannot be added
        tap(onNodeWithTag("type:0:11"))
        waitForIdle()
        assertEquals(10, chart().multiplier(0, 11), "no free slot")
        onNodeWithText("No free slot", substring = true).assertExists()

        buildAndReopen(model) { rebuilt ->
            assertEquals(chart(), (TypeChart.load(rebuilt) as FoxResult.Ok).value)
        }
    }

    @Test
    fun teachingAndForgettingAMoveInBulkOnTheLearnsetScreen() = withHeartGoldApp { model, project ->
        val sets = { (Learnset.load(project) as FoxResult.Ok).value.records }
        val before = sets()
        val scratch = project.names!!.moves.indexOf("Scratch")
        open("Learnsets")
        onNodeWithTag("search:Search…").performTextInput("bulbasaur")
        waitForIdle()
        pick("Bulk move", "Scratch")
        onNodeWithTag("number:Bulk level").typeNumber(5)
        waitForIdle()
        tap(onNodeWithTag("bulk-teach"))
        waitForIdle()
        assertTrue(sets()[1].entries.any { it.move == scratch && it.level == 5 }, "Bulbasaur learned it")
        assertEquals(before.drop(2), sets().drop(2), "the species that are not shown stay as they were")
        tap(onNodeWithTag("bulk-forget"))
        waitForIdle()
        assertEquals(before[1], sets()[1], "forgetting it again restores the learnset")

        buildAndReopen(model) { rebuilt ->
            assertEquals(sets(), (Learnset.load(rebuilt) as FoxResult.Ok).value.records)
        }
    }

    @Test
    fun editingThePickupTablesOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (PickupTable.load(project) as FoxResult.Ok).value }
        val before = table()
        open("Pickup")
        onNodeWithTag("number:Divisor").typeNumber(5)
        waitForIdle()
        assertEquals(before.copy(divisor = 5), table())
        onNodeWithTag("number:Weight 1").typeNumber(20)
        waitForIdle()
        assertEquals(before.copy(divisor = 5, weights = before.weights.toMutableList().also { it[0] = 20 }), table())
        onNodeWithText("Activates 20.00% of battles").assertExists()

        buildAndReopen(model) { rebuilt ->
            assertEquals(table(), (PickupTable.load(rebuilt) as FoxResult.Ok).value)
        }
    }

    @Test
    fun editingTheBugContestSetsOnItsScreen() = withHeartGoldApp { model, project ->
        val table = { (BugContestTable.load(project) as FoxResult.Ok).value }
        val before = table()
        val scyther = project.names!!.species.indexOf("SCYTHER")

        open("Bug Contest")
        tap(onNodeWithTag("chip:Set 2"))
        waitForIdle()
        pick("Slot 1", "SCYTHER")
        assertEquals(scyther, table().sets[2][0].species)
        assertEquals(before.sets[0], table().sets[0], "other sets stay")
        onNodeWithTag("number:Slot 1 rate").typeNumber(60)
        waitForIdle()
        assertEquals(60, table().sets[2][0].rate)
        onNodeWithText("Slot 2 can never appear", substring = true).assertExists()

        buildAndReopen(model) { rebuilt ->
            assertEquals(table(), (BugContestTable.load(rebuilt) as FoxResult.Ok).value)
        }
    }

    @Test
    fun editingTheSafariZoneAreasOnItsScreen() = withHeartGoldApp { model, project ->
        val files = { (NarcRecords.load(project, SafariZoneFile.SPEC) as FoxResult.Ok).value.records }
        val before = files()
        val scyther = project.names!!.species.indexOf("SCYTHER")

        open("Safari Zone")
        tap(onNodeWithTag("chip:Surfing"))
        waitForIdle()
        pick("Day 3", "SCYTHER")
        assertEquals(scyther, files()[0].groups[1].day[2].species)
        onNodeWithTag("number:Day 3 level").typeNumber(44)
        waitForIdle()
        assertEquals(44, files()[0].groups[1].day[2].level)
        assertEquals(before[0].groups[0], files()[0].groups[0], "other groups stay")
        assertEquals(before.drop(1), files().drop(1), "other areas stay")

        buildAndReopen(model) { rebuilt ->
            assertEquals(files(), (NarcRecords.load(rebuilt, SafariZoneFile.SPEC) as FoxResult.Ok).value.records)
        }
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
    fun mapHeaderLinksOpenTheMatchingEditors() = withHeartGoldApp { model, project ->
        val route29 = MapHeaderEditorModel(project).let { m -> m.search = "route 29"; m.rows().first() }
        val h = (MapHeaderTable.load(project) as FoxResult.Ok).value[route29]

        open("Map headers")
        onNodeWithTag("search:Search maps…").performTextInput("route 29")
        waitForIdle()
        clickFirstText("Route 29", substring = true)
        tap(onNodeWithText("Open matrix"))
        waitForIdle()
        assertEquals(EditorKind.MATRICES, model.selected)
        assertEquals(h.matrix, model.selectionMemory[EditorKind.MATRICES])

        model.selected = EditorKind.HEADERS
        waitLoaded()
        tap(onNodeWithText("Open event file"))
        waitForIdle()
        assertEquals(EditorKind.EVENTS, model.selected)
        assertEquals(h.eventFile, model.selectionMemory[EditorKind.EVENTS])
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

    @Test
    fun editingAndAddingBattleMessagesOnTheTrainerMessagesScreenWritesTheTablesAndTheText() = withHeartGoldApp { _, project ->
        val names = project.names!!
        val falkner = names.trainerNames.indexOfFirst { it.equals("Falkner", ignoreCase = true) }
        val load = { (TrainerMessages.load(project) as FoxResult.Ok).value }
        val before = load()
        val first = before.messagesOf(falkner).first()
        assertTrue(before.messagesOf(falkner).size >= 2)

        open("Trainer messages")
        onNodeWithTag("search:Search trainers").performTextInput("Falkner")
        waitForIdle()
        // node 0 is the search box itself, then the rows 020 and 712 (two Falkners)
        onAllNodesWithText("Falkner")[1].performClick()
        waitForIdle()
        onAllNodesWithTag("textarea").onFirst().performTextReplacement("Edited battle line!")
        onAllNodesWithText("Apply").onFirst().performClick()
        waitForIdle()
        assertEquals("Edited battle line!", load().message(first).text)
        assertEquals(before.size, load().size)
        assertTrue(project.isDirty)

        onNodeWithText("Add message").performScrollTo().performClick()
        waitForIdle()
        assertEquals(before.size + 1, load().size)
        assertEquals(before.messagesOf(falkner).size + 1, load().messagesOf(falkner).size)
        // every other trainer still finds its own messages
        val other = (0 until before.offsets.size).first { it != falkner && before.messagesOf(it).isNotEmpty() }
        assertEquals(before.messagesOf(other).map { before.message(it) }, load().messagesOf(other).map { load().message(it) })
    }
}
