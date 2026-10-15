package io.github.romulofer.foxhg.core.nds

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.u16
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds against the clean HeartGold ROM (roms/heartgold.nds or FOXHG_ROM); passes with a notice when absent. */
class RealRomBuildTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun withRom(block: (NdsRom, FileRomSource) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((NdsRom.open(source) as FoxResult.Ok).value, source) }
    }

    @Test
    fun headerCrcAlgorithmMatchesTheRealRom() = withRom { _, source ->
        val header = source.read(0, NdsHeader.SIZE)
        assertEquals(header.u16(0x15E), Crc16.compute(header, 0, 0x15E))
    }

    @Test
    fun uneditedBuildIsByteIdentical() = withRom { rom, source ->
        val out = Files.createTempFile("foxhg-build", ".nds")
        try {
            FileRomSink(out).use { sink -> assertTrue(NdsBuilder.build(rom, source, RomEdits(), sink) is FoxResult.Ok) }
            assertEquals(romPath.toFile().length(), out.toFile().length())
            assertEquals(sha256(romPath), sha256(out))
        } finally {
            Files.delete(out)
        }
    }

    @Test
    fun editedNarcSurvivesAndNeighboursAreUntouched() = withRom { rom, source ->
        val personal = rom.file("a/0/0/2")!!
        val narc = (Narc.parse(rom.read(personal)) as FoxResult.Ok).value
        val files = narc.files.map { it.copyOf() }
        files[1][0] = (files[1][0] + 1).toByte()
        val edited = Narc(files, narc.nameTable).toBytes()
        val bigger = edited + ByteArray(4096)

        for (replacement in listOf(edited, bigger)) {
            val out = Files.createTempFile("foxhg-build", ".nds")
            try {
                FileRomSink(out).use { sink ->
                    assertTrue(NdsBuilder.build(rom, source, RomEdits(mapOf(personal.id to replacement)), sink) is FoxResult.Ok)
                }
                FileRomSource(out).use { outSource ->
                    val built = (NdsRom.open(outSource) as FoxResult.Ok).value
                    assertContentEquals(replacement, built.read("a/0/0/2"))
                    val other = rom.file("a/0/1/2")!!
                    assertContentEquals(rom.read(other), built.read("a/0/1/2"))
                    assertContentEquals(rom.readArm9(), built.readArm9())
                    val header = outSource.read(0, NdsHeader.SIZE)
                    assertEquals(header.u16(0x15E), Crc16.compute(header, 0, 0x15E))
                }
            } finally {
                Files.delete(out)
            }
        }
    }

    @Test
    fun replacingACompressedOverlayUpdatesItsTableEntry() = withRom { rom, source ->
        val overlay = rom.arm9Overlays.first { it.isCompressed && it.ramSize > 50_000 }
        val plain = (io.github.romulofer.foxhg.core.compression.Blz.decompress(rom.readOverlay(overlay)!!) as FoxResult.Ok).value
        // Recompressing a prefix gives a differently sized (but valid) stream.
        val replacement = (io.github.romulofer.foxhg.core.compression.Blz.compress(plain.copyOf(plain.size - 4096)) as FoxResult.Ok).value
        val out = Files.createTempFile("foxhg-build", ".nds")
        try {
            FileRomSink(out).use { sink ->
                assertTrue(NdsBuilder.build(rom, source, RomEdits(mapOf(overlay.fileId to replacement)), sink) is FoxResult.Ok)
            }
            FileRomSource(out).use { outSource ->
                val built = (NdsRom.open(outSource) as FoxResult.Ok).value
                val entry = built.arm9Overlays[overlay.id]
                assertEquals(replacement.size.toLong(), entry.compressedSize)
                assertEquals(overlay.flags, entry.flags)
                assertEquals(overlay.ramSize, entry.ramSize, "other table fields stay")
                assertContentEquals(replacement, built.readOverlay(entry))
                // Neighbouring entries are untouched.
                val other = rom.arm9Overlays.first { it.id != overlay.id && it.isCompressed }
                assertEquals(other.compressedSize, built.arm9Overlays[other.id].compressedSize)
            }
        } finally {
            Files.delete(out)
        }
    }
}
