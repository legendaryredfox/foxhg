package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.arm9.Arm9
import io.github.legendaryredfox.foxhg.core.compression.Blz
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ARM9 replacement against the real ROM (roms/heartgold.nds or FOXHG_ROM); passes with a notice when absent. */
class RealRomArm9Test {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withRom(block: (NdsRom, FileRomSource) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block(NdsRom.open(source).ok(), source) }
    }

    private class Built(val path: Path, val rom: NdsRom, val source: FileRomSource) : AutoCloseable {
        override fun close() {
            source.close()
            Files.deleteIfExists(path)
        }
    }

    /** Builds with [newArm9] (and optional file edits) and checks the result is a faithful, shifted copy. */
    private fun buildAndVerify(rom: NdsRom, source: FileRomSource, newArm9: ByteArray, files: Map<Int, ByteArray> = emptyMap()): Built {
        val out = Files.createTempFile("foxhg-arm9", ".nds")
        FileRomSink(out).use { sink -> NdsBuilder.build(rom, source, RomEdits(files, newArm9), sink).ok() }
        val outSource = FileRomSource(out)
        val built = NdsRom.open(outSource).ok()
        assertContentEquals(newArm9, built.readArm9(), "the ARM9 in the built ROM")
        assertEquals(rom.fileCount, built.fileCount)
        for (id in 0 until rom.fileCount) {
            val expected = files[id] ?: rom.read(rom.file(id)!!)
            assertContentEquals(expected, built.read(built.file(id)!!), "file $id")
        }
        assertEquals(rom.arm9Overlays.map { it.copy() }.size, built.arm9Overlays.size)
        assertEquals(rom.read("a/0/2/7")!!.size, built.read("a/0/2/7")!!.size)
        val header = outSource.read(0, NdsHeader.SIZE)
        assertEquals(header.u16(0x15E), Crc16.compute(header, 0, 0x15E), "header CRC")
        assertEquals(0L, built.header.arm9Overlays.offset % 0x200)
        assertEquals(newArm9.size.toLong(), built.header.arm9.size)
        return Built(out, built, outSource)
    }

    @Test
    fun plainArm9ReplacesTheCompressedOneAndEverythingShifts() = withRom { rom, source ->
        val plain = Arm9.decompressed(rom).ok()
        val footer = Arm9.footer(rom)!!
        val stored = Arm9.forRom(plain, footer.paramsOffset, compress = false).ok()
        assertEquals(0L, stored.u32(footer.paramsOffset + 0x14), "compressed_static_end is cleared for a plain ARM9")
        buildAndVerify(rom, source, stored).use { b ->
            val built = b.rom
            println("plain ARM9: ${rom.header.arm9.size} -> ${built.header.arm9.size} bytes, ROM ${Files.size(romPath)} -> ${Files.size(b.path)}; overlays moved ${built.header.arm9Overlays.offset - rom.header.arm9Overlays.offset}")
            assertTrue(built.header.arm9Overlays.offset > rom.header.arm9Overlays.offset)
            assertContentEquals(footer.bytes, built.readAfterArm9(12), "footer is kept")
            assertContentEquals(plain.copyOf(footer.paramsOffset + 0x14), built.readArm9().copyOf(footer.paramsOffset + 0x14))
        }
    }

    @Test
    fun recompressedArm9RoundTripsAndKeepsTheStaticEndInSync() = withRom { rom, source ->
        val plain = Arm9.decompressed(rom).ok()
        val footer = Arm9.footer(rom)!!
        val stored = Arm9.forRom(plain, footer.paramsOffset, compress = true).ok()
        println("recompressed ARM9: ${rom.header.arm9.size} -> ${stored.size} bytes")
        assertEquals(0x02000000L + stored.size, stored.u32(footer.paramsOffset + 0x14))
        assertTrue(Blz.isInPlaceSafe(stored))
        val back = Blz.decompress(stored).ok()
        // Everything but the static-end word is the same as the plain image.
        val a = plain.copyOf().also { it.fill(0, footer.paramsOffset + 0x14, footer.paramsOffset + 0x18) }
        val b = back.copyOf().also { it.fill(0, footer.paramsOffset + 0x14, footer.paramsOffset + 0x18) }
        assertContentEquals(a, b)
        buildAndVerify(rom, source, stored).close()
    }

    @Test
    fun arm9AndFileEditsCombine() = withRom { rom, source ->
        val plain = Arm9.decompressed(rom).ok()
        val footer = Arm9.footer(rom)!!
        val stored = Arm9.forRom(plain, footer.paramsOffset, compress = false).ok()
        val personal = rom.file("a/0/0/2")!!
        val edited = rom.read(personal).copyOf().also { it[it.size - 1] = 0x5A }
        buildAndVerify(rom, source, stored, mapOf(personal.id to edited)).use { b ->
            assertEquals(0x5A, b.rom.read("a/0/0/2")!!.last().toInt())
        }
    }
}
