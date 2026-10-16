package io.github.legendaryredfox.foxhg.core.project

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.ByteArrayRomSource
import io.github.legendaryredfox.foxhg.core.testing.SyntheticRom
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ProjectStoreTest {
    @Test
    fun savedEditsComeBackAndRevertedOnesDoNot() {
        val dir = Files.createTempDirectory("foxhg-project")
        try {
            val romFile = dir.resolve("test.nds")
            Files.write(romFile, SyntheticRom.build(listOf("one" to ByteArray(100) { 1 }, "two" to ByteArray(300) { 2 })))
            val projectDir = dir.resolve("project")
            assertIs<FoxResult.Ok<*>>(ProjectStore.create(projectDir, romFile))

            val first = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            first.project.write("a/one", ByteArray(60) { 5 })
            first.project.write("a/two", ByteArray(10) { 6 })
            assertIs<FoxResult.Ok<Unit>>(ProjectStore.save(projectDir, first.project))
            first.source.close()

            val second = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            assertEquals(setOf("a/one", "a/two"), second.project.editedPaths)
            assertContentEquals(ByteArray(60) { 5 }, second.project.read("a/one"))
            second.project.revert("a/two")
            ProjectStore.save(projectDir, second.project)
            second.source.close()

            val third = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            assertEquals(setOf("a/one"), third.project.editedPaths)
            assertTrue(!Files.exists(projectDir.resolve("files/a/two")))
            third.source.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsADirectoryThatIsNotAProject() {
        val dir = Files.createTempDirectory("foxhg-notproject")
        try {
            assertIs<FoxResult.Err>(ProjectStore.load(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun opensSyntheticRomSource() {
        assertIs<FoxResult.Ok<*>>(RomProject.open(ByteArrayRomSource(SyntheticRom.build(listOf("x" to byteArrayOf(1))))))
    }

    @Test
    fun createRefusesToOverwriteAndRelinkFollowsAMovedRom() {
        val dir = Files.createTempDirectory("foxhg-relink")
        try {
            val rom = dir.resolve("a.nds")
            Files.write(rom, SyntheticRom.build(listOf("one" to ByteArray(100) { 1 })))
            val projectDir = dir.resolve("p")
            assertIs<FoxResult.Ok<*>>(ProjectStore.create(projectDir, rom))
            assertTrue(ProjectStore.isProject(projectDir))
            assertIs<FoxResult.Err>(ProjectStore.create(projectDir, rom), "an existing project must not be overwritten")

            // The ROM moves: loading fails with a clear message, relinking fixes it.
            val moved = dir.resolve("moved.nds")
            Files.move(rom, moved)
            val failure = ProjectStore.load(projectDir)
            assertIs<FoxResult.Err>(failure)
            assertTrue(failure.message.contains("no longer exists"))
            assertEquals(rom.toAbsolutePath(), ProjectStore.romOf(projectDir))
            assertIs<FoxResult.Err>(ProjectStore.relink(projectDir, dir.resolve("nope.nds")))
            assertIs<FoxResult.Ok<Unit>>(ProjectStore.relink(projectDir, moved))
            val loaded = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            assertEquals(moved.toAbsolutePath(), loaded.romPath.toAbsolutePath())
            loaded.source.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun theArm9CompressionChoiceIsKept() {
        val dir = Files.createTempDirectory("foxhg-arm9flag")
        try {
            val rom = dir.resolve("a.nds")
            Files.write(rom, SyntheticRom.build(listOf("one" to ByteArray(100) { 1 })))
            val projectDir = dir.resolve("p")
            ProjectStore.create(projectDir, rom)
            val first = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            first.project.compressArm9 = !first.project.compressArm9
            val chosen = first.project.compressArm9
            ProjectStore.save(projectDir, first.project)
            first.source.close()
            val second = (ProjectStore.load(projectDir) as FoxResult.Ok).value
            assertEquals(chosen, second.project.compressArm9)
            second.source.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
