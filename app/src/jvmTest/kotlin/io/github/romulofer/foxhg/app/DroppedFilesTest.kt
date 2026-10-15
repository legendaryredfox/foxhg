package io.github.romulofer.foxhg.app

import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What dropping files on the window does ([Actions.openDropped]). The drag itself needs a real window system. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class DroppedFilesTest {
    @Test
    fun aRomOpensAndAProjectFolderOpensAsAProject() {
        if (!romAvailable()) return
        val dir = tempDir("foxhg-drop")
        try {
            val model = testModel()
            Actions.openDropped(model, listOf(romPath))
            assertNotNull(model.project)
            assertNull(model.projectDir, "a dropped ROM opens without a project")

            val projectDir = dir.resolve("hack")
            assertTrue(testModel().also { assertTrue(it.newProject(romPath, projectDir)); it.close() }.project != null)
            Actions.openDropped(model, listOf(projectDir))
            assertEquals(projectDir.toAbsolutePath(), model.projectDir?.toAbsolutePath())
            model.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun otherDropsAreRefusedWithAMessage() {
        val dir = tempDir("foxhg-drop")
        try {
            val model = testModel()
            val text = Files.writeString(dir.resolve("notes.txt"), "x")
            Actions.openDropped(model, listOf(text))
            assertTrue(model.error!!.contains("notes.txt"))
            assertNull(model.project)
            model.error = null
            Actions.openDropped(model, listOf(dir))
            assertTrue(model.error!!.contains("not a foxhg project folder"))
            model.error = null
            Actions.openDropped(model, emptyList())
            assertNull(model.error)
        } finally {
            dir.deleteRecursively()
        }
    }
}
