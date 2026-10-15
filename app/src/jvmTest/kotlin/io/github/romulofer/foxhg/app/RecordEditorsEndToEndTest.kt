package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.evolution.EvolutionData
import io.github.romulofer.foxhg.core.data.evolution.EvolutionMethod
import io.github.romulofer.foxhg.core.data.evolution.TradeEvolutions
import io.github.romulofer.foxhg.core.data.learnset.Learnset
import io.github.romulofer.foxhg.core.data.learnset.LearnsetEntry
import io.github.romulofer.foxhg.core.data.move.MoveData
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits through the generic editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class RecordEditorsEndToEndTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun learnsetEvolutionAndMoveEditsSurviveABuild() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val app = testModel()
        val built = Files.createTempFile("foxhg-e2e", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val labels = RecordEditorModel.speciesLabel(project.names)

            val learnsets = RecordEditorModel(project, Learnset.SPEC, labels, app::projectChanged, firstIndex = 1)
            learnsets.select(25) // Pikachu
            val before = learnsets.selected!!.entries.size
            learnsets.update { it.copy(entries = it.entries + LearnsetEntry(move = 85, level = 5)).sorted() }
            assertEquals(before + 1, learnsets.selected!!.entries.size)
            assertTrue(learnsets.isEdited(25) && !learnsets.isEdited(24))

            val evolutions = RecordEditorModel(project, EvolutionData.SPEC, labels, app::projectChanged, firstIndex = 1)
            assertTrue(evolutions.records.any { r -> r.slots.any { it.known?.isTrade == true } })
            evolutions.updateAll { TradeEvolutions.convert(it, 36).first }
            assertFalse(evolutions.records.any { r -> r.slots.any { it.known?.isTrade == true } })
            assertEquals(EvolutionMethod.LEVEL.id, evolutions.records[64].slots[0].method) // Kadabra

            val moves = RecordEditorModel(project, MoveData.SPEC, RecordEditorModel.moveLabel(project.names), app::projectChanged, firstIndex = 1)
            moves.update { it.copy(power = 90, priority = -3) }

            app.build(built)
            assertNull(app.error, app.error)

            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val l = (Learnset.load(reopened) as FoxResult.Ok).value.records
                assertTrue(LearnsetEntry(85, 5) in l[25].entries)
                assertEquals(l[25].entries.map { it.level }, l[25].entries.map { it.level }.sorted())
                val e = (EvolutionData.load(reopened) as FoxResult.Ok).value.records
                assertEquals(36, e[64].slots[0].param)
                assertEquals(65, e[64].slots[0].target)
                val m = (MoveData.load(reopened) as FoxResult.Ok).value.records
                assertEquals(90, m[1].power)
                assertEquals(-3, m[1].priority)
                assertEquals(35, m[1].pp, "untouched fields stay")
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
