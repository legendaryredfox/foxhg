package io.github.legendaryredfox.foxhg.app

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.project.ProjectStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Creating, autosaving, closing, reopening and relinking projects through the model. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ProjectLifecycleTest {
    private fun editPikachu(app: AppModel, hp: Int) {
        val editor = PersonalEditorModel(app.project!!, onChanged = app::projectChanged)
        editor.select(25)
        editor.update { it.copy(baseHp = hp) }
    }

    private fun pikachuHp(app: AppModel): Int =
        (PersonalTable.load(app.project!!) as FoxResult.Ok).value.records[25].baseHp

    @Test
    fun editsAreAutosavedAndComeBackWhenTheProjectIsReopened() {
        if (!romAvailable()) return
        val dir = tempDir("foxhg-lifecycle")
        try {
            val manual = ManualScheduler()
            val first = testModel(manual)
            val projectDir = dir.resolve("my-hack")
            assertTrue(first.newProject(romPath, projectDir))
            assertEquals("my-hack", first.projectName)
            assertFalse(first.hasUnsavedWork, "a fresh project has nothing to save")

            editPikachu(first, 222)
            assertTrue(first.hasUnsavedWork, "the edit is not on disk yet")
            assertTrue(manual.hasPending, "an autosave is scheduled")
            assertFalse(Files.exists(projectDir.resolve("files/a/0/0/2")))
            manual.runPending()
            assertFalse(first.hasUnsavedWork)
            assertTrue(Files.exists(projectDir.resolve("files/a/0/0/2")))
            assertEquals("Saved", first.status)

            // Several quick edits collapse into one pending save.
            editPikachu(first, 200)
            editPikachu(first, 201)
            first.close()
            assertFalse(first.hasUnsavedWork, "closing flushes the pending save")

            val second = testModel()
            assertTrue(second.openProject(projectDir))
            assertEquals(201, pikachuHp(second))
            assertEquals(projectDir.toAbsolutePath(), second.projectDir?.toAbsolutePath())
            assertTrue(second.recent.any { it.kind == RecentKind.PROJECT })
            second.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun withAutosaveOffNothingIsWrittenUntilSaveIsCalled() {
        if (!Files.exists(romPath)) return
        val dir = tempDir("foxhg-noauto")
        try {
            val manual = ManualScheduler()
            val app = testModel(manual)
            app.newProject(romPath, dir.resolve("p"))
            app.autosave = false
            editPikachu(app, 99)
            assertFalse(manual.hasPending)
            assertTrue(app.hasUnsavedWork)
            assertTrue(app.saveProject())
            assertFalse(app.hasUnsavedWork)
            app.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aBareRomSessionAsksBeforeDiscardingAndCanBecomeAProject() {
        if (!Files.exists(romPath)) return
        val dir = tempDir("foxhg-bare")
        try {
            val app = testModel()
            app.openRom(romPath)
            var ran = 0
            app.guard { ran++ }
            assertEquals(1, ran, "nothing to lose yet, so the action just runs")
            assertNull(app.dialog)

            editPikachu(app, 123)
            assertTrue(app.needsProjectToKeepWork)
            app.guard { ran++ }
            assertEquals(1, ran, "the action waits for the answer")
            val prompt = assertIs<AppDialog.UnsavedWork>(app.dialog)

            // "Save as project" from the prompt keeps the work, then the action may continue.
            assertTrue(app.saveProjectAs(dir.resolve("kept")))
            app.dialog = null
            prompt.then()
            assertEquals(2, ran)
            assertFalse(app.needsProjectToKeepWork)
            assertTrue(Files.exists(dir.resolve("kept/files/a/0/0/2")))
            assertEquals("kept", app.projectName)
            app.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aProjectWhoseRomWentMissingOffersToRelink() {
        val dir = tempDir("foxhg-missing")
        try {
            val projectDir = dir.resolve("p")
            assertIs<FoxResult.Ok<*>>(ProjectStore.create(projectDir, dir.resolve("gone.nds")))
            val app = testModel()
            assertFalse(app.openProject(projectDir))
            val dialog = assertIs<AppDialog.RelinkRom>(app.dialog)
            assertEquals(projectDir, dialog.projectDir)
            assertTrue(dialog.message.contains("no longer exists"))
            assertNull(app.project)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun newProjectRefusesAnExistingProjectAndBadNames() {
        val dir = tempDir("foxhg-names")
        try {
            assertNull(AppModel.projectFolder(dir, ""))
            assertNull(AppModel.projectFolder(dir, "a/b"))
            assertNull(AppModel.projectFolder(dir, ".."))
            assertEquals(dir.resolve("my hack"), AppModel.projectFolder(dir, "  my hack "))
            ProjectStore.create(dir.resolve("p"), dir.resolve("x.nds"))
            assertFalse(AppModel.isUsableProjectFolder(dir.resolve("p")))
            assertTrue(AppModel.isUsableProjectFolder(dir.resolve("fresh")))
            val app = testModel()
            assertFalse(app.newProject(dir.resolve("x.nds"), dir.resolve("p")))
            assertNotNull(app.error)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recentsRememberKindsAndIgnoreMissingPaths() {
        val dir = tempDir("foxhg-recents-test")
        try {
            val recents = Recents(dir.resolve("r.txt"))
            val rom = Files.createFile(dir.resolve("a.nds"))
            val proj = Files.createDirectory(dir.resolve("proj"))
            recents.add(RecentKind.ROM, rom)
            recents.add(RecentKind.PROJECT, proj)
            recents.add(RecentKind.ROM, rom)
            assertEquals(listOf(RecentKind.ROM, RecentKind.PROJECT), recents.load().map { it.kind }, "newest first, no duplicates")
            Files.delete(rom)
            assertEquals(listOf(RecentKind.PROJECT), recents.load().map { it.kind }, "paths that vanished are dropped")
            Files.writeString(dir.resolve("old.txt"), "${proj.toAbsolutePath()}\n")
            assertEquals(RecentKind.ROM, Recents(dir.resolve("old.txt")).load().single().kind, "a legacy line is a ROM")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theUndoHistoryIsKeptWhenTheProjectIsClosedAndReopened() {
        if (!romAvailable()) return
        val dir = tempDir("foxhg-history")
        try {
            val manual = ManualScheduler()
            val first = testModel(manual)
            val projectDir = dir.resolve("hack")
            assertTrue(first.newProject(romPath, projectDir))
            val original = pikachuHp(first)
            editPikachu(first, 111)
            manual.runPending() // autosave: the files are written, the history is not
            assertFalse(Files.exists(projectDir.resolve("history.bin")), "autosave leaves the (large) history out")
            first.project!!.clock = { 10_000L } // a later moment, so the second edit is a new step
            editPikachu(first, 222)
            first.close()
            assertTrue(Files.exists(projectDir.resolve("history.bin")), "closing writes it")

            val second = testModel()
            assertTrue(second.openProject(projectDir))
            assertEquals(222, pikachuHp(second))
            assertTrue(second.canUndo, "the history came back")
            second.undo()
            assertEquals(111, pikachuHp(second), "the last step is undone")
            second.undo()
            assertEquals(original, pikachuHp(second), "and the one before it")
            assertFalse(second.project!!.isDirty)
            second.redo()
            assertEquals(111, pikachuHp(second))
            second.saveProject() // an explicit save writes the history again
            second.close()

            val third = testModel()
            assertTrue(third.openProject(projectDir))
            assertEquals(111, pikachuHp(third))
            assertTrue(third.canRedo && third.canUndo, "undo and redo both survive a second round")

            // an edit made outside foxhg makes the history stale: it is dropped
            third.close()
            Files.delete(projectDir.resolve("files/a/0/0/2"))
            val fourth = testModel()
            assertTrue(fourth.openProject(projectDir))
            assertFalse(fourth.canUndo || fourth.canRedo, "the files no longer match the history")
            fourth.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
