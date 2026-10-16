package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The saves made by the emulator tests (`core/build/emulator-saves`, never committed): they parse, their checksums are right and a rewrite gives the same bytes. Skips without them. */
class RealSaveTest {
    private val names = listOf("cherrygrove", "viridian")

    @Test
    fun theRealSavesAreWholeAndRoundTrip() {
        val dir = Path.of("build", "emulator-saves")
        val files = names.map { dir.resolve("$it.dsv") }.filter { Files.exists(it) }
        if (files.isEmpty()) { println("SKIPPED: no saves in ${dir.toAbsolutePath()} (run :core:emulatorSetup)"); return }
        for (path in files) {
            val file = Files.readAllBytes(path)
            val save = (SaveFile.parse(file) as? FoxResult.Ok)?.value ?: error("$path does not parse")
            assertContentEquals(file, save.toFileBytes(), "$path writes back the same bytes")
            // the first save of a new game is in the second slot, counter 1: slot 0 was never written (all 0xFF)
            assertEquals(listOf(false, false, true, true), save.blocks.map { it.valid }, "$path: only the second slot holds a save")
            for (kind in BlockKind.entries) {
                val current = save.current(kind) ?: error("no current $kind block in $path")
                assertEquals(1, current.slot)
                assertEquals(1L, current.footer.count, "saved once")
            }
            assertContentEquals(file, save.withFixedChecksums().toFileBytes(), "recomputing the checksums of a whole save changes nothing")
            assertTrue(save.bytes.copyOfRange(0, SaveFormat.SLOT_OFFSETS[1]).all { it == 0xFF.toByte() }, "slot 0 is blank")

            // the chunk table: the chunks fill each block exactly, and the six that keep their own CRC have it where the offsets put it
            for (kind in BlockKind.entries) assertEquals(kind.size - BlockFooter.SIZE, SaveChunks.of(kind).sumOf { it.stride }, "the $kind chunks fill the block")
            val general = save.current(BlockKind.GENERAL)!!
            for (chunk in SaveChunks.of(BlockKind.GENERAL).filter { it.hasOwnCrc }) {
                val rounded = (chunk.size + 3) and 3.inv()
                val stored = (save.bytes[general.offset + chunk.crcOffset].toInt() and 0xFF) or ((save.bytes[general.offset + chunk.crcOffset + 1].toInt() and 0xFF) shl 8)
                assertEquals(SaveFormat.crc16(save.bytes, general.offset + chunk.offset, general.offset + chunk.offset + rounded), stored, "the CRC of ${chunk.name}")
            }
            // rewriting a chunk with itself changes nothing, rewriting it with something else keeps both CRCs right
            for (name in listOf("PLAYERDATA", "MYSTERY_GIFT")) {
                val chunk = SaveChunks.byName(name)
                assertContentEquals(file, save.withChunk(general, chunk, save.chunk(general, chunk)).toFileBytes(), "$name written back as it is")
                val other = save.chunk(general, chunk).also { it[0] = (it[0] + 1).toByte() }
                val changed = save.withChunk(general, chunk, other)
                assertTrue(changed.block(1, BlockKind.GENERAL).valid, "the block CRC of the edited save")
                assertContentEquals(other, changed.chunk(changed.block(1, BlockKind.GENERAL), chunk))
            }
        }
    }

