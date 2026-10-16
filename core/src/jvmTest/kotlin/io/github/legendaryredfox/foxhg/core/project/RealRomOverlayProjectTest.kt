package io.github.legendaryredfox.foxhg.core.project

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.FileRomSink
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Overlay edits through RomProject and ProjectStore on the real ROM; passes with a notice when it is absent. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class RealRomOverlayProjectTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun compressedOverlayEditsSurviveSaveLoadAndBuild() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val dir = Files.createTempDirectory("foxhg-overlay-project")
        try {
            val projectDir = dir.resolve("p")
            ProjectStore.create(projectDir, romPath).ok()
            val first = ProjectStore.load(projectDir).ok()
            val project = first.project
            val entry = project.rom.arm9Overlays.first { it.id == OVERLAY }
            assertTrue(entry.isCompressed, "overlay $OVERLAY is stored compressed")

            val plain = project.readOverlay(OVERLAY).ok()
            assertEquals(entry.ramSize.toInt(), plain.size, "the table's RAM size is the decompressed size")
            project.writeOverlay(OVERLAY, plain).ok()
            assertFalse(project.isDirty, "writing the identical overlay is not an edit")
            assertTrue(project.writeOverlay(OVERLAY, plain.copyOf(plain.size + 1)) is FoxResult.Err, "a size change is refused")

            val patched = plain.copyOf().also { it[0x34C04] = (it[0x34C04] + 1).toByte() }
            project.writeOverlay(OVERLAY, patched).ok()
            assertTrue(project.isDirty)
            ProjectStore.save(projectDir, project).ok()
            first.source.close()
            assertTrue(Files.exists(projectDir.resolve("overlays/$OVERLAY.bin")))

            val second = ProjectStore.load(projectDir).ok()
            assertContentEquals(patched, second.project.readOverlay(OVERLAY).ok())
            val built = Files.createTempFile(dir, "built", ".nds")
            FileRomSink(built).use { sink -> second.project.build(sink).ok() }
            FileRomSource(built).use { outSource ->
                val rom = NdsRom.open(outSource).ok()
                val reopened = RomProject(rom, outSource)
                assertContentEquals(patched, reopened.readOverlay(OVERLAY).ok(), "the rebuilt ROM decompresses to the edited overlay")
                val rebuilt = rom.arm9Overlays.first { it.id == OVERLAY }
                assertEquals(rom.file(rebuilt.fileId)!!.let { it.end - it.start }, rebuilt.compressedSize, "table size follows the new file")
                assertTrue(rom.read("a/0/2/7")!!.isNotEmpty())
            }
            second.project.revertAll()
            ProjectStore.save(projectDir, second.project).ok()
            second.source.close()
            assertFalse(Files.exists(projectDir.resolve("overlays/$OVERLAY.bin")))
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val OVERLAY = 12
    }
}
