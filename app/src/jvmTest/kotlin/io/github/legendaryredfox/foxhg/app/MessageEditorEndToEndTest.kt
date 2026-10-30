package io.github.legendaryredfox.foxhg.app

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits messages through the editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class MessageEditorEndToEndTest {
    @Test
    fun messageEditsSurviveABuildAndBadDraftsAreNotWritten() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e-msg", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = MessageEditorModel(project, app::projectChanged)

            editor.banks.search = "great ball"
            val matches = editor.bankRows()
            assertTrue(HgssEnglishLayout.textBanks.items in matches, "a bank is found by the text of its messages")
            assertTrue(matches.size < editor.banks.recordCount, "other banks are filtered out")
            editor.selectBank(HgssEnglishLayout.textBanks.items)
            assertEquals("great ball", editor.messageSearch, "the bank search carries over to the message list")
            editor.banks.search = ""
            editor.selectBank(HgssEnglishLayout.textBanks.items)
            editor.selectMessage(3)
            assertEquals("Great Ball", editor.draft)
            assertFalse(editor.isMessageEdited(3))
            editor.edit("Mega Ball")
            assertNull(editor.draftError)
            editor.banks.search = "mega ball"
            assertTrue(HgssEnglishLayout.textBanks.items in editor.bankRows(), "the search sees edited text")
            editor.banks.search = ""
            assertTrue(editor.isMessageEdited(3) && editor.isBankEdited(HgssEnglishLayout.textBanks.items))

            editor.edit("Mega {NOPE} Ball")
            assertNotNull(editor.draftError, "an unknown command is reported")
            assertEquals("Mega Ball", editor.archive!!.entries[3].text(), "a bad draft is not written")
            editor.edit("Mega Ball")
            assertEquals("003  Mega Ball", RecordEditorModel.itemLabel(project.names)(3), "an editor opened now lists the new name")

            editor.selectBank(HgssEnglishLayout.textBanks.trainerNames) // 9-bit packed bank
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
                val items = (TextArchive.load(reopened) as FoxResult.Ok).value.records[HgssEnglishLayout.textBanks.items]
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
