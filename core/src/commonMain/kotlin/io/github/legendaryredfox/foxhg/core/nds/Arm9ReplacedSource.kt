package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.arm9.Arm9
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * A read-only view of a ROM whose ARM9 has been replaced. Everything that follows the ARM9 moves by a multiple of
 * 0x200 bytes, and the header and FAT pointers are adjusted to match; no copy of the image is made. File edits are then
 * applied on top of this view by [NdsBuilder].
 */
class Arm9ReplacedSource private constructor(
    private val original: RomSource,
    private val newArm9: ByteArray,
    private val footer: ByteArray,
    private val arm9Offset: Long,
    private val oldTailStart: Long,
    private val newTailStart: Long,
    private val header: ByteArray,
    private val fatOffset: Long,
    private val fat: ByteArray,
) : RomSource {
    private val delta = newTailStart - oldTailStart
    override val size: Long = original.size + delta

    override fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset + length <= size) { "read $offset+$length outside $size" }
        val out = ByteArray(length) { 0xFF.toByte() }
        fun put(segStart: Long, segEnd: Long, supply: (from: Long, to: Long) -> ByteArray) {
            val from = maxOf(offset, segStart)
            val to = minOf(offset + length, segEnd)
            if (from < to) supply(from, to).copyInto(out, (from - offset).toInt())
        }
        // Head: original bytes with the patched header on top.
        put(0, arm9Offset) { from, to -> original.read(from, (to - from).toInt()).also { b -> overlay(b, from, 0, header) } }
        put(arm9Offset, arm9Offset + newArm9.size) { from, to -> newArm9.copyOfRange((from - arm9Offset).toInt(), (to - arm9Offset).toInt()) }
        val footerStart = arm9Offset + newArm9.size
        put(footerStart, footerStart + footer.size) { from, to -> footer.copyOfRange((from - footerStart).toInt(), (to - footerStart).toInt()) }
        // Padding up to the tail is already 0xFF. The tail is the original, shifted, with the patched FAT on top.
        put(newTailStart, size) { from, to ->
            original.read(from - delta, (to - from).toInt()).also { b -> overlay(b, from, fatOffset + delta, fat) }
        }
        return out
    }

    /** Writes [patch] (which sits at [patchAt] in the output) over [bytes] (which start at [bytesStart]). */
    private fun overlay(bytes: ByteArray, bytesStart: Long, patchAt: Long, patch: ByteArray) {
        val from = maxOf(bytesStart, patchAt)
        val to = minOf(bytesStart + bytes.size, patchAt + patch.size)
        if (from < to) patch.copyInto(bytes, (from - bytesStart).toInt(), (from - patchAt).toInt(), (to - patchAt).toInt())
    }

    companion object {
        private const val ALIGN = 0x200L

        /** [newArm9] is what is stored in the ROM (see [Arm9.forRom]). Fails when the ROM layout is not the usual one. */
        fun create(rom: NdsRom, source: RomSource, newArm9: ByteArray): FoxResult<Arm9ReplacedSource> {
            val h = rom.header
            val footer = Arm9.footer(rom)?.bytes ?: ByteArray(0)
            val oldArm9End = h.arm9.offset + h.arm9.size
            val oldTailStart = alignUp(oldArm9End + footer.size)
            val newTailStart = alignUp(h.arm9.offset + newArm9.size + footer.size)
            val delta = newTailStart - oldTailStart

            // Every pointer after the ARM9 must sit at or after the tail start, or shifting would break it.
            val pointers = mutableListOf(h.arm7.offset, h.fileNameTable.offset, h.fileAllocationTable.offset, h.bannerOffset)
            if (h.arm9Overlays.size > 0) pointers += h.arm9Overlays.offset
            if (h.arm7Overlays.size > 0) pointers += h.arm7Overlays.offset
            pointers.firstOrNull { it in oldArm9End until oldTailStart || (it != 0L && it < oldArm9End) }?.let {
                return FoxResult.Err("Unsupported ROM layout: a table at 0x${it.toString(16)} is not after the ARM9")
            }

            val header = source.read(0, NdsHeader.SIZE)
            header.putU32(0x2C, newArm9.size.toLong())
            for (field in listOf(0x30, 0x40, 0x48, 0x68, 0x80)) header.putU32(field, header.u32(field) + delta)
            if (h.arm9Overlays.size > 0) header.putU32(0x50, header.u32(0x50) + delta)
            if (h.arm7Overlays.size > 0) header.putU32(0x58, header.u32(0x58) + delta)
            header.putU16(Crc16.HEADER_CRC_OFFSET, Crc16.compute(header, 0, Crc16.HEADER_CRC_LENGTH))

            val fat = source.read(h.fileAllocationTable.offset, h.fileAllocationTable.size.toInt())
            for (i in 0 until fat.size / 8) {
                val start = fat.u32(i * 8)
                val end = fat.u32(i * 8 + 4)
                if (start >= oldTailStart) {
                    fat.putU32(i * 8, start + delta)
                    fat.putU32(i * 8 + 4, end + delta)
                }
            }
            return FoxResult.Ok(
                Arm9ReplacedSource(source, newArm9, footer, h.arm9.offset, oldTailStart, newTailStart, header, h.fileAllocationTable.offset, fat),
            )
        }

        private fun alignUp(v: Long) = (v + ALIGN - 1) / ALIGN * ALIGN
    }
}
