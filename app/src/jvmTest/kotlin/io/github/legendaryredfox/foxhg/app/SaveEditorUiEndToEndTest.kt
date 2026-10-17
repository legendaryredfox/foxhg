package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.save.BagData
import io.github.legendaryredfox.foxhg.core.save.BagPocket
import io.github.legendaryredfox.foxhg.core.save.BagSlot
import io.github.legendaryredfox.foxhg.core.save.BlockFooter
import io.github.legendaryredfox.foxhg.core.save.BlockKind
import io.github.legendaryredfox.foxhg.core.save.PartyData
import io.github.legendaryredfox.foxhg.core.save.PlayerData
import io.github.legendaryredfox.foxhg.core.save.Pokemon
import io.github.legendaryredfox.foxhg.core.save.SaveChunks
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.SaveFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A save file with one sound slot (slot 1, counter 1) and a player called TEST with 1500 money, made without the game. */
object SyntheticSave {
    fun bytes(): ByteArray {
        val file = ByteArray(SaveFormat.FILE_SIZE) { 0xFF.toByte() }
        val base = SaveFormat.SLOT_OFFSETS[1]
        for (kind in BlockKind.entries) {
            val offset = base + kind.offset
            file.fill(0, offset, offset + kind.size - BlockFooter.SIZE)
            if (kind == BlockKind.GENERAL) {
                val player = (PlayerData.parse(ByteArray(PlayerData.SIZE)) as FoxResult.Ok).value.copy(money = 1500, version = 7, language = 2)
                val named = (player.withName("TEST") as FoxResult.Ok).value
                named.toBytes().copyInto(file, offset + SaveChunks.byId(SaveChunks.PLAYERDATA).offset)
                // the game writes its empty party slots encrypted, never as zeros
                PartyData(6, 0, List(PartyData.SLOTS) { Pokemon.EMPTY }, ByteArray(PartyData.EXTRA)).toBytes().copyInto(file, offset + SaveChunks.byId(SaveChunks.PARTY).offset)
            }
            val footer = offset + kind.size - BlockFooter.SIZE
            file.putU32(footer, 1); file.putU32(footer + 4, kind.size.toLong()); file.putU32(footer + 8, BlockFooter.MAGIC)
            file.putU16(footer + 12, kind.index); file.putU16(footer + 14, SaveFormat.crc16(file, offset, footer))
        }
        return file
    }
}

