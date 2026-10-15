package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.matrix.GameMatrix
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits a matrix through the generic record model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class MatrixEditorEndToEndTest {
    @Test
    fun matrixEditsSurviveABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-matrix", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = RecordEditorModel(project, GameMatrix.SPEC, { "%03d".format(it) }, app::projectChanged, firstIndex = 0)
            editor.select(5)
            val before = editor.selected!!
            editor.update { it.withMap(0, 0, 1234).resized(it.width + 1, it.height) }
            assertTrue(editor.isEdited(5))
            assertEquals(1234, editor.selected!!.map(0, 0))
            assertEquals(GameMatrix.EMPTY, editor.selected!!.map(before.width, 0), "the new column starts empty")

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val m = (GameMatrix.load(reopened) as FoxResult.Ok).value.records
                assertEquals(1234, m[5].map(0, 0))
                assertEquals(before.width + 1, m[5].width)
                assertEquals(before.name, m[5].name)
                assertEquals(288, m.size)
                assertEquals(47, m[0].width, "other matrices are untouched")
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
