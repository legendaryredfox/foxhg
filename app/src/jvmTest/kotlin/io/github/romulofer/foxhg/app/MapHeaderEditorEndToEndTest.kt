package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits map headers through the view model on the real ROM (project, autosave, build, reopen). Skips without the ROM. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class MapHeaderEditorEndToEndTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun headerEditsAreSavedInTheProjectAndBuiltIntoTheRom() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val dir = Files.createTempDirectory("foxhg-headers")
        try {
            val manual = ManualScheduler()
            val app = testModel(manual)
            assertTrue(app.newProject(romPath, dir.resolve("p")))
            val editor = MapHeaderEditorModel(app.project!!, onChanged = app::projectChanged)

            editor.search = "route 29"
            val route29 = editor.rows().first()
            editor.select(route29)
            val before = editor.selected!!
            editor.update { it.withWeather(7).withPermission(MapHeader.Permission.BICYCLE, false).copy(musicDay = 1234) }
            assertTrue(editor.isEdited(route29))
            assertFalse(editor.isEdited(route29 + 1))

            // The ARM9 edit is autosaved into the project as arm9.bin.
            assertTrue(app.hasUnsavedWork)
            manual.runPending()
            assertTrue(Files.exists(dir.resolve("p/arm9.bin")), "the ARM9 edit is part of the project")

            val built = dir.resolve("built.nds")
            app.build(built)
            assertNull(app.error, app.error)
            app.close()

            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val headers = (MapHeaderTable.load(reopened) as FoxResult.Ok).value
                val h = headers[route29]
                assertEquals(7, h.weather)
                assertEquals(1234, h.musicDay)
                assertFalse(h.permissions and 1 != 0, "bicycle was switched off")
                assertEquals(before.eventFile, h.eventFile, "untouched fields stay")
                assertEquals(before.locationName, h.locationName)
            }

            // Reopening the project brings the edit back without building anything.
            val second = testModel()
            assertTrue(second.openProject(dir.resolve("p")))
            assertEquals(1234, MapHeaderEditorModel(second.project!!).all[route29].musicDay)
            second.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
