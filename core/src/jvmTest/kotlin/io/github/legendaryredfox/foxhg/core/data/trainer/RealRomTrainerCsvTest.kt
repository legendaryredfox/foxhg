package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The trainer CSV files against every trainer of the clean HeartGold ROM; passes with a notice when it is absent. */
class RealRomTrainerCsvTest {
    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withTrainers(block: (List<Trainer>) -> Unit) {
        val rom = TestRoms.heartGoldEnglish
        if (!Files.exists(rom)) {
            println("SKIPPED: no ROM at ${rom.toAbsolutePath()}")
            return
        }
        FileRomSource(rom).use { source -> block(TrainerTable.load(RomProject.open(source).ok()).ok().trainers) }
    }

    private val name = { i: Int -> "T$i" }

    @Test
    fun theTrainerHeadersRoundTripThroughTheCsv() = withTrainers { trainers ->
        val csv = TrainerCsv.exportTrainers(trainers, name) { "C$it" }
        assertEquals(trainers.size + 1, csv.trim().lines().size)
        assertEquals(trainers, TrainerCsv.importTrainers(csv, trainers).ok(), "an unedited export imports back as it was")
    }

    @Test
    fun theWholePartiesRoundTripThroughTheCsv() = withTrainers { trainers ->
        val csv = TrainerCsv.exportParties(trainers, name) { "S$it" }
        assertEquals(trainers.sumOf { it.party.size } + 1, csv.trim().lines().size)
        assertEquals(trainers, TrainerCsv.importParties(csv, trainers).ok())
    }

    @Test
    fun aValueChangedInTheFileChangesThatTrainerOnly() = withTrainers { trainers ->
        val id = trainers.indexOfFirst { it.party.size >= 2 && !it.header.hasHeldItems }
        val rows = TrainerCsv.exportParties(trainers, name) { "S$it" }.trim().lines().toMutableList()
        val at = rows.indexOfFirst { it.startsWith("$id,") }
        val fields = rows[at].split(",").toMutableList()
        val column = rows[0].split(",").indexOf("level")
        fields[column] = "99"
        rows[at] = fields.joinToString(",")
        val updated = TrainerCsv.importParties(rows.joinToString("\r\n"), trainers).ok()
        assertEquals(99, updated[id].party[0].level)
        assertEquals(trainers[id].party.drop(1), updated[id].party.drop(1))
        assertEquals(trainers.filterIndexed { i, _ -> i != id }, updated.filterIndexed { i, _ -> i != id })
    }

    @Test
    fun aHeldItemInTheFileTurnsTheTrainersItemFlagOn() = withTrainers { trainers ->
        val id = trainers.indexOfFirst { !it.header.hasHeldItems && it.party.isNotEmpty() }
        val rows = TrainerCsv.exportParties(trainers, name) { "S$it" }.trim().lines().toMutableList()
        val at = rows.indexOfFirst { it.startsWith("$id,") }
        val fields = rows[at].split(",").toMutableList()
        fields[rows[0].split(",").indexOf("item")] = "17"
        rows[at] = fields.joinToString(",")
        val updated = TrainerCsv.importParties(rows.joinToString("\r\n"), trainers).ok()
        assertTrue(updated[id].header.hasHeldItems)
        assertEquals(17, updated[id].party[0].item)
        assertEquals(TrainerMon.recordSize(updated[id].header.hasCustomMoves, true), updated[id].partyBytes().size / updated[id].party.size)
    }

    @Test
    fun aFilesRowsBecomeTheWholePartyAndABadFileChangesNothing() = withTrainers { trainers ->
        val id = trainers.indexOfFirst { it.party.size == 3 }
        val rows = TrainerCsv.exportParties(trainers, name) { "S$it" }.trim().lines()
        val mine = rows.filter { it.startsWith("$id,") }
        // drop the middle one: slots 1 and 3 are not a party
        val gap = (listOf(rows[0]) + mine[0] + mine[2]).joinToString("\r\n")
        val refused = TrainerCsv.importParties(gap, trainers)
        assertTrue(refused is FoxResult.Err && refused.message.contains("slots"), refused.toString())
        // renumbering them makes a party of two
        val two = (listOf(rows[0]) + mine[0] + mine[2].replace(",3,", ",2,")).joinToString("\r\n")
        val updated = TrainerCsv.importParties(two, trainers).ok()
        assertEquals(2, updated[id].party.size)
        assertEquals(2, updated[id].header.partySize)
        assertNotEquals(trainers[id], updated[id])
    }

    @Test
    fun badFilesAreRefusedWhole() = withTrainers { trainers ->
        val trainerCsv = TrainerCsv.exportTrainers(trainers, name) { "C$it" }
        assertTrue(TrainerCsv.importTrainers(trainerCsv.replace("ai_flags", "ai_flagz"), trainers) is FoxResult.Err)
        assertTrue(TrainerCsv.importTrainers("id,class\r\n${trainers.size},1\r\n", trainers) is FoxResult.Err, "an id that is not a trainer")
        assertTrue(TrainerCsv.importTrainers("id,class\r\n1,256\r\n", trainers) is FoxResult.Err, "a class that does not fit a byte")
        assertTrue(TrainerCsv.importTrainers("id,class\r\n1,5\r\n1,6\r\n", trainers) is FoxResult.Err, "a trainer twice")
        assertTrue(TrainerCsv.importParties("trainer,slot,species,level\r\n1,1,25,101\r\n", trainers) is FoxResult.Err, "level 101")
        assertTrue(TrainerCsv.importParties("trainer,slot,species,level\r\n1,7,25,5\r\n", trainers) is FoxResult.Err, "slot 7")
        assertTrue(TrainerCsv.importParties("trainer,slot,species,level\r\n1,1,25,x\r\n", trainers) is FoxResult.Err, "a level that is not a number")
        assertTrue(TrainerCsv.importParties("trainer,slot,species\r\n1,1,25\r\n", trainers) is FoxResult.Err, "no level column")
        val ok = TrainerCsv.importTrainers("id,class\r\n1,5\r\n", trainers).ok()
        assertEquals(5, ok[1].header.trainerClass)
        assertEquals(trainers[2], ok[2])
    }
}
