package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * Replacement contents for files, keyed by FAT id, and optionally a new ARM9 as it must be stored in the ROM
 * (see `Arm9.forRom`). A new ARM9 moves everything after it.
 */
class RomEdits(val files: Map<Int, ByteArray> = emptyMap(), val arm9: ByteArray? = null) {
    val isEmpty: Boolean get() = files.isEmpty() && arm9 == null
}

/**
 * Builds a new `.nds` by patching a copy of the original image, so everything foxhg does not understand (secure
 * area, signatures, padding, unused gaps) is preserved and **an unedited build is byte-identical to the input**.
 *
 * - A replacement that fits its old slot is written in place; the rest of the slot is cleared to 0xFF.
 * - A larger replacement (or one whose slot is shared by another FAT entry) is appended after the last file,
 *   aligned to [ALIGNMENT]; its old slot is cleared if nothing else points into it.
 * - The FAT, the used ROM size (0x80), the chip capacity (0x14) and the header CRC (0x15E) are updated as needed.
 *
 * A replaced ARM9 is handled first, through [Arm9ReplacedSource]: the ROM is viewed with the new ARM9 in place and
 * everything after it shifted, then the file edits apply on that view.
 *
 * Not yet handled (plan.md M1): replacing the ARM7 binary and the banner, compacting freed space.
 */
object NdsBuilder {
    const val ALIGNMENT = 0x200L
    private const val CAPACITY_UNIT = 0x20000L

    /** The cartridge capacity byte is a shift of [CAPACITY_UNIT]; a header claiming more is corrupt. */
    private const val MAX_CAPACITY = 15

    /** [progress] is told how far along the build is, 0.0 to 1.0, as the ROM is copied. */
    fun build(rom: NdsRom, source: RomSource, edits: RomEdits, sink: RomSink, progress: (Double) -> Unit = {}): FoxResult<Unit> {
        if (edits.arm9 != null) {
            val view = when (val v = Arm9ReplacedSource.create(rom, source, edits.arm9)) {
                is FoxResult.Ok -> v.value
                is FoxResult.Err -> return v
            }
            val shifted = when (val r = NdsRom.open(view)) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return FoxResult.Err("Rebuilt layout is invalid: ${r.message}")
            }
            return build(shifted, view, RomEdits(edits.files), sink, force = true, progress)
        }
        return build(rom, source, edits, sink, force = false, progress)
    }

    private fun build(rom: NdsRom, source: RomSource, edits: RomEdits, sink: RomSink, force: Boolean, progress: (Double) -> Unit): FoxResult<Unit> {
        for (id in edits.files.keys) {
            if (id < 0 || id >= rom.fileCount) return FoxResult.Err("File id $id is not in the ROM")
        }
        copy(source, sink, progress)
        if (edits.isEmpty && !force) return FoxResult.Ok(Unit.also { progress(1.0) })

        val header = rom.header
        val fat = source.read(header.fileAllocationTable.offset, header.fileAllocationTable.size.toInt())
        val starts = LongArray(rom.fileCount) { fat.u32(it * 8) }
        val ends = LongArray(rom.fileCount) { fat.u32(it * 8 + 4) }
        // Appended files go after everything the ROM holds, so a trimmed or odd ROM whose files pass the size in the header loses nothing.
        var cursor = alignUp(maxOf(header.usedRomSize, ends.max()))

        for ((id, data) in edits.files.toSortedMap()) {
            val oldStart = starts[id]
            val oldEnd = ends[id]
            val shared = (0 until rom.fileCount).any { it != id && starts[it] < oldEnd && oldStart < ends[it] && ends[it] > starts[it] }
            if (!shared && data.size <= oldEnd - oldStart) {
                sink.write(oldStart, data)
                clear(sink, oldStart + data.size, oldEnd)
                ends[id] = oldStart + data.size
            } else {
                val newStart = cursor
                sink.write(newStart, data)
                if (!shared) clear(sink, oldStart, oldEnd)
                starts[id] = newStart
                ends[id] = newStart + data.size
                cursor = alignUp(newStart + data.size)
            }
        }

        val newFat = ByteArray(fat.size)
        for (i in 0 until rom.fileCount) {
            newFat.putU32(i * 8, starts[i])
            newFat.putU32(i * 8 + 4, ends[i])
        }
        sink.write(header.fileAllocationTable.offset, newFat)

        updateOverlayTable(rom, source, sink, header.arm9Overlays, rom.arm9Overlays, starts, ends, edits)
        updateOverlayTable(rom, source, sink, header.arm7Overlays, rom.arm7Overlays, starts, ends, edits)

        val usedEnd = maxOf(header.usedRomSize, ends.max())
        val headerBytes = source.read(0, NdsHeader.SIZE)
        headerBytes.putU32(0x80, usedEnd)
        var capacity = headerBytes[0x14].toInt() and 0xFF
        while (capacity < MAX_CAPACITY && CAPACITY_UNIT shl capacity < usedEnd) capacity++
        headerBytes[0x14] = capacity.toByte()
        headerBytes.putU16(Crc16.HEADER_CRC_OFFSET, Crc16.compute(headerBytes, 0, Crc16.HEADER_CRC_LENGTH))
        sink.write(0, headerBytes)
        sink.extendTo(CAPACITY_UNIT shl capacity)
        progress(1.0)
        return FoxResult.Ok(Unit)
    }

    /**
     * A compressed overlay's table entry stores the size of its file (low 24 bits of the last word) and the loader
     * relies on it, so a replaced overlay must update it. The flag byte is kept.
     */
    private fun updateOverlayTable(
        rom: NdsRom,
        source: RomSource,
        sink: RomSink,
        region: RomRegion,
        overlays: List<OverlayEntry>,
        starts: LongArray,
        ends: LongArray,
        edits: RomEdits,
    ) {
        val changed = overlays.filter { it.isCompressed && it.fileId in edits.files.keys }
        if (changed.isEmpty()) return
        val table = source.read(region.offset, region.size.toInt())
        for (overlay in changed) {
            val index = overlays.indexOf(overlay)
            val size = ends[overlay.fileId] - starts[overlay.fileId]
            table.putU32(index * OverlayEntry.ENTRY_SIZE + 28, (overlay.flags.toLong() shl 24) or (size and 0xFFFFFF))
        }
        sink.write(region.offset, table)
    }

    /** Copying is almost all of the work; it reports up to 0.95 and the file edits finish the rest. */
    private fun copy(source: RomSource, sink: RomSink, progress: (Double) -> Unit) {
        val chunk = 1 shl 20
        var offset = 0L
        while (offset < source.size) {
            val n = minOf(chunk.toLong(), source.size - offset).toInt()
            sink.write(offset, source.read(offset, n))
            offset += n
            progress(0.95 * offset / source.size)
        }
    }

    private fun clear(sink: RomSink, from: Long, to: Long) {
        if (to > from) sink.write(from, ByteArray((to - from).toInt()) { 0xFF.toByte() })
    }

    private fun alignUp(value: Long) = (value + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT
}
