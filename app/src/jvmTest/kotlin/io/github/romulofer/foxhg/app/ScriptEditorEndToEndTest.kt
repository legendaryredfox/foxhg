package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.script.ScriptEntry
import io.github.romulofer.foxhg.core.script.ScriptText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits a script as text through the editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class ScriptEditorEndToEndTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun scriptTextEditsSurviveABuildAndBadTextIsNotWritten() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val app = testModel()
        val built = Files.createTempFile("foxhg-e2e-script", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = ScriptEditorModel(project, ScriptLabels(project), app::projectChanged)
            val index = editor.files.records.indexOfFirst { it is ScriptEntry.Script && it.file.scriptCount >= 2 }
            editor.select(index)
            val original = editor.draft
            assertTrue(original.startsWith("script_1:"), original.take(40))
            assertTrue(!editor.files.isEdited(index))

            editor.edit(original.replaceFirst("\n    End\n", "\n    SetFlag 999\n    End\n"))
            assertNull(editor.draftError, editor.draftError)
            assertTrue(editor.files.isEdited(index))

            val good = (editor.files.selected as ScriptEntry.Script).file
            editor.edit(editor.draft + "\n    NotACommand 1\n")
            assertNotNull(editor.draftError, "an unknown command is reported")
            assertEquals(good, (editor.files.selected as ScriptEntry.Script).file, "bad text is not written")
            editor.edit(editor.draft.replace("    NotACommand 1\n", ""))
            assertNull(editor.draftError)

            val raw = editor.files.records.indexOfFirst { it is ScriptEntry.Raw }
            editor.select(raw)
            editor.edit("script_1:\n    End\n")
            assertTrue(editor.files.selected is ScriptEntry.Raw, "raw files are never rewritten from text")

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val entry = (ScriptEntry.load(reopened) as FoxResult.Ok).value.records[index] as ScriptEntry.Script
                assertTrue(ScriptText.toText(entry.file).contains("SetFlag 999"))
                val untouched = (ScriptEntry.load(reopened) as FoxResult.Ok).value.records[index + 1]
                assertEquals(untouched, (ScriptEntry.load(project) as FoxResult.Ok).value.records[index + 1])
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
