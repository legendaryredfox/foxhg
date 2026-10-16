package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.testing.SyntheticRom
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class NdsBuilderTest {
    private val original = SyntheticRom.build(
        listOf(
            "one" to ByteArray(100) { 1 },
            "two" to ByteArray(700) { 2 },
            "three" to ByteArray(10) { 3 },
        ),
    )

    private fun open(bytes: ByteArray): Pair<NdsRom, RomSource> {
        val source = ByteArrayRomSource(bytes)
        return (NdsRom.open(source) as FoxResult.Ok).value to source
    }

    private fun build(edits: RomEdits): ByteArray {
        val (rom, source) = open(original)
        val sink = ByteArrayRomSink()
        assertIs<FoxResult.Ok<Unit>>(NdsBuilder.build(rom, source, edits, sink))
        return sink.bytes
    }

    @Test
    fun readsTheSyntheticFileSystem() {
        val (rom, _) = open(original)
        assertEquals(listOf("a/one", "a/two", "a/three"), rom.files.map { it.path })
        assertContentEquals(ByteArray(700) { 2 }, rom.read("a/two"))
    }

    @Test
    fun uneditedBuildIsByteIdentical() {
        assertContentEquals(original, build(RomEdits()))
    }

    @Test
    fun smallerFileIsWrittenInPlace() {
        val out = build(RomEdits(mapOf(1 to ByteArray(50) { 9 })))
        val (rom, _) = open(out)
        assertContentEquals(ByteArray(50) { 9 }, rom.read("a/two"))
        assertEquals(open(original).first.file("a/two")!!.start, rom.file("a/two")!!.start)
        assertContentEquals(ByteArray(100) { 1 }, rom.read("a/one"))
        assertContentEquals(ByteArray(10) { 3 }, rom.read("a/three"))
    }

    @Test
    fun largerFileIsAppendedAndHeaderIsUpdated() {
        val big = ByteArray(5000) { 7 }
        val out = build(RomEdits(mapOf(0 to big)))
        val (rom, _) = open(out)
        assertContentEquals(big, rom.read("a/one"))
        assertContentEquals(ByteArray(700) { 2 }, rom.read("a/two"))
        val file = rom.file("a/one")!!
        assertEquals(0L, file.start % NdsBuilder.ALIGNMENT)
        assertEquals(file.end, rom.header.usedRomSize)
        assertEquals(Crc16.compute(out, 0, 0x15E), out.u16(0x15E))
    }

    @Test
    fun growingPastTheChipSizeRaisesCapacity() {
        val huge = ByteArray(300_000) { 5 }
        val out = build(RomEdits(mapOf(2 to huge)))
        val (rom, _) = open(out)
        assertContentEquals(huge, rom.read("a/three"))
        // 128 KB of original image + 300 000 bytes appended needs the 512 KB chip size (capacity 2)
        assertEquals(0x80000L, out.size.toLong())
        assertEquals(2, out[0x14].toInt())
        assertEquals(rom.header.usedRomSize, out.u32(0x80))
    }

    @Test
    fun rejectsUnknownFileId() {
        val (rom, source) = open(original)
        assertIs<FoxResult.Err>(NdsBuilder.build(rom, source, RomEdits(mapOf(99 to byteArrayOf(1))), ByteArrayRomSink()))
        assertNotNull(rom.file(0))
    }
}