    @Test
    fun thePlayerChunkReadsAsTheGameSaidItAndWritesBack() {
        val dir = Path.of("build", "emulator-saves")
        val expected = mapOf("cherrygrove" to 1500L, "viridian" to 2750L) // the money on the shop screens of those saves
        val files = expected.keys.filter { Files.exists(dir.resolve("$it.dsv")) }
        if (files.isEmpty()) { println("SKIPPED: no saves in ${dir.toAbsolutePath()}"); return }
        for (name in files) {
            val file = Files.readAllBytes(dir.resolve("$name.dsv"))
            val save = (SaveFile.parse(file) as FoxResult.Ok).value
            val player = (PlayerData.read(save) as? FoxResult.Ok)?.value ?: error("$name: no player data")
            assertEquals("AAAAAAA", player.name, "the default name on the Continue screen")
            assertEquals(expected[name], player.money, "the money the shop showed")
            assertEquals(0, player.gender, "a boy")
            assertEquals(2, player.language, "English")
            assertEquals(7, player.version, "HeartGold")
            assertEquals(0, player.badgeCount)
            assertEquals(listOf(0, 1, 10), listOf(player.hours, player.minutes, player.seconds), "0:01 on the Continue screen, 10 seconds in")
            assertContentEquals(save.chunk(save.current(BlockKind.GENERAL)!!, SaveChunks.byId(SaveChunks.PLAYERDATA)), player.toBytes(), "$name: the chunk writes back as it was")
            assertContentEquals(file, (save.withPlayer(player) as FoxResult.Ok).value.toFileBytes(), "$name: writing the player back changes no byte")

            val edited = player.copy(money = 123_456, johtoBadges = 0b101, hours = 12, coins = 77).withName("FOX")
            val changed = (save.withPlayer((edited as FoxResult.Ok).value) as FoxResult.Ok).value
            val again = (PlayerData.read(changed) as FoxResult.Ok).value
            assertEquals(listOf("FOX", 123_456L, 2, 12, 77), listOf(again.name, again.money, again.badgeCount, again.hours, again.coins))
            assertTrue(changed.block(1, BlockKind.GENERAL).valid && changed.block(1, BlockKind.STORAGE).valid)
            assertEquals(player.trainerId, again.trainerId, "the ID is left alone")
        }
    }

    @Test
    fun theFieldChunkHoldsThePlaceOfTheMartAndWritesBack() {
        val dir = Path.of("build", "emulator-saves")
        val places = mapOf("cherrygrove" to Location(68, -1, 3, 6, 2), "viridian" to Location(500, -1, 3, 4, 2)) // where those saves were made
        val files = places.keys.filter { Files.exists(dir.resolve("$it.dsv")) }
        if (files.isEmpty()) { println("SKIPPED: no saves in ${dir.toAbsolutePath()}"); return }
        for (name in files) {
            val file = Files.readAllBytes(dir.resolve("$name.dsv"))
            val save = (SaveFile.parse(file) as FoxResult.Ok).value
            val field = (LocalFieldData.read(save) as? FoxResult.Ok)?.value ?: error("$name: no field data")
            assertEquals(places[name], field.current, "$name: where the player stood when saving")
            assertEquals(field.current, field.previous)
            assertContentEquals(save.chunk(save.current(BlockKind.GENERAL)!!, SaveChunks.byId(SaveChunks.LOCAL_FIELD_DATA)), field.toBytes(), "$name: the chunk writes back as it was")
            assertContentEquals(file, (save.withLocalFieldData(field) as FoxResult.Ok).value.toFileBytes(), "$name: writing it back changes no byte")
            val moved = (save.withLocalFieldData(field.movedTo(Location(1, -1, 5, 6, 0))) as FoxResult.Ok).value
            assertEquals(Location(1, -1, 5, 6, 0), (LocalFieldData.read(moved) as FoxResult.Ok).value.current)
            assertTrue(moved.blocks.count { it.valid } == 2)

            // moving a save sets the place, the dynamic warp and the flag that makes Continue take it
            val general = save.current(BlockKind.GENERAL)!!
            val flagsChunk = SaveChunks.byId(SaveChunks.FLAGS)
            assertTrue(!SaveVarsFlags.isSet(save.chunk(general, flagsChunk), SaveVarsFlags.CONTINUE_AT_DYNAMIC_WARP), "the game's own save does not have it")
            val relocated = (save.movedTo(Location(500, -1, 3, 4, 2)) as FoxResult.Ok).value
            val after = (LocalFieldData.read(relocated) as FoxResult.Ok).value
            assertEquals(listOf(Location(500, -1, 3, 4, 2), Location(500, -1, 3, 4, 2), Location(500, -1, 3, 4, 2)), listOf(after.current, after.previous, after.dynamicWarp))
            assertTrue(SaveVarsFlags.isSet(relocated.chunk(relocated.current(BlockKind.GENERAL)!!, flagsChunk), SaveVarsFlags.CONTINUE_AT_DYNAMIC_WARP))
            assertContentEquals(save.chunk(general, SaveChunks.byId(SaveChunks.MAP_OBJECTS)), relocated.chunk(relocated.current(BlockKind.GENERAL)!!, SaveChunks.byId(SaveChunks.MAP_OBJECTS)), "the saved map objects are left alone")
        }
    }
}
