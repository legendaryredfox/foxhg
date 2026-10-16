package io.github.legendaryredfox.foxhg.app

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The whole path: open the real ROM, edit through the view model, build, reopen and read the change back. */
class PersonalEditorEndToEndTest {
    @Test
    fun editBuildAndReadBack() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e", ".nds")
        try {
            app.openRom(romPath)
            assertNull(app.error)
            val project = app.project!!
            assertFalse(app.isDirty)

            val editor = PersonalEditorModel(project, onChanged = app::projectChanged)
            assertEquals("025  PIKACHU", editor.label(25))
            editor.search = "pikach"
            assertEquals(listOf(25), editor.rows())

            editor.select(25)
            assertEquals(35, editor.selected!!.baseHp)
            editor.update { it.copy(baseHp = 200, catchRate = 3) }
            assertTrue(app.isDirty)
            assertTrue(editor.isEdited(25))
            assertFalse(editor.isEdited(1))

            app.build(built)
            assertNull(app.error, app.error)
            assertTrue(Files.size(built) >= Files.size(romPath))

            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val table = (PersonalTable.load(reopened) as FoxResult.Ok).value
                assertEquals(200, table.records[25].baseHp)
                assertEquals(3, table.records[25].catchRate)
                assertEquals(45, table.records[1].baseHp, "other species stay as they were")
            }
            // Editing the value back to the original makes the project clean again.
            editor.update { it.copy(baseHp = 35, catchRate = 190) }
            assertFalse(app.isDirty)
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
