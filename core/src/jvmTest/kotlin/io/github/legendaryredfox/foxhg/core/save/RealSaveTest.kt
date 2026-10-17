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

    @Test
    fun thePartyOfTheRealSaveDecryptsAndWritesBack() {
        val dir = Path.of("build", "emulator-saves")
        if (!Files.exists(dir.resolve("party.dsv"))) { println("SKIPPED: no party save in ${dir.toAbsolutePath()}"); return }
        val file = Files.readAllBytes(dir.resolve("party.dsv"))
        val save = (SaveFile.parse(file) as FoxResult.Ok).value
        val party = (PartyData.read(save) as? FoxResult.Ok)?.value ?: error("the party does not parse")
        assertEquals(listOf(6, 3), listOf(party.maxCount, party.count))
        // what the init script gave: Cyndaquil Lv 5, Pikachu Lv 20 holding a Potion, Pidgey Lv 7
        assertEquals(listOf(155, 25, 16), party.members.map { it.species })
        assertEquals(listOf(5, 20, 7), party.members.map { it.level })
        assertEquals(listOf(0, 17, 0), party.members.map { it.heldItem })
        assertEquals(listOf(33, 43), party.members[0].moves.filter { it != 0 }, "Cyndaquil knows Tackle and Leer at level 5")
        assertEquals(listOf(33, 28), party.members[2].moves.filter { it != 0 }, "Pidgey knows Tackle and Sand-Attack at level 7")
        assertEquals(listOf("CYNDAQUIL", "PIKACHU", "PIDGEY"), party.members.map { it.nickname.uppercase() })
        assertTrue(party.members.all { it.currentHp == it.maxHp && it.maxHp > 0 && !it.isEgg && it.ivs.all { v -> v in 0..31 } })
        assertTrue(party.slots.drop(3).all { it.isEmpty })
        // unedited: the party and each Pokemon write back to the same bytes, and so does the save
        val general = save.current(BlockKind.GENERAL)!!
        assertContentEquals(save.chunk(general, SaveChunks.byId(SaveChunks.PARTY)), party.toBytes(), "the party chunk writes back as it was")
        assertContentEquals(file, (save.withParty(party) as FoxResult.Ok).value.toFileBytes())

        // the stat formula gives the stats the game stored for each Pokemon (base stats of Cyndaquil, Pikachu and Pidgey)
        val bases = listOf(listOf(39, 52, 43, 65, 60, 50), listOf(35, 55, 30, 90, 50, 40), listOf(40, 45, 40, 56, 35, 35))
        party.members.forEachIndexed { i, mon ->
            val worked = mon.recalculated(bases[i])
            assertEquals(listOf(mon.maxHp) + mon.stats, listOf(worked.maxHp) + worked.stats, "stats of slot $i")
            assertEquals(mon.currentHp, worked.currentHp)
        }
        val leveled = party.members[1].withLevel(50).recalculated(bases[1])
        assertTrue(leveled.maxHp > party.members[1].maxHp && leveled.currentHp == leveled.maxHp, "a higher level raises the maximum and the current HP with it")

        // edits survive a write and a read, with the checksum kept right
        val edited = party.with(1, party.members[1].withLevel(50).withExperience(125_000).withMove(0, 85, 15).withIv(0, 31).withHeldItem(0)).without(0)
        val changed = (save.withParty(edited) as FoxResult.Ok).value
        val again = (PartyData.read(changed) as FoxResult.Ok).value
        assertEquals(listOf(25, 16), again.members.map { it.species })
        assertEquals(listOf(50, 7), again.members.map { it.level })
        again.members[0].let {
            assertEquals(85, it.moves[0]); assertEquals(125_000L, it.experience); assertEquals(31, it.ivs[0]); assertEquals(0, it.heldItem)
        }
        assertEquals(2, again.count)
        assertTrue(again.slots.drop(2).all { it.isEmpty })
        assertEquals(party.members[1].personality, again.members[0].personality, "the personality value, which gives the nature, is left alone")

        val named = (party.members[0].withNickname("EMBER") as FoxResult.Ok).value
        assertEquals("EMBER", named.nickname)
        assertTrue(named.hasNickname)
        assertEquals("CYNDAQUIL", (Pokemon.parse(named.withSpecies(155).toBytes()) as FoxResult.Ok).value.let { (it.withNickname("") as FoxResult.Ok).value.nickname.uppercase().ifEmpty { "CYNDAQUIL" } })
        // a changed byte makes the checksum fail
        val raw = save.chunk(general, SaveChunks.byId(SaveChunks.PARTY))
        raw[8 + 20] = (raw[8 + 20] + 1).toByte()
        assertTrue(PartyData.parse(raw) is FoxResult.Err)
    }
    @Test
    fun theBagOfTheRealSaveReadsAndWritesBack() {
        val dir = Path.of("build", "emulator-saves")
        val files = names.map { dir.resolve("$it.dsv") }.filter { Files.exists(it) }
        if (files.isEmpty()) { println("SKIPPED: no saves in ${dir.toAbsolutePath()}"); return }
        for (path in files) {
            val file = Files.readAllBytes(path)
            val save = (SaveFile.parse(file) as FoxResult.Ok).value
            val bag = (BagData.read(save) as? FoxResult.Ok)?.value ?: error("$path: no bag")
            println("BAG ${path.fileName}: " + BagPocket.entries.joinToString { p -> "${p.label}=" + bag.items(p).joinToString("/") { "${it.id}x${it.quantity}" } } + " registered=${bag.registered}")
            for (pocket in BagPocket.entries) {
                // used slots come first, then only empty ones
                val used = bag.items(pocket)
                assertTrue(used.all { it.quantity in 1..pocket.maxQuantity && it.id in 1..BagData.MAX_ITEM }, "$path ${pocket.label} holds sane slots")
                val raw = bag.slots.subList(pocket.start, pocket.start + pocket.capacity)
                assertEquals(used + List(pocket.capacity - used.size) { BagSlot.EMPTY }, raw, "$path ${pocket.label}: used slots first")
            }
            val general = save.current(BlockKind.GENERAL)!!
            assertContentEquals(save.chunk(general, SaveChunks.byId(SaveChunks.BAG)), bag.toBytes(), "$path: the bag chunk writes back as it was")
            assertContentEquals(file, (save.withBag(bag) as FoxResult.Ok).value.toFileBytes(), "$path: writing the bag back changes no byte")

            val edited = (bag.adding(BagPocket.BALLS, 1, 5) as FoxResult.Ok).value.let { (it.adding(BagPocket.MEDICINE, 17, 99) as FoxResult.Ok).value }
            val again = (BagData.read((save.withBag(edited) as FoxResult.Ok).value) as FoxResult.Ok).value
            assertEquals(edited, again)
            assertEquals(bag.count(1) + 5, again.count(1))
            assertEquals(bag.count(17) + 99, again.count(17))
            assertTrue((save.withBag(edited) as FoxResult.Ok).value.let { it.block(1, BlockKind.GENERAL).valid })
        }
    }
}
