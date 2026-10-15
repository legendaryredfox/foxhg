package io.github.romulofer.foxhg.core.compression

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies BLZ against every compressed overlay and the ARM9 of the real ROM (roms/heartgold.nds or FOXHG_ROM). */
class RealRomBlzTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun withRom(block: (NdsRom) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((NdsRom.open(source) as FoxResult.Ok).value) }
    }

    private fun ok(result: FoxResult<ByteArray>, what: String): ByteArray =
        (result as? FoxResult.Ok)?.value ?: error("$what failed: ${(result as FoxResult.Err).message}")

    @Test
    fun everyCompressedOverlayDecompressesToItsDeclaredRamSize() = withRom { rom ->
        var compressed = 0
        for (overlay in rom.arm9Overlays) {
            val bytes = rom.readOverlay(overlay)!!
            if (!overlay.isCompressed) continue
            compressed++
            val plain = ok(Blz.decompress(bytes), "overlay ${overlay.id}")
            assertEquals(overlay.ramSize.toInt(), plain.size, "overlay ${overlay.id}")
            assertTrue(Blz.isInPlaceSafe(bytes), "overlay ${overlay.id} should satisfy the in-place rule")
        }
        println("decompressed overlays=$compressed")
        assertTrue(compressed > 100)
    }

    @Test
    fun arm9DecompressesAndOurRecompressionRoundTrips() = withRom { rom ->
        val arm9 = rom.readArm9()
        assertTrue(Blz.looksCompressed(arm9))
        val plain = ok(Blz.decompress(arm9), "arm9")
        println("arm9 packed=${arm9.size} plain=${plain.size}")
        assertTrue(plain.size > arm9.size)
        val again = ok(Blz.compress(plain), "arm9 recompress")
        assertTrue(Blz.isInPlaceSafe(again))
        assertContentEquals(plain, ok(Blz.decompress(again), "arm9 re-decompress"))
    }

    @Test
    fun overlayRecompressionRoundTrips() = withRom { rom ->
        val overlay = rom.arm9Overlays.first { it.isCompressed && it.ramSize > 50_000 }
        val plain = ok(Blz.decompress(rom.readOverlay(overlay)!!), "overlay")
        val again = ok(Blz.compress(plain), "overlay recompress")
        assertTrue(Blz.isInPlaceSafe(again))
        assertContentEquals(plain, ok(Blz.decompress(again), "overlay re-decompress"))
    }
}