/** The save editor through the widgets: a save opens with no ROM, its tabs show, an edit is written with a backup, and leaving with an unwritten edit asks first. */
@OptIn(ExperimentalTestApi::class, kotlin.io.path.ExperimentalPathApi::class)
class SaveEditorUiEndToEndTest {
    private fun withSave(block: (Path, ByteArray) -> Unit) {
        val dir = tempDir("foxhg-save-ui")
        try {
            val original = SyntheticSave.bytes()
            val path = dir.resolve("heartgold.sav").also { Files.write(it, original) }
            block(path, original)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun money(path: Path): Long =
        (PlayerData.read((SaveFile.parse(Files.readAllBytes(path)) as FoxResult.Ok).value) as FoxResult.Ok).value.money

    @Test
    fun aSaveOpensWithoutARomAndShowsOnlyItsOwnTabs() = withSave { path, _ ->
        val model = testModel()
        assertTrue(model.openSave(path))
        runApp(model) {
            onNodeWithTag("nav:Player").assertIsDisplayed()
            onNodeWithTag("nav:Save file").assertIsDisplayed()
            onNodeWithTag("nav:Personal data").assertDoesNotExist()
            onNodeWithText("TEST", substring = true).assertExists()
        }
    }

    @Test
    fun anEditIsWrittenOnlyWhenAskedAndTheOldFileIsKept() = withSave { path, original ->
        val model = testModel()
        model.openSave(path)
        runApp(model) {
            onNodeWithTag("number:Money").typeNumber(4242)
            waitForIdle()
            onNodeWithTag("save-dirty").assertIsDisplayed()
            assertEquals(1500, money(path), "nothing is written until asked")
            tap(onNodeWithTag("save-write"), inScrollPane = false)
            waitForIdle()
            assertEquals(4242, money(path))
            assertContentEquals(original, Files.readAllBytes(path.resolveSibling("heartgold.sav.bak")), "the file that was there is kept")
            assertFalse(model.save!!.dirty)
            assertTrue((SaveFile.parse(Files.readAllBytes(path)) as FoxResult.Ok).value.blocks.count { it.valid } == 2, "the written save is still sound")
        }
    }

    @Test
    fun leavingWithAnUnwrittenEditAsksAndDiscardingKeepsTheFile() = withSave { path, original ->
        val model = testModel()
        model.openSave(path)
        model.save!!.updatePlayer { it.copy(money = 99) }
        var ran = false
        model.guard { ran = true }
        assertTrue(model.dialog is AppDialog.UnsavedSave, "it asks first")
        assertFalse(ran)
        (model.dialog as AppDialog.UnsavedSave).let { model.save!!.discard(); model.dialog = null; model.guard(it.then) }
        assertTrue(ran)
        assertContentEquals(original, Files.readAllBytes(path), "the file was never touched")
        assertNull(model.dialog)
    }

    @Test
    fun aFileThatIsNotASaveIsRefused() {
        val dir = tempDir("foxhg-save-bad")
        try {
            val bad = dir.resolve("bad.sav").also { Files.write(it, ByteArray(1000)) }
            val model = testModel()
            assertFalse(model.openSave(bad))
            assertNotNull(model.error)
            assertNull(model.save)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun movingTheSaveSetsThePlaceTheDynamicWarpAndTheFlag() = withSave { path, _ ->
        val model = testModel()
        model.openSave(path)
        model.selected = EditorKind.SAVE_POSITION
        runApp(model) {
            tap(onNodeWithTag("preset:500"), inScrollPane = false)
            waitForIdle()
            tap(onNodeWithTag("save-move"), inScrollPane = false)
            waitForIdle()
            val field = model.save!!.position!!
            assertEquals(io.github.legendaryredfox.foxhg.core.save.Location(500, -1, 3, 4, 2), field.current)
            assertEquals(field.current, field.dynamicWarp)
            val general = model.save!!.file.current(BlockKind.GENERAL)!!
            assertTrue(io.github.legendaryredfox.foxhg.core.save.SaveVarsFlags.isSet(model.save!!.file.chunk(general, SaveChunks.byId(SaveChunks.FLAGS)), 0x966))
            onNodeWithTag("save-dirty").assertIsDisplayed()
            assertEquals(1500, money(path), "nothing is written until asked")
            tap(onNodeWithTag("save-write"), inScrollPane = false)
            waitForIdle()
            assertTrue((SaveFile.parse(Files.readAllBytes(path)) as FoxResult.Ok).value.blocks.count { it.valid } == 2)
        }
    }

    @Test
    fun thePartyScreenEditsAPokemonAndRemovesIt() = withSave { path, _ ->
        val model = testModel()
        model.openSave(path)
        val pikachu = Pokemon.EMPTY.withSpecies(25).withLevel(20).withHp(50, 50).withMove(0, 85, 15).withMove(1, 98, 30).withIv(0, 10).withExperience(8000)
        model.save!!.updateParty { it.with(it.count, pikachu).with(it.count + 1, pikachu.withSpecies(16).withLevel(7)) }
        assertEquals(listOf(25, 16), model.save!!.party!!.members.map { it.species })
        model.selected = EditorKind.SAVE_PARTY
        runApp(model) {
            onNodeWithTag("number:Level").typeNumber(30)
            waitForIdle()
            assertEquals(30, model.save!!.party!!.members[0].level)
            assertEquals(7, model.save!!.party!!.members[1].level, "the other slot is left alone")
            onNodeWithTag("number:IV 0").typeNumber(31)
            onNodeWithTag("number:PP 1").typeNumber(40)
            waitForIdle()
            model.save!!.party!!.members[0].let { assertEquals(31, it.ivs[0]); assertEquals(40, it.pp[0]); assertEquals(85, it.moves[0]) }
            tap(onNodeWithTag("party-slot:1"), inScrollPane = false)
            waitForIdle()
            onNodeWithTag("number:Level").typeNumber(9)
            waitForIdle()
            assertEquals(listOf(30, 9), model.save!!.party!!.members.map { it.level })
            tap(onNodeWithTag("party-remove"), inScrollPane = false)
            waitForIdle()
            assertEquals(listOf(25), model.save!!.party!!.members.map { it.species })
            onNodeWithTag("save-dirty").assertExists() // the header scrolls away on this long page
            tap(onNodeWithTag("save-write"), inScrollPane = false)
            waitForIdle()
            val written = (SaveFile.parse(Files.readAllBytes(path)) as FoxResult.Ok).value
            assertEquals(listOf(25), (PartyData.read(written) as FoxResult.Ok).value.members.map { it.species })
        }
    }
    @Test
    fun theBagScreenAddsChangesAndRemovesAnItem() = withSave { path, _ ->
        val model = testModel()
        model.openSave(path)
        model.selected = EditorKind.SAVE_BAG
        runApp(model) {
            onNodeWithTag("number:New quantity").typeNumber(7)
            tap(onNodeWithTag("bag-add"), inScrollPane = false)
            waitForIdle()
            assertEquals(listOf(BagSlot(1, 7)), model.save!!.bag!!.items(BagPocket.ITEMS))
            onNodeWithTag("number:Quantity 0").typeNumber(50)
            waitForIdle()
            assertEquals(listOf(BagSlot(1, 50)), model.save!!.bag!!.items(BagPocket.ITEMS))
            tap(onNodeWithTag("bag-pocket:6"), inScrollPane = false)
            waitForIdle()
            tap(onNodeWithTag("bag-add"), inScrollPane = false)
            waitForIdle()
            assertEquals(listOf(BagSlot(1, 1)), model.save!!.bag!!.items(BagPocket.BALLS))
            tap(onNodeWithTag("bag-remove:0"), inScrollPane = false)
            waitForIdle()
            assertEquals(emptyList(), model.save!!.bag!!.items(BagPocket.BALLS))
            tap(onNodeWithTag("save-write"), inScrollPane = false)
            waitForIdle()
            val written = (SaveFile.parse(Files.readAllBytes(path)) as FoxResult.Ok).value
            assertEquals(listOf(BagSlot(1, 50)), (BagData.read(written) as FoxResult.Ok).value.items(BagPocket.ITEMS))
        }
    }
}
