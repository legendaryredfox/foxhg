package io.github.romulofer.foxhg.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.romulofer.foxhg.app.ui.Loading
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The loading screen: shown while a screen is built in the background, with a ring that keeps turning. */
@OptIn(ExperimentalTestApi::class)
class LoadingUiEndToEndTest {
    @Test
    fun theLoadingScreenShowsUntilTheWorkIsDoneAndThenTheContent() = runDesktopComposeUiTest(800, 600) {
        val release = CountDownLatch(1)
        val built = ArrayList<Thread>()
        setContent {
            io.github.romulofer.foxhg.app.ui.FoxTheme {
                Loading("key", "the slow screen", { built += Thread.currentThread(); release.await(); 42 }) { Text("Ready: $it") }
            }
        }
        waitUntil(timeoutMillis = 10_000) { built.isNotEmpty() }
        onNodeWithTag("loading").assertIsDisplayed()
        onNodeWithText("Loading the slow screen…").assertIsDisplayed()
        // the interface still answers while the work runs: the work is not on the UI thread
        assertEquals(false, built.single().name.contains("AWT-EventQueue"), "the work runs in the background")
        release.countDown()
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithText("Ready: 42").assertIsDisplayed() }.isSuccess }
        onNodeWithTag("loading").assertDoesNotExist()
    }

    @Test
    fun openingAScreenWithARomShowsLoadingThenTheEditor() {
        if (!romAvailable()) return
        val model = testModel()
        model.openRom(romPath)
        try {
            runApp(model) {
                open("Maps")
                onNodeWithTag("nav:Maps").assertIsDisplayed()
                assertTrue(onAllNodesWithText("New Bark Town", substring = true).fetchSemanticsNodes().isNotEmpty(), "the maps list is there")
                onNodeWithTag("loading").assertDoesNotExist()
            }
        } finally {
            model.close()
        }
    }
}
