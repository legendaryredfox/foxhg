package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The progress window for background jobs. */
@OptIn(ExperimentalTestApi::class)
class ProgressUiEndToEndTest {
    @Test
    fun aRunningTaskShowsItsProgressAndGoesAwayWhenDone() {
        val model = testModel()
        val half = CountDownLatch(1)
        val release = CountDownLatch(1)
        runApp(model) {
            model.startTask("Slow job") { report ->
                report(0.5)
                half.countDown()
                release.await()
                report(1.0)
            }
            half.await()
            waitUntil(timeoutMillis = 10_000) { model.task?.fraction == 0.5 }
            waitForIdle()
            onNodeWithText("Slow job").assertIsDisplayed()
            onNodeWithText("50 %").assertIsDisplayed()
            onNodeWithTag("progress").assertIsDisplayed()
            model.startTask("A second job") { } // refused while one runs
            assertEquals("Slow job", model.task?.title)
            release.countDown()
            model.awaitTask()
            waitForIdle()
            assertNull(model.task)
            onNodeWithText("Slow job").assertDoesNotExist()
        }
    }

    @Test
    fun aFailingTaskIsReportedAndDoesNotLeaveTheWindowUp() {
        val model = testModel()
        runApp(model) {
            model.startTask("Doomed") { error("disk on fire") }
            model.awaitTask()
            waitForIdle()
            assertNull(model.task)
            assertTrue(model.error!!.contains("Doomed failed: disk on fire"), model.error)
        }
    }

    @Test
    fun buildingARomReportsProgressUpToTheEndAndWritesTheFile() {
        if (!romAvailable()) return
        val model = testModel()
        model.openRom(romPath)
        val target = tempFile("foxhg-progress", ".nds")
        try {
            val seen = ArrayList<Double>()
            model.build(target) { seen += it }
            assertTrue(seen.isNotEmpty() && seen.last() == 1.0, "ends at 1.0: ${seen.takeLast(3)}")
            assertTrue(seen.zipWithNext().all { (a, b) -> b >= a }, "never goes back")
            assertTrue(seen.size > 20, "many steps for a 128 MB ROM, ${seen.size}")
            assertTrue(Files.size(target) == Files.size(romPath), "the built ROM has the size of the original")
        } finally {
            Files.deleteIfExists(target)
            model.close()
        }
    }

    @Test
    fun theMatrixOverviewReportsItsProgressWhileTheMapsRender() {
        if (!romAvailable()) return
        val model = testModel()
        model.openRom(romPath)
        try {
            val matrix = (io.github.romulofer.foxhg.core.data.matrix.GameMatrix.load(model.project!!) as io.github.romulofer.foxhg.core.FoxResult.Ok).value.records[0]
            val reports = ArrayList<Pair<Int, Int>>()
            val picture = MapPictures(model.project!!).sceneOfMatrix(matrix) { done, total -> reports += done to total }
            assertEquals(47 * 17, reports.first().second)
            assertTrue(reports.size == 47 * 17 && reports.zipWithNext().all { (a, b) -> b.first == a.first + 1 }, "one report per cell, in order")
            assertEquals(47 * 64, picture.width, "two pixels per tile")
        } finally {
            model.close()
        }
    }

    @Test
    fun buildingOverAnExistingFileKeepsTheOldOneAsBak() {
        if (!romAvailable()) return
        val model = testModel()
        model.openRom(romPath)
        val dir = tempDir("foxhg-bak")
        val target = dir.resolve("hack.nds")
        try {
            val editor = PersonalEditorModel(model.project!!, onChanged = model::projectChanged)
            editor.select(25)
            editor.update { it.copy(baseHp = 11) }
            model.build(target)
            assertTrue(Files.exists(target) && !Files.exists(dir.resolve("hack.nds.bak")), "the first build has nothing to back up")
            val first = Files.readAllBytes(target)

            editor.update { it.copy(baseHp = 22) }
            model.build(target)
            assertTrue(model.status!!.contains("hack.nds.bak"), model.status)
            assertTrue(Files.readAllBytes(dir.resolve("hack.nds.bak")).contentEquals(first), "the .bak is the first build")
            assertTrue(!Files.readAllBytes(target).contentEquals(first), "the new file differs")

            // a build that cannot happen leaves the existing file alone
            model.build(romPath)
            assertTrue(model.error!!.contains("different file"))
            assertTrue(Files.exists(target))
        } finally {
            model.close()
        }
    }
}
