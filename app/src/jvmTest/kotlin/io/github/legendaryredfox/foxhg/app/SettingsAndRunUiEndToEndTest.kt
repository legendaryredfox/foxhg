package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The settings window and the Run button, through the widgets. The emulator is replaced by a recorder. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class SettingsAndRunUiEndToEndTest {
    @Test
    fun runAsksForAnEmulatorThenBuildsAndStartsItAndTheSettingsPersist() {
        if (!romAvailable()) return
        val file = tempDir("foxhg-settings").resolve("settings.properties")
        val model = AppModel(Recents(tempDir("foxhg-recents").resolve("r.txt")), ManualScheduler().scheduler, settingsStore = SettingsStore(file, detectEmulator = { "" }))
        model.openRom(romPath)
        val started = ArrayList<List<String>>()
        val old = Emulator.launcher
        Emulator.launcher = { started += it; null }
        try {
            runApp(model) {
                onNodeWithTag("run").performClick()
                waitForIdle()
                onNodeWithText("Set the emulator command to use Run. Use {rom} where the ROM goes.").assertIsDisplayed()
                assertTrue(started.isEmpty(), "nothing starts without a command")

                onNodeWithText("Dark").performClick()
                onNodeWithTag("field:Emulator command (Run)").performTextInput("fake-emu --fast {rom}")
                onNodeWithTag("settings-save").performClick()
                waitForIdle()
                assertNull(model.dialog)
                assertEquals(ThemeChoice.DARK, model.settings.theme)
                assertTrue(Files.readString(file).contains("emulator=fake-emu --fast {rom}"), "the settings are on disk")

                onNodeWithTag("run").performClick()
                model.awaitTask()
                waitForIdle()
                assertEquals(1, started.size)
                val command = started.single()
                assertEquals(listOf("fake-emu", "--fast"), command.take(2))
                assertTrue(command.last().endsWith(".nds") && Files.size(java.nio.file.Path.of(command.last())) > 1_000_000, "it was given a built ROM: ${command.last()}")
                assertTrue(model.status!!.startsWith("Started the emulator"))
                assertNull(model.error)
            }
            assertEquals(ThemeChoice.DARK, SettingsStore(file).load().theme, "a new session reads them back")
        } finally {
            Emulator.launcher = old
            model.close()
        }
    }

    @Test
    fun aCommandThatCannotStartIsReported() {
        if (!romAvailable()) return
        val model = testModel()
        model.openRom(romPath)
        model.updateSettings(AppSettings(emulatorCommand = "no-such-emulator {rom}"))
        val old = Emulator.launcher
        try {
            Emulator.launcher = old
            model.run()
            model.awaitTask()
            assertNotNull(model.error)
            assertTrue(model.error!!.startsWith("Cannot start the emulator"), model.error)
        } finally {
            model.close()
        }
    }
}
