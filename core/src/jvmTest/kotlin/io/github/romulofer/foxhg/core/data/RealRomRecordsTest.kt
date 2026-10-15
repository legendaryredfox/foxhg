package io.github.romulofer.foxhg.core.data

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves
import io.github.romulofer.foxhg.core.data.evolution.EvolutionData
import io.github.romulofer.foxhg.core.data.evolution.EvolutionMethod
import io.github.romulofer.foxhg.core.data.evolution.TradeEvolutions
import io.github.romulofer.foxhg.core.data.learnset.Learnset
import io.github.romulofer.foxhg.core.data.learnset.LearnsetEntry
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.data.move.MoveData
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
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
}
