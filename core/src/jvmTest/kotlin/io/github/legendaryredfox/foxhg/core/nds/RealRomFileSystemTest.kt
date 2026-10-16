package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.ascii
import io.github.legendaryredfox.foxhg.core.narc.Narc
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Needs the clean HeartGold ROM at roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomFileSystemTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun withRom(block: (NdsRom) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((NdsRom.open(source) as FoxResult.Ok).value) }
    }

    @Test
    fun listsTheFileSystem() = withRom { rom ->
        println("files=${rom.files.size} arm9Overlays=${rom.arm9Overlays.size} arm7Overlays=${rom.arm7Overlays.size}")
        assertTrue(rom.files.size > 300, "named files: ${rom.files.size}")
        assertTrue(rom.fileCount > rom.files.size, "overlays are unnamed FAT entries")
        assertTrue(rom.root.directories.isNotEmpty())
        assertTrue(rom.arm9Overlays.isNotEmpty())
        for (overlay in rom.arm9Overlays) assertNotNull(rom.file(overlay.fileId), "overlay ${overlay.id} file ${overlay.fileId}")
    }

    @Test
    fun everyFileReadsItsDeclaredSize() = withRom { rom ->
        for (file in rom.files) assertEquals(file.size.toInt(), rom.read(file).size, file.path)
    }

    @Test
    fun narcsRoundTripByteForByte() = withRom { rom ->
        var narcCount = 0
        val mismatches = mutableListOf<String>()
        for (file in rom.files) {
            if (file.size < 16) continue
            val bytes = rom.read(file)
            if (bytes.ascii(0, 4) != "NARC") continue
            narcCount++
            val parsed = Narc.parse(bytes)
            if (parsed !is FoxResult.Ok) {
                mismatches += "${file.path}: ${(parsed as FoxResult.Err).message}"
                continue
            }
            if (!bytes.contentEquals(parsed.value.toBytes())) mismatches += "${file.path}: bytes differ"
        }
        println("narcs=$narcCount mismatches=${mismatches.size}")
        mismatches.take(15).forEach(::println)
        assertTrue(narcCount > 100, "expected many NARCs, found $narcCount")
        assertEquals(emptyList(), mismatches)
    }

    @Test
    fun readsPersonalNarcAndArm9() = withRom { rom ->
        val personal = assertNotNull(rom.read("a/0/0/2"), "a/0/0/2 missing")
        assertEquals("NARC", personal.ascii(0, 4))
        assertEquals(rom.header.arm9.size, rom.readArm9().size.toLong())
    }
}
