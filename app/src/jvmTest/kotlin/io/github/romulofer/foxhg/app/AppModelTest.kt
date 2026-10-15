package io.github.romulofer.foxhg.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppModelTest {
    private fun withFile(bytes: ByteArray, block: (Path) -> Unit) {
        val file = tempFile("foxhg", ".nds")
        try {
            Files.write(file, bytes)
            block(file)
        } finally {
            Files.delete(file)
        }
    }

    private fun headerWith(code: String) = ByteArray(0x200).also { b -> code.forEachIndexed { i, c -> b[0x0C + i] = c.code.toByte() } }

    @Test
    fun rejectsFileThatIsNotARom() = withFile(ByteArray(32)) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.header)
        assertNull(model.project)
        assertNotNull(model.error)
    }

    @Test
    fun rejectsUnsupportedGames() = withFile(headerWith("BPEE")) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.project)
        assertTrue(model.error!!.contains("not supported"), model.error)
    }

    @Test
    fun rejectsARomWithoutAFileSystem() = withFile(headerWith("IPKE")) { file ->
        val model = testModel()
        model.openRom(file)
        assertNull(model.project, "a header alone is not a usable ROM")
        assertNotNull(model.error)
        model.close()
    }

    @Test
    fun quittingDuringATaskWaitsInTheBackgroundThenExits() {
        val model = testModel()
        val release = java.util.concurrent.CountDownLatch(1)
        model.startTask("Slow job") { release.await(10, java.util.concurrent.TimeUnit.SECONDS) }
        val exited = java.util.concurrent.CountDownLatch(1)
        val started = System.nanoTime()
        Actions.quit(model) { exited.countDown() }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1000, "quit returns at once instead of freezing the window")
        assertTrue(exited.count == 1L, "the window stays while the task runs")
        release.countDown()
        assertTrue(exited.await(10, java.util.concurrent.TimeUnit.SECONDS), "it exits once the task ends")
    }

    @Test
    fun quittingWithNothingRunningExitsAtOnce() {
        var exited = false
        Actions.quit(testModel()) { exited = true }
        assertTrue(exited)
    }
}
