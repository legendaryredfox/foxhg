package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.trainer.TrainerTable
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits trainers through the view model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class TrainerEditorEndToEndTest {
    @Test
    fun trainerEditsSurviveABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val names = project.names!!
            val editor = TrainerEditorModel(project, onChanged = app::projectChanged)

            // Search finds trainers by name and by party member.
            editor.search = "falkner"
            val falkner = editor.rows().first() // several trainers share the name (rematches); the first is the gym fight
            editor.search = "pidgeotto"
            assertTrue(falkner in editor.rows())
            editor.search = ""

            val pidgey = names.species.indexOf("PIDGEY")
            val rattata = names.species.indexOf("RATTATA")
            val users = editor.trainers.sumOf { t -> t.party.count { it.species == pidgey } }
            assertTrue(users > 0)
            assertEquals(users, editor.replaceSpeciesEverywhere(pidgey, rattata))
            assertEquals(0, editor.replaceSpeciesEverywhere(pidgey, rattata), "nothing left to replace")
            assertTrue(editor.isEdited(falkner))

            editor.select(falkner)
            editor.updateMon(1) { it.copy(level = 40) }
            editor.addMon()
            assertEquals(3, editor.selected!!.party.size)
            editor.removeMon(2)
            assertEquals(2, editor.selected!!.party.size)

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val table = (TrainerTable.load(reopened) as FoxResult.Ok).value
                val party = table.trainers[falkner].party
                assertEquals(listOf(rattata, names.species.indexOf("PIDGEOTTO")), party.map { it.species })
                assertEquals(listOf(9, 40), party.map { it.level })
                assertFalse(table.trainers.any { t -> t.party.any { it.species == pidgey } })
                assertEquals(editor.trainers.size, table.trainers.size)
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
