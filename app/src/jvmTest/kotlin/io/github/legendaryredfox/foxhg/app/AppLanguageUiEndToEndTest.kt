package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals

/** Choosing the app language in the settings window changes the words on screen at once (docs/spec.md section 12). */
@OptIn(ExperimentalTestApi::class)
class AppLanguageUiEndToEndTest {
    private fun androidx.compose.ui.test.DesktopComposeUiTest.chooseLanguage(model: AppModel, language: AppLanguage) {
        model.dialog = AppDialog.Settings()
        waitForIdle()
        onNodeWithTag("language:${language.tag}").performClick()
        onNodeWithTag("settings-save").performClick()
        waitForIdle()
        assertEquals(language, model.settings.language)
    }

    @Test
    fun theSettingsWindowSwitchesTheLanguage() {
        val model = AppModel(Recents(tempDir("foxhg-recents").resolve("r.txt")), ManualScheduler().scheduler, settingsStore = testSettings())
        runApp(model) {
            model.dialog = AppDialog.Settings()
            waitForIdle()
            onNodeWithText("Settings").assertIsDisplayed()
            onNodeWithTag("settings-save").performClick()
            waitForIdle()
            chooseLanguage(model, AppLanguage.PORTUGUESE_BR)
            model.dialog = AppDialog.Settings()
            waitForIdle()
            onNodeWithText("Configurações").assertIsDisplayed()
            onNodeWithTag("settings-save").performClick()
            waitForIdle()
            chooseLanguage(model, AppLanguage.CHINESE_SIMPLIFIED)
            model.dialog = AppDialog.Settings()
            waitForIdle()
            onNodeWithText("设置").assertIsDisplayed()
            onNodeWithText("此翻译尚未经母语人士审阅。").assertIsDisplayed()
            onNodeWithTag("settings-save").performClick()
            waitForIdle()
            onNodeWithText("开始一个项目").assertIsDisplayed()
            onNodeWithText("新建项目…").assertIsDisplayed()
            model.dialog = AppDialog.NewSave
            waitForIdle()
            onNodeWithText("新建存档文件").assertIsDisplayed()
        }
    }

    @Test
    fun theSidebarShowsTheEditorNamesInTheChosenLanguageAndTheTagsStayTheSame() = withHeartGoldApp { model, _ ->
        onNodeWithTag("nav:Items").assertIsDisplayed()
        onNodeWithText("Items").assertIsDisplayed()
        chooseLanguage(model, AppLanguage.SPANISH)
        onNodeWithTag("nav:Items").assertIsDisplayed()
        onNodeWithText("Objetos").assertIsDisplayed()
        chooseLanguage(model, AppLanguage.CHINESE_SIMPLIFIED)
        onNodeWithText("道具").assertIsDisplayed()
        open("Items")
        assertEquals(EditorKind.ITEMS, model.selected, "the editor opens by its tag whatever the language")
    }
}
