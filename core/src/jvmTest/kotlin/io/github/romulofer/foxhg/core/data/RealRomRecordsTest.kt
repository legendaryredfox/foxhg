package io.github.romulofer.foxhg.core.data

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.evolution.EvolutionData
import io.github.romulofer.foxhg.core.data.evolution.EvolutionMethod
import io.github.romulofer.foxhg.core.data.evolution.TradeEvolutions
import io.github.romulofer.foxhg.core.data.learnset.Learnset
import io.github.romulofer.foxhg.core.data.learnset.LearnsetEntry
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.graphics.Icons
import io.github.romulofer.foxhg.core.sound.Sounds
import io.github.romulofer.foxhg.core.data.map.Collision
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsages
import io.github.romulofer.foxhg.core.data.map.TileTypes
import io.github.romulofer.foxhg.core.data.move.MoveData
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.romulofer.foxhg.core.script.ScriptEntry
import io.github.romulofer.foxhg.core.script.ScriptText
import io.github.romulofer.foxhg.core.text.TextArchive
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassPrizes
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomRecordsTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((RomProject.open(source) as FoxResult.Ok).value) }
    }

    private fun <T> NarcRecords<T>.assertNarcRoundTrips(project: RomProject) {
        assertContentEquals(project.read(path), toNarc().toBytes(), "$path must re-serialize unchanged")
    }

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun learnsetsRoundTripAndMatchTheGame() = withProject { project ->
        val table = Learnset.load(project).ok()
        println("learnsets=${table.records.size} longest=${table.records.maxOf { it.entries.size }}")
        table.assertNarcRoundTrips(project)
        // Bulbasaur: Tackle (33) at level 1, Growl (45) at level 3.
        assertEquals(listOf(LearnsetEntry(33, 1), LearnsetEntry(45, 3)), table.records[1].entries.take(2))
        assertTrue(table.records.size >= 496)
    }

    @Test
    fun evolutionsRoundTripAndMatchTheGame() = withProject { project ->
        val table = EvolutionData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val bulbasaur = table.records[1].slots[0]
        assertEquals(listOf(EvolutionMethod.LEVEL.id, 16, 2), listOf(bulbasaur.method, bulbasaur.param, bulbasaur.target))
        assertEquals(EvolutionMethod.TRADE.id, table.records[64].slots[0].method) // Kadabra -> Alakazam
        assertEquals(65, table.records[64].slots[0].target)
        val unknown = table.records.flatMap { it.slots }.filter { it.method != 0 && it.known == null }
        assertEquals(emptyList(), unknown, "every method in the ROM should be a known one")

        val (converted, changes) = TradeEvolutions.convert(table.records, 36)
        println("trade evolutions converted=${changes.size}")
        assertTrue(changes.size >= 10)
        assertTrue(converted.none { r -> r.slots.any { it.known?.isTrade == true } })
    }

    @Test
    fun movesRoundTripAndMatchTheGame() = withProject { project ->
        val table = MoveData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val pound = table.records[1]
        assertEquals(listOf(0, 40, 0, 100, 35), listOf(pound.category, pound.power, pound.type, pound.accuracy, pound.pp))
        assertEquals(471, table.records.size, "3 more records than the 468 named moves")
    }

    @Test
    fun itemsRoundTripAndMatchTheGame() = withProject { project ->
        val table = ItemData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val pokeBall = table.records[4]
        assertEquals(200, pokeBall.price)
        assertEquals(1, pokeBall.battleUseFunc, "Poké Ball is a ball")
        assertEquals(ItemData.SIZE, table.records.first().toBytes().size)
    }

    @Test
    fun eggMovesRoundTrip() = withProject { project ->
        val table = EggMoves.load(project).ok()
        table.assertNarcRoundTrips(project)
        assertEquals(1, table.records.size)
        val eggs = table.records.single()
        assertEquals(198, eggs.groups.size)
        assertEquals(listOf(113, 130, 219, 204, 80, 345, 320, 174, 275, 267, 133), eggs.groups.first().moves.take(11))
        assertTrue(!eggs.overLimit)
        val first = eggs.groups.first()
        assertEquals(eggs, eggs.withMoves(first.species, first.moves))
        assertEquals(eggs.groups.size - 1, eggs.withMoves(first.species, emptyList()).groups.size)
    }

    @Test
    fun tmHmTableMatchesTheGameAndRoundTrips() = withProject { project ->
        val table = TmHmTable.load(project).ok()
        assertEquals(100, table.moves.size)
        val moves = project.names!!.moves
        assertEquals("Focus Punch", moves[table.moves[0]], "TM01")
        assertEquals("Cut", moves[table.moves[92]], "HM01")
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain), "unchanged table must not touch the ARM9")
        TmHmTable.save(project, table.with(0, 1)).ok()
        assertEquals(1, TmHmTable.load(project).ok().moves[0])
        assertTrue(project.editedArm9 != null)
    }

    @Test
    fun moveAndItemCsvRoundTripThroughTheRom() = withProject { project ->
        val moves = MoveData.load(project).ok().records
        val moveNames = project.names!!.moves
        val moveCsv = MoveData.CSV.export(moves) { moveNames.getOrElse(it) { "" } }
        assertEquals(moves, MoveData.CSV.import(moveCsv, moves).ok())
        val items = ItemData.load(project).ok().records
        val itemNames = project.names!!.items
        val itemCsv = ItemData.CSV.export(items) { itemNames.getOrElse(it) { "" } }
        assertEquals(items, ItemData.CSV.import(itemCsv, items).ok())
    }

    @Test
    fun trainerClassGendersMatchTheGame() = withProject { project ->
        val table = TrainerClassGenders.load(project).ok()
        val names = project.names!!.trainerClasses
        assertEquals(128, table.genders.size)
        assertEquals(listOf("Youngster" to 0, "Lass" to 1), listOf(2, 3).map { names[it] to table.genders[it] })
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain))
        TrainerClassGenders.save(project, table.with(2, 1)).ok()
        assertEquals(1, TrainerClassGenders.load(project).ok().genders[2])
    }

    @Test
    fun trainerClassPrizesMatchTheGameAndSurviveAnOverlayEdit() = withProject { project ->
        val table = TrainerClassPrizes.load(project).ok()
        assertEquals(129, table.entries.size)
        assertEquals(listOf(4, 40, 15), listOf(2, 32, 24).map { table.multiplierOf(it) }, "Youngster, Rich Boy, Ace Trainer")
        val plain = project.readOverlay(12).ok()
        assertContentEquals(plain, table.applyTo(plain))
        TrainerClassPrizes.save(project, table.withEntry(2, 9)).ok()
        assertEquals(9, TrainerClassPrizes.load(project).ok().entries[2].multiplier)
        assertEquals(setOf(12), project.editedOverlays)
    }

    @Test
    fun nameListsFollowMessageEdits() = withProject { project ->
        assertEquals("Great Ball", project.names!!.items[3])
        val table = TextArchive.load(project).ok()
        val renamed = table.records[222].withText(3, "Mega Ball").ok()
        table.with(222, renamed).save(project).ok()
        assertEquals("Mega Ball", project.names!!.items[3], "the lists are rebuilt after the text changed")
        project.revertAll()
        assertEquals("Great Ball", project.names!!.items[3], "and again after a revert")
    }

    @Test
    fun eventFilesRoundTripAndMatchTheGame() = withProject { project ->
        val table = EventFile.load(project).ok()
        table.assertNarcRoundTrips(project)
        val files = table.records
        assertEquals(491, files.size)
        assertEquals(7, files.count { it.isEmpty })
        assertEquals(listOf(1034, 2667, 1317, 195), listOf(files.sumOf { it.spawnables.size }, files.sumOf { it.overworlds.size }, files.sumOf { it.warps.size }, files.sumOf { it.triggers.size }))
        assertTrue(files.all { it.trailing.isEmpty() }, "no bytes after the last record in vanilla")
        assertEquals(10, files.flatMap { it.overworlds }.count { it.isAlias })
    }

    @Test
    fun scriptFilesRoundTripAndTheirTextToo() = withProject { project ->
        val table = ScriptEntry.load(project).ok()
        table.assertNarcRoundTrips(project)
        val scripts = table.records.filterIsInstance<ScriptEntry.Script>()
        assertEquals(965, table.records.size)
        assertEquals(492, scripts.size, "script files that parse and write back exactly")
        val levels = table.records.filterIsInstance<ScriptEntry.Level>()
        val raw = table.records.filterIsInstance<ScriptEntry.Raw>()
        assertEquals(468, levels.size, "every level script parses and writes back exactly")
        assertEquals(5, raw.size, "3 empty files and 2 odd script files stay raw bytes")
        for (s in scripts) {
            val back = ScriptText.parse(ScriptText.toText(s.file)).ok()
            assertEquals(s.file, back, "text form of a script file must read back to the same file")
        }
    }

    @Test
    fun mapFilesRoundTripAndMatchTheGame() = withProject { project ->
        val table = MapFile.load(project).ok()
        table.assertNarcRoundTrips(project)
        val maps = table.records
        assertEquals(676, maps.size)
        assertEquals(2859, maps.sumOf { it.buildings.size })
        assertTrue(maps.all { m -> m.buildings.all { it.width == 16L && it.height == 16L && it.length == 16L } }, "building scales are 16 in vanilla")
        assertEquals("Tall grass", TileTypes.info(2).label)
        assertTrue(TileTypes.info(16).surfable && TileTypes.info(2).encounters)
        val edited = maps[3].withTile(5, 7, type = 2, collision = 0x80)
        assertEquals(listOf(2, 0x80), listOf(edited.type(5, 7), edited.collision(5, 7)))
        assertEquals(maps[3].collision(7, 5), edited.collision(7, 5), "only the (5, 7) tile changed")
        assertContentEquals(MapFile.parse(edited.toBytes()).ok().toBytes(), edited.toBytes())
    }

    @Test
    fun mapPermissionsAreRowMajorAndEveryUsedMapHasAUsage() = withProject { project ->
        val maps = MapFile.load(project).ok().records
        val usages = MapUsages(project)
        val events = EventFile.load(project).ok().records
        var npcs = 0
        var walkable = 0
        maps.indices.forEach { map ->
            for (u in usages.of(map)) {
                val ev = events.getOrNull(usages.headers[u.header].eventFile) ?: continue
                for (o in ev.overworlds.filter { !it.isAlias && it.x / 32 == u.x && it.y / 32 == u.y }) {
                    npcs++
                    if (!Collision.isBlocked(maps[map].collision(o.x % 32, o.y % 32))) walkable++
                }
            }
        }
        assertTrue(npcs > 1000, "many NPCs sit on a map cell we can find, found $npcs")
        assertTrue(walkable * 100 / npcs >= 90, "NPCs stand on walkable tiles: $walkable of $npcs (row-major axes)")
    }

    @Test
    fun iconsDecodeToRealPictures() = withProject { project ->
        val icons = Icons(project)
        val bulbasaur = icons.pokemon(1)!!
        assertEquals(listOf(32, 32), listOf(bulbasaur.width, bulbasaur.height))
        assertTrue(bulbasaur.opaqueColors >= 4, "an icon has several colours, got ${bulbasaur.opaqueColors}")
        assertTrue(bulbasaur != icons.pokemon(4)!!, "different species have different icons")
        val pokeBall = icons.item(4)!!
        assertEquals(listOf(32, 32), listOf(pokeBall.width, pokeBall.height))
        assertTrue(pokeBall.opaqueColors >= 3)
        assertTrue((1..493).all { icons.pokemon(it) != null }, "every species has an icon")
        assertTrue((1..536).count { icons.item(it) != null } > 500, "nearly every item has an icon")
        // A Poké Ball is red above and white below.
        fun count(rows: IntRange, test: (Int) -> Boolean) = rows.sumOf { y -> (0 until 32).count { x -> pokeBall.argb[y * 32 + x].let { it ushr 24 != 0 && test(it) } } }
        assertTrue(count(4..14) { (it shr 16 and 0xFF) > (it and 0xFF) + 60 } > 20, "the upper half has red")
        assertTrue(count(17..28) { (it and 0xFF) > 150 && (it shr 16 and 0xFF) > 150 && (it shr 8 and 0xFF) > 150 } > 20, "the lower half has white")
    }

    @Test
    fun soundsRenderCriesEffectsAndMusic() = withProject { project ->
        val sounds = Sounds(project)
        assertEquals(2379, sounds.sdat!!.sequences.size)
        assertEquals("SEQ_PV001", sounds.name(1))
        assertEquals("SEQ_SE_DP_SELECT", sounds.name(1500))
        fun rms(c: io.github.romulofer.foxhg.core.sound.PcmClip) = Math.sqrt(c.samples.sumOf { it.toDouble() * it } / c.samples.size)
        val bulbasaur = sounds.cry(1)!!
        assertTrue(bulbasaur.seconds in 0.5..1.2 && rms(bulbasaur) > 500, "a cry is under a second and audible: ${bulbasaur.seconds}")
        assertTrue(bulbasaur.samples.toList() != sounds.cry(25)!!.samples.toList(), "cries differ per species")
        assertTrue((1..493).all { sounds.cry(it) != null }, "every species has a cry")
        val select = sounds.sequence(1500)!!
        assertTrue(select.seconds in 0.02..2.0 && rms(select) > 200, "a sound effect is short and audible: ${select.seconds}")
        val title = sounds.sequence(1004)!!
        assertTrue(title.seconds > 20 && rms(title) > 500, "music runs for a while and is audible: ${title.seconds}")
        assertEquals(select.samples.toList(), sounds.sequence(1500)!!.samples.toList(), "rendering is deterministic")
    }
}
