package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.encounter.EncounterData
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits wild encounters through the view model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class EncounterEditorEndToEndTest {
    @Test
    fun soulSilverDataCanBeCopiedIntoHeartGoldAndSurvivesABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val model = EncounterEditorModel(project, onChanged = app::projectChanged)
            assertEquals("SoulSilver", model.otherName)
            val differing = model.differingAreas()
            assertTrue(differing > 10)

            // One area first: Route 30, then everything.
            val route30 = model.editor.rows().first { model.editor.label(it).contains("Route 30") }
            model.editor.select(route30)
            val ledyba = model.names!!.species.indexOf("LEDYBA")
            assertFalse(model.editor.records[route30].let { r -> ledyba in r.landMorning + r.landDay + r.landNight })
            model.copySelectedFromOther()
            assertTrue(model.editor.records[route30].let { r -> ledyba in r.landMorning + r.landDay + r.landNight })
            assertFalse(model.differsFromOther(route30))
            assertEquals(differing - 1, model.differingAreas())

            assertEquals(differing - 1, model.copyAllFromOther())
            assertEquals(0, model.differingAreas())
            assertEquals(0, model.copyAllFromOther(), "nothing left to copy")

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                // The HeartGold table now equals the SoulSilver one, which itself was not touched.
                assertContentEquals(reopened.read("a/1/3/6"), reopened.read("a/0/3/7"))
                assertContentEquals(project.rom.read("a/1/3/6"), reopened.read("a/1/3/6"))
                val hg = (EncounterData.loadHeartGold(reopened) as FoxResult.Ok).value
                assertEquals(142, hg.records.size)
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
