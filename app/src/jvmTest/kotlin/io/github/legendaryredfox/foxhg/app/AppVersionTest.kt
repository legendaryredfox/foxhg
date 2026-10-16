package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppVersionTest {
    @Test
    fun theWindowShowsTheVersionTheBuildWroteIntoTheResources() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(AppVersion.current), "version was '${AppVersion.current}'")
        val model = testModel()
        try {
            runApp(model) {
                onNodeWithTag("app-version").assertIsDisplayed().assertTextEquals("v${AppVersion.current}")
            }
        } finally {
            model.close()
        }
    }
}
