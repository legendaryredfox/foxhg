package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The saves made by the emulator tests (`core/build/emulator-saves`, never committed): they parse, their checksums are right and a rewrite gives the same bytes. Skips without them. */
class RealSaveTest {
    private val names = listOf("cherrygrove", "viridian")

    @Test
    fun theRealSavesAreWholeAndRoundTrip() {
        val dir = Path.of("build", "emulator-saves")
        val files = names.map { dir.resolve("$it.dsv") }.filter { Files.exists(it) }
        if (files.isEmpty()) { println("SKIPPED: no saves in ${dir.toAbsolutePath()} (run :core:emulatorSetup)"); return }
        for (path in files) {
            val file = Files.readAllBytes(path)
            val save = (SaveFile.parse(file) as? FoxResult.Ok)?.value ?: error("$path does not parse")
            assertContentEquals(file, save.toFileBytes(), "$path writes back the same bytes")
            // the first save of a new game is in the second slot, counter 1: slot 0 was never written (all 0xFF)
            assertEquals(listOf(false, false, true, true), save.blocks.map { it.valid }, "$path: only the second slot holds a save")
            for (kind in BlockKind.entries) {
                val current = save.current(kind) ?: error("no current $kind block in $path")
                assertEquals(1, current.slot)
                assertEquals(1L, current.footer.count, "saved once")
            }
            assertContentEquals(file, save.withFixedChecksums().toFileBytes(), "recomputing the checksums of a whole save changes nothing")
            assertTrue(save.bytes.copyOfRange(0, SaveFormat.SLOT_OFFSETS[1]).all { it == 0xFF.toByte() }, "slot 0 is blank")

            // the chunk table: the chunks fill each block exactly, and the six that keep their own CRC have it where the offsets put it
            for (kind in BlockKind.entries) assertEquals(kind.size - BlockFooter.SIZE, SaveChunks.of(kind).sumOf { it.stride }, "the $kind chunks fill the block")
            val general = save.current(BlockKind.GENERAL)!!
            for (chunk in SaveChunks.of(BlockKind.GENERAL).filter { it.hasOwnCrc }) {
                val rounded = (chunk.size + 3) and 3.inv()
                val stored = (save.bytes[general.offset + chunk.crcOffset].toInt() and 0xFF) or ((save.bytes[general.offset + chunk.crcOffset + 1].toInt() and 0xFF) shl 8)
                assertEquals(SaveFormat.crc16(save.bytes, general.offset + chunk.offset, general.offset + chunk.offset + rounded), stored, "the CRC of ${chunk.name}")
            }
            // rewriting a chunk with itself changes nothing, rewriting it with something else keeps both CRCs right
            for (name in listOf("PLAYERDATA", "MYSTERY_GIFT")) {
                val chunk = SaveChunks.byName(name)
                assertContentEquals(file, save.withChunk(general, chunk, save.chunk(general, chunk)).toFileBytes(), "$name written back as it is")
                val other = save.chunk(general, chunk).also { it[0] = (it[0] + 1).toByte() }
                val changed = save.withChunk(general, chunk, other)
                assertTrue(changed.block(1, BlockKind.GENERAL).valid, "the block CRC of the edited save")
                assertContentEquals(other, changed.chunk(changed.block(1, BlockKind.GENERAL), chunk))
            }
        }
    }
}
