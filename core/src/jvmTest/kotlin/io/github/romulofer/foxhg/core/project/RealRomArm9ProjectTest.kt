package io.github.romulofer.foxhg.core.project

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.FileRomSink
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ARM9 edits through RomProject and ProjectStore on the real ROM; passes with a notice when it is absent. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class RealRomArm9ProjectTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun arm9EditsSurviveSaveLoadAndBuild() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val dir = Files.createTempDirectory("foxhg-arm9-project")
        try {
            val projectDir = dir.resolve("p")
            ProjectStore.create(projectDir, romPath).ok()
            val first = ProjectStore.load(projectDir).ok()
            val project = first.project
            assertFalse(project.isDirty)
            assertTrue(project.compressArm9, "the vanilla ARM9 is compressed, so builds keep it compressed by default")

            val plain = project.readArm9().ok()
            assertTrue(plain.size > 1_000_000)
            assertEquals(1122040, plain.size)
            project.writeArm9(plain).ok()
            assertFalse(project.isDirty, "writing the identical ARM9 is not an edit")

            // A harmless data edit deep in the image (an unused pad byte pattern is not guaranteed, so flip a literal-pool byte and back).
            val patched = plain.copyOf().also { it[0x100000] = (it[0x100000] + 1).toByte() }
            project.writeArm9(patched).ok()
            assertTrue(project.isDirty)
            ProjectStore.save(projectDir, project).ok()
            first.source.close()
            assertTrue(Files.exists(projectDir.resolve("arm9.bin")))

            val second = ProjectStore.load(projectDir).ok()
            assertContentEquals(patched, second.project.readArm9().ok())
            val built = Files.createTempFile(dir, "built", ".nds")
            FileRomSink(built).use { sink -> second.project.build(sink).ok() }
            FileRomSource(built).use { outSource ->
                val rom = NdsRom.open(outSource).ok()
                val back = io.github.romulofer.foxhg.core.arm9.Arm9.decompressed(rom).ok()
                assertEquals(patched[0x100000], back[0x100000])
                assertEquals(patched.size, back.size)
                assertTrue(rom.read("a/0/2/7")!!.isNotEmpty())
            }
            second.project.revertArm9()
            ProjectStore.save(projectDir, second.project).ok()
            second.source.close()
            assertFalse(Files.exists(projectDir.resolve("arm9.bin")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
