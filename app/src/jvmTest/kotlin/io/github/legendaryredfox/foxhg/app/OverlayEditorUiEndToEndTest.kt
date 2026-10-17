package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.legendaryredfox.foxhg.core.FoxResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The overlay editor: the table, the bytes, export, replacing an overlay with a file of the same size, and revert. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class OverlayEditorUiEndToEndTest {
    @Test
    fun exportReplaceAndRevertAnOverlay() = withHeartGoldApp { model, project ->
        open("Overlays")
        onNodeWithText("${project.rom.arm9Overlays.size} overlays").assertIsDisplayed()
        onNodeWithTag("overlay:1").performClick()
        waitForIdle()
        val original = (project.readOverlay(1) as FoxResult.Ok).value
        val entry = project.rom.arm9Overlays.first { it.id == 1 }
        assertTrue(onNodeWithTag("overlay-hex").editableOrText().startsWith("0000  "))
        onNodeWithText("RAM address 0x%08X".format(entry.ramAddress), substring = true).assertIsDisplayed()

        val file = tempFile("foxhg-overlay", ".bin")
        try {
            withChoosers(file = { _, _, _ -> file }) {
                onNodeWithTag("export-overlay").performClick()
                waitForIdle()
            }
            assertContentEquals(original, Files.readAllBytes(file), "the export is the decompressed overlay")

            // a replacement of the same size becomes an edit that the build compresses again; the original bytes stay in the ROM
            val changed = original.copyOf().also { it[original.size - 1] = (it[original.size - 1] + 1).toByte() }
            Files.write(file, changed)
            withChoosers(file = { _, _, _ -> file }) {
                onNodeWithTag("import-overlay").performClick()
                waitForIdle()
            }
            assertContentEquals(changed, (project.readOverlay(1) as FoxResult.Ok).value)
            assertEquals(setOf(1), project.editedOverlays)
            onNodeWithText("edited").assertIsDisplayed()

            // a file of another size is refused and changes nothing
            Files.write(file, original.copyOf(original.size - 4))
            withChoosers(file = { _, _, _ -> file }) {
                onNodeWithTag("import-overlay").performClick()
                waitForIdle()
            }
            assertNotNull(model.error)
            assertContentEquals(changed, (project.readOverlay(1) as FoxResult.Ok).value)

            onNodeWithTag("revert-overlay").performClick()
            waitForIdle()
            assertContentEquals(original, (project.readOverlay(1) as FoxResult.Ok).value)
            assertEquals(emptySet(), project.editedOverlays)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
