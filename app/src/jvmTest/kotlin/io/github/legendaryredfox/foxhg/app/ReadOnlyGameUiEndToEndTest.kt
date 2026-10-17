package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull

/** A game foxhg has no editors for opens with the file browser only: no editor in the sidebar, Run and Build off, and the reason on screen. */
@OptIn(ExperimentalTestApi::class)
class ReadOnlyGameUiEndToEndTest {
    @Test
    fun aPlatinumRomOpensReadOnly() {
        val file = tempFile("foxhg-platinum", ".nds")
        try {
            Files.write(file, AppSyntheticRom.build(listOf("one.bin" to byteArrayOf(1, 2, 3)), gameCode = "CPUE"))
            val model = testModel()
            model.openRom(file)
            runApp(model) {
                onNodeWithTag("read-only").assertIsDisplayed()
                onNodeWithTag("read-only-reason").assertIsDisplayed()
                onNodeWithText("Files").assertIsDisplayed()
                onNodeWithText("Personal data").assertDoesNotExist()
                onNodeWithText("Scripts").assertDoesNotExist()
                onNodeWithText("Overlays").assertDoesNotExist()
                onNodeWithTag("run").assertIsNotEnabled()
                // an editor picked from the command line or an old selection still shows the explanation, not a broken screen
                model.selected = EditorKind.PERSONAL
                waitForIdle()
                onNodeWithTag("read-only-reason").assertIsDisplayed()
                model.selected = EditorKind.FILES
                waitForIdle()
                onNodeWithText("1 files in the ROM", substring = true).assertIsDisplayed()
            }
            assertNull(model.error)
            model.close()
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
