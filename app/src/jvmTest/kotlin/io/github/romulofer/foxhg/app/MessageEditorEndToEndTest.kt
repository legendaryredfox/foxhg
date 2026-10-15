package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.TextBanks
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.text.TextArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits messages through the editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class MessageEditorEndToEndTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun messageEditsSurviveABuildAndBadDraftsAreNotWritten() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val app = testModel()
        val built = Files.createTempFile("foxhg-e2e-msg", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = MessageEditorModel(project, app::projectChanged)

            editor.selectBank(TextBanks.ITEMS)
            editor.selectMessage(3)
            assertEquals("Great Ball", editor.draft)
            assertFalse(editor.isMessageEdited(3))
            editor.edit("Mega Ball")
            assertNull(editor.draftError)
            assertTrue(editor.isMessageEdited(3) && editor.isBankEdited(TextBanks.ITEMS))

            editor.edit("Mega {NOPE} Ball")
            assertNotNull(editor.draftError, "an unknown command is reported")
            assertEquals("Mega Ball", editor.archive!!.entries[3].text(), "a bad draft is not written")
            editor.edit("Mega Ball")
            assertEquals("003  Mega Ball", RecordEditorModel.itemLabel(project.names)(3), "an editor opened now lists the new name")

            editor.selectBank(TextBanks.TRAINER_NAMES) // 9-bit packed bank
            editor.selectMessage(20)
            editor.edit("{PAUSE 5}Falk")
            assertNotNull(editor.draftError, "a packed message cannot hold commands")

            editor.selectBank(0)
            val withCommand = editor.archive!!.entries.indexOfFirst { it.text().contains("{") && !it.compressed }
            editor.selectMessage(withCommand)
            val original = editor.draft
            editor.edit(original + "!")
            assertEquals(original + "!", editor.archive!!.entries[withCommand].text())
            editor.revertMessage()
            assertEquals(original, editor.draft)
            assertFalse(editor.isMessageEdited(withCommand))

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val items = (TextArchive.load(reopened) as FoxResult.Ok).value.records[TextBanks.ITEMS]
                assertEquals("Mega Ball", items.entries[3].text())
                assertEquals("Poké Ball", items.entries[4].text())
                assertEquals(original, (TextArchive.load(reopened) as FoxResult.Ok).value.records[0].entries[withCommand].text())
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
