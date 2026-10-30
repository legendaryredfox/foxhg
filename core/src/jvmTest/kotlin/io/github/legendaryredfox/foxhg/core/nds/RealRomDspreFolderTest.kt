package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The DSPRE folder export against the clean HeartGold ROM (roms/heartgold.nds or FOXHG_ROM); passes with a notice when absent. */
class RealRomDspreFolderTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    private fun withProject(block: (RomProject, NdsRom, Path) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val work = Files.createTempDirectory("foxhg-dspre")
            try {
                block(project, (NdsRom.open(source) as FoxResult.Ok).value, work)
            } finally {
                work.toFile().deleteRecursively()
            }
        }
    }

    private fun bytes(folder: Path, path: String) = Files.readAllBytes(folder.resolve(path))

    @Test
    fun anUneditedExportHoldsEveryPartOfTheRom() = withProject { project, rom, work ->
        val folder = work.resolve(DspreFolder.folderName("heartgold.nds"))
        assertEquals("heartgold_DSPRE_contents", folder.fileName.toString())
        assertTrue(exportDspreFolder(project, folder) is FoxResult.Ok)
        assertContentEquals(rom.readRange(0, NdsHeader.SIZE), bytes(folder, "header.bin"))
        val footer = rom.readAfterArm9(12)!!
        assertContentEquals(rom.readArm9() + footer, bytes(folder, "arm9.bin"))
        assertContentEquals(rom.readArm7(), bytes(folder, "arm7.bin"))
        assertContentEquals(rom.readRange(rom.header.arm9Overlays.offset, rom.header.arm9Overlays.size.toInt()), bytes(folder, "y9.bin"))
        assertEquals(rom.header.arm9Overlays.size, bytes(folder, "y9.bin").size.toLong())
        assertContentEquals(rom.readRange(rom.header.bannerOffset, DspreFolder.BANNER_SIZE), bytes(folder, "banner.bin"))
        for (entry in rom.arm9Overlays) {
            assertContentEquals(rom.readOverlay(entry), bytes(folder, "overlay/overlay_%04d.bin".format(entry.id)), "overlay ${entry.id}")
        }
        assertEquals(rom.arm9Overlays.size, Files.list(folder.resolve("overlay")).use { it.count() }.toInt())
        for (file in rom.files) assertContentEquals(rom.read(file), bytes(folder, "data/${file.path}"), file.path)
        val written = Files.walk(folder.resolve("data")).use { s -> s.filter { Files.isRegularFile(it) }.count() }
        assertEquals(rom.files.size.toLong(), written)
        assertFalse(Files.exists(work.resolve("heartgold_DSPRE_contents.part")), "no half written folder is left")
        assertFalse(Files.exists(work.resolve("heartgold_DSPRE_contents.rom.part")), "no temporary ROM is left")
    }

    @Test
    fun anEditedFileAndOverlayShowInTheExport() = withProject { project, rom, work ->
        val smallest = rom.files.filter { it.size >= 4 }.minBy { it.size }
        val edited = project.read(smallest.path)!!.also { it[0] = (it[0] + 1).toByte() }
        assertTrue(project.write(smallest.path, edited) is FoxResult.Ok)
        val overlay = rom.arm9Overlays.first { !it.isCompressed }
        val plain = (project.readOverlay(overlay.id) as FoxResult.Ok).value.also { it[0] = (it[0] + 1).toByte() }
        assertTrue(project.writeOverlay(overlay.id, plain) is FoxResult.Ok)
        val folder = work.resolve("edited_DSPRE_contents")
        assertTrue(exportDspreFolder(project, folder) is FoxResult.Ok)
        assertContentEquals(edited, bytes(folder, "data/${smallest.path}"))
        assertContentEquals(plain, bytes(folder, "overlay/overlay_%04d.bin".format(overlay.id)))
        val other = rom.files.first { it.path != smallest.path && it.size > 0 }
        assertContentEquals(rom.read(other), bytes(folder, "data/${other.path}"), "a file nobody edited is as it was")
    }

    @Test
    fun aFolderWithFilesInIsNotOverwritten() = withProject { project, _, work ->
        val folder = work.resolve("busy_DSPRE_contents")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("mine.txt"), "keep")
        val result = exportDspreFolder(project, folder)
        assertTrue(result is FoxResult.Err)
        assertEquals("keep", Files.readString(folder.resolve("mine.txt")))
        assertTrue(Files.exists(folder.resolve("mine.txt")))
        // An empty folder is fine.
        val empty = work.resolve("empty_DSPRE_contents").also { Files.createDirectories(it) }
        assertTrue(exportDspreFolder(project, empty) is FoxResult.Ok)
    }

    @Test
    fun onlyPathsInsideTheFolderAreSafe() {
        assertTrue(DspreFolder.isSafe("a/0/2/8"))
        assertTrue(DspreFolder.isSafe("poketool/personal/pl_personal.narc"))
        for (bad in listOf("", "..", "a/../b", "/a", "a//b", "a/./b", "a\\b", "C:/x", "a/b\u0001")) assertFalse(DspreFolder.isSafe(bad), bad)
        assertEquals("x_DSPRE_contents", DspreFolder.folderName("x.nds"))
        assertEquals("x_DSPRE_contents", DspreFolder.folderName("x"))
    }
}
