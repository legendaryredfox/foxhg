package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomTrainerTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun everyTrainerAndPartyRoundTripsAndFalknerIsRight() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val table = (TrainerTable.load(project) as? FoxResult.Ok)?.value ?: error((TrainerTable.load(project) as FoxResult.Err).message)
            println("trainers=${table.trainers.size} formats=${table.trainers.groupingBy { it.header.type }.eachCount()}")
            assertEquals(738, table.trainers.size)

            val headers = (Narc.parse(project.read(HgssLayout.TRAINER_DATA)!!) as FoxResult.Ok).value
            val parties = (Narc.parse(project.read(HgssLayout.TRAINER_PARTIES)!!) as FoxResult.Ok).value
            table.trainers.forEachIndexed { i, t ->
                assertContentEquals(headers.files[i], t.header.toBytes(), "header $i")
                assertContentEquals(parties.files[i], t.partyBytes(), "party $i")
            }

            val names = project.names!!
            val falkner = table.trainers.indices.first { names.trainerNames.getOrNull(it) == "Falkner" }
            val party = table.trainers[falkner].party.map { names.species[it.species] to it.level }
            println("Falkner (#$falkner): $party class=${names.trainerClasses[table.trainers[falkner].header.trainerClass]}")
            assertTrue(party.any { it.first == "PIDGEY" } && party.any { it.first == "PIDGEOTTO" })
        }
    }
}
