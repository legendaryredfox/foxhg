package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomEncounterMusicTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block(RomProject.open(source).ok()) }
    }

    @Test
    fun anUneditedTableWritesBackTheSameArm9() = withProject { project ->
        val table = TrainerEncounterMusic.load(project).ok()
        assertEquals(HgssLayout.ENCOUNTER_MUSIC_VANILLA_ROWS, table.rows.size)
        TrainerEncounterMusic.save(project, table).ok()
        assertFalse(project.isDirty, "the same bytes are not an edit")
    }

    @Test
    fun aSongChangeStaysInPlaceAndUndoTakesItBack() = withProject { project ->
        val table = TrainerEncounterMusic.load(project).ok()
        val youngster = assertNotNull(table.rows.first { it.trainerClass == 2 })
        TrainerEncounterMusic.save(project, table.with(youngster.copy(first = 1113, second = 1112))).ok()
        val after = TrainerEncounterMusic.load(project).ok()
        assertEquals(table.rows.size, after.rows.size)
        assertEquals(EncounterMusicRow(2, 1113, 1112), after.of(2))
        assertEquals(table.rows.drop(1), after.rows.drop(1), "the other rows did not change")
        project.undo()
        assertFalse(project.isDirty)
        assertEquals(table, TrainerEncounterMusic.load(project).ok())
    }

    @Test
    fun aRowForAClassWithoutOneNeedsTheExpansionAndThenMovesTheTable() = withProject { project ->
        val table = TrainerEncounterMusic.load(project).ok()
        val without = (1..128).first { table.of(it) == null }
        assertTrue(TrainerEncounterMusic.save(project, table.with(EncounterMusicRow(without, 1113, 1113))) is FoxResult.Err, "no synthetic overlay yet")
        assertFalse(project.isDirty, "the refusal changed nothing")

        check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        TrainerEncounterMusic.save(project, table.with(EncounterMusicRow(without, 1113, 1113))).ok()
        val moved = TrainerEncounterMusic.load(project).ok()
        assertEquals(table.rows + EncounterMusicRow(without, 1113, 1113), moved.rows)
        // a moved table is edited where it is
        TrainerEncounterMusic.save(project, moved.with(EncounterMusicRow(without, 1112, 1112)).with(table.rows[0].copy(first = 1113))).ok()
        val edited = TrainerEncounterMusic.load(project).ok()
        assertEquals(EncounterMusicRow(without, 1112, 1112), edited.of(without))
        assertEquals(1113, edited.rows[0].first)
        assertNull(table.of(without))
    }
}
