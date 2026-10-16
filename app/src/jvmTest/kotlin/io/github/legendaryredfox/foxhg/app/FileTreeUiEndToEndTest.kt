package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** The read-only file tree: browse folders, search, see size and bytes, edited marks, export. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class FileTreeUiEndToEndTest {
    @Test
    fun browsingSearchingAndExportingAFile() = withHeartGoldApp { model, project ->
        open("Files")
        onNodeWithText("${project.rom.files.size} files in the ROM", substring = true).assertIsDisplayed()
        onNodeWithTag("tree:a").performClick()
        onNodeWithTag("tree:a/0").performClick()
        onNodeWithTag("tree:a/0/1").performClick()
        waitForIdle()
        onNodeWithTag("tree:a/0/1/7").performClick()
        waitForIdle()
        onNodeWithText("a/0/1/7").assertIsDisplayed()
        val original = project.read("a/0/1/7")!!
        assertTrue(onNodeWithTag("hex-dump").editableOrText().startsWith("0000  "), "a hex dump of the file")
        assertTrue(onNodeWithTag("hex-dump").editableOrText().contains("%02X ".format(original[0].toInt() and 0xFF)))

        // an edit shows as edited, and the dump and export carry it
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement("4321")
        waitForIdle()
        open("Files")
        onNodeWithTag("search:Search paths…").performTextInput("a/0/1/7")
        waitForIdle()
        onNodeWithTag("tree:a/0/1/7").performClick()
        waitForIdle()
        onNodeWithText("edited").assertIsDisplayed()
        val exported = tempFile("foxhg-export", ".bin")
        try {
            withChoosers(file = { _, _, _ -> exported }) {
                onNodeWithTag("export-file").performClick()
                waitForIdle()
            }
            assertContentEquals(project.read("a/0/1/7"), Files.readAllBytes(exported), "the export is the edited file")
            assertTrue(Files.readAllBytes(exported).size == original.size)
            assertTrue((ItemData.load(project) as FoxResult.Ok).value.records[4].price == 4321)
            assertTrue(model.status!!.startsWith("Exported a/0/1/7"))
        } finally {
            Files.deleteIfExists(exported)
        }
    }

    @Test
    fun theHexDumpFormatsSixteenBytesALine() {
        val dump = hexDump(ByteArray(20) { (it + 60).toByte() })
        val lines = dump.lines()
        assertTrue(lines.size == 2)
        assertTrue(lines[0].startsWith("0000  3C 3D 3E"))
        assertTrue(lines[0].endsWith("<=>?@ABCDEFGHIJK"))
        assertTrue(lines[1].startsWith("0010  4C 4D 4E 4F "))
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.editableOrText(): String =
    fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""
