package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.ProjectStore
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The project life cycle through the window: the menu, the New project and Save as dialogs, the unsaved-work and relink
 * dialogs, recents, autosave, and Build ROM. File choosers are replaced by [withChoosers].
 */
@OptIn(ExperimentalTestApi::class, kotlin.io.path.ExperimentalPathApi::class)
class ProjectUiEndToEndTest {
    /** A model whose recents hold the test ROM and a project folder in [base], so dialogs offer both as defaults. */
    private fun modelWithRecents(base: Path, manual: ManualScheduler = ManualScheduler()): AppModel {
        val recents = Recents(base.resolve("recent.txt"))
        recents.add(RecentKind.ROM, romPath)
        recents.add(RecentKind.PROJECT, Files.createDirectories(base.resolve("projects/seed")))
        return AppModel(recents, manual.scheduler, settingsStore = testSettings())
    }

    private fun DesktopComposeUiTest.menu(item: String) {
        onNodeWithText("Project  ▾").performClick()
        waitForIdle()
        onNodeWithText(item).performClick()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.editItemPrice(price: Int) {
        open("Items")
        clickFirstText("Poké Ball")
        onNodeWithTag("number:Price").performTextReplacement(price.toString())
        waitForIdle()
    }

    private fun price(project: RomProject) = (ItemData.load(project) as FoxResult.Ok).value.records[4].price

    private fun withBase(block: (Path) -> Unit) {
        if (!romAvailable()) return
        val base = tempDir("foxhg-project-ui")
        try {
            block(base)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun newProjectDialogChecksTheNameThenCreatesAndOpensTheProject() = withBase { base ->
        val model = modelWithRecents(base)
        try {
            runApp(model) {
                onNodeWithText("Start a project").assertIsDisplayed()
                tap(onNodeWithText("New project…"), inScrollPane = false)
                onNodeWithText("Create project").assertIsEnabled()

                onNodeWithTag("field:Name").performTextReplacement("bad/name")
                waitForIdle()
                onNodeWithText("Use a name without slashes").assertIsDisplayed()
                onNodeWithText("Create project").assertIsNotEnabled()

                onNodeWithTag("field:Name").performTextReplacement("hack-one")
                waitForIdle()
                val folder = base.resolve("projects/hack-one")
                onNodeWithText("Creates $folder").assertIsDisplayed()
                tap(onNodeWithText("Create project"), inScrollPane = false)

                assertEquals(folder, model.projectDir)
                assertTrue(ProjectStore.isProject(folder))
                assertNull(model.dialog)
                onAllNodesWithText("hack-one").onFirst().assertIsDisplayed()
                onNodeWithText("Opened project hack-one").assertIsDisplayed()

                // the same name again is refused
                menu("New project…          Ctrl+N")
                onNodeWithTag("field:Name").performTextReplacement("hack-one")
                waitForIdle()
                onNodeWithText("A project already exists in that folder").assertIsDisplayed()
                onNodeWithText("Create project").assertIsNotEnabled()
                tap(onNodeWithText("Cancel"), inScrollPane = false)
                assertNull(model.dialog)
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun folderPickerChoosesExactlyTheFolderItShows() = withBase { base ->
        java.nio.file.Files.createDirectories(base.resolve("picked/inner"))
        val model = modelWithRecents(base)
        try {
            runApp(model) {
                tap(onNodeWithText("New project…"), inScrollPane = false)
                tap(onAllNodesWithText("Browse…").onFirst(), inScrollPane = false)
                onNodeWithTag("folder-path").performTextReplacement(base.toString())
                waitForIdle()
                tap(onNodeWithTag("folder:picked"), inScrollPane = false)
                onNodeWithText("Choosing: ${base.resolve("picked")}").assertIsDisplayed()
                tap(onNodeWithTag("folder:inner"), inScrollPane = false)
                tap(onNodeWithTag("folder-up"), inScrollPane = false)
                tap(onNodeWithTag("folder-choose"), inScrollPane = false)
                onNodeWithText("Creates ${base.resolve("picked/my-hack")}").assertIsDisplayed()
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun aRomSessionIsSavedAsAProjectThenAutosavesAndComesBack() = withBase { base ->
        val manual = ManualScheduler()
        val model = modelWithRecents(base, manual)
        val romTime = Files.getLastModifiedTime(romPath)
        val romSize = Files.size(romPath)
        try {
            model.openRom(romPath)
            runApp(model) {
                editItemPrice(321)
                // a bare ROM with edits: the chip offers to save, and nothing is written yet
                tap(onNodeWithText("● Not saved. Save as project…"), inScrollPane = false)
                onNodeWithText("Save as project").assertIsDisplayed()
                onNodeWithTag("field:Name").performTextReplacement("saved-hack")
                waitForIdle()
                tap(onNodeWithText("Save project"), inScrollPane = false)

                val folder = base.resolve("projects/saved-hack")
                assertEquals(folder, model.projectDir)
                assertFalse(model.hasUnsavedWork)
                onNodeWithText("✓ Saved").assertIsDisplayed()
                onNodeWithText("Saved project saved-hack").assertIsDisplayed()

                // autosave: the edit shows as saving until the timer fires
                editItemPrice(654)
                onNodeWithText("Saving…").assertIsDisplayed()
                manual.runPending()
                waitForIdle()
                onNodeWithText("✓ Saved").assertIsDisplayed()

                // autosave off: the edit stays unsaved until Save is chosen in the menu
                menu("✓ Autosave")
                editItemPrice(777)
                onNodeWithText("● Unsaved changes").assertIsDisplayed()
                assertFalse(manual.hasPending)
                menu("Save                         Ctrl+S")
                onNodeWithText("✓ Saved").assertIsDisplayed()
            }
            model.close()

            val second = testModel()
            assertTrue(second.openProject(base.resolve("projects/saved-hack")))
            assertEquals(777, price(second.project!!), "the project came back with the last edit")
            second.close()
            assertEquals(romTime, Files.getLastModifiedTime(romPath), "the ROM was not written")
            assertEquals(romSize, Files.size(romPath))
        } finally {
            model.close()
        }
    }

    @Test
    fun leavingARomSessionWithEditsAsksFirst() = withBase { base ->
        val model = modelWithRecents(base)
        try {
            model.openRom(romPath)
            runApp(model) {
                editItemPrice(321)
                withChoosers(file = { _, _, _ -> error("the chooser must not open while the question is open") }) {
                    menu("Open ROM without a project…")
                    onNodeWithText("Your changes are not saved").assertIsDisplayed()
                    tap(onNodeWithText("Cancel"), inScrollPane = false)
                    assertNull(model.dialog)
                    assertTrue(model.isDirty, "cancelling keeps the edits")
                }
                var asked = 0
                withChoosers(file = { _, _, _ -> asked++; romPath }) {
                    menu("Open ROM without a project…")
                    tap(onNodeWithText("Discard changes"), inScrollPane = false)
                }
                assertEquals(1, asked)
                assertFalse(model.isDirty, "the ROM was opened again with no edits")
                onNodeWithText("No changes").assertIsDisplayed()
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun aProjectWithAMissingRomIsRelinkedFromTheRecentsList() = withBase { base ->
        val project = base.resolve("projects/orphan")
        assertTrue(ProjectStore.create(project, base.resolve("gone.nds")) is FoxResult.Ok)
        val recents = Recents(base.resolve("recent.txt"))
        recents.add(RecentKind.PROJECT, project)
        val model = AppModel(recents, ManualScheduler().scheduler, settingsStore = testSettings())
        try {
            runApp(model) {
                clickFirstText("orphan")
                onNodeWithText("Can't find the ROM").assertIsDisplayed()
                assertNull(model.project)
                withChoosers(file = { _, _, _ -> romPath }) { tap(onNodeWithText("Locate ROM…"), inScrollPane = false) }
                assertEquals(project, model.projectDir)
                assertNull(model.dialog)
                assertNotNull(model.project)
                onNodeWithText("Opened project orphan").assertIsDisplayed()
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun openProjectAcceptsAProjectFolderAndRejectsAnythingElse() = withBase { base ->
        val project = base.resolve("projects/real")
        assertTrue(ProjectStore.create(project, romPath) is FoxResult.Ok)
        val notAProject = Files.createDirectories(base.resolve("plain"))
        val model = modelWithRecents(base)
        try {
            runApp(model) {
                withChoosers(directory = { _, _ -> notAProject }) { tap(onNodeWithText("Open project…"), inScrollPane = false) }
                onNodeWithText("$notAProject is not a foxhg project folder").assertIsDisplayed()
                assertNull(model.project)
                withChoosers(directory = { _, _ -> project }) { tap(onNodeWithText("Open project…"), inScrollPane = false) }
                assertEquals(project, model.projectDir)
                onNodeWithText("Opened project real").assertIsDisplayed()
            }
        } finally {
            model.close()
        }
    }

    @Test
    fun buildRomWritesAPatchedRomAndRefusesToOverwriteTheOriginal() = withBase { base ->
        val model = modelWithRecents(base)
        try {
            model.openRom(romPath)
            runApp(model) {
                editItemPrice(321)
                withChoosers(file = { _, _, _ -> romPath }) { tap(onNodeWithText("Build ROM…"), inScrollPane = false) }
                onNodeWithText("Choose a different file than the ROM you opened").assertIsDisplayed()

                val built = base.resolve("built.nds")
                withChoosers(file = { title, _, _ -> assertEquals("Build ROM", title); built }) { tap(onNodeWithText("Build ROM…"), inScrollPane = false) }
                onNodeWithText("Built $built").assertIsDisplayed()
                FileRomSource(built).use { source ->
                    assertEquals(321, price((RomProject.open(source) as FoxResult.Ok).value))
                }
            }
        } finally {
            model.close()
        }
    }
}
