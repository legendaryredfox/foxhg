package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** The two blocks of a save slot: the general data (player, party, bag, flags, ...) and the PC storage (boxes). */
enum class BlockKind(val index: Int, val offset: Int, val size: Int) {
    GENERAL(0, SaveFormat.GENERAL_OFFSET, SaveFormat.GENERAL_SIZE),
    STORAGE(1, SaveFormat.STORAGE_OFFSET, SaveFormat.STORAGE_SIZE),
}

/** The 16 byte footer that ends every block: how many times the game has saved, the block's size, [MAGIC], which block it is and the CRC of the rest. */
data class BlockFooter(val count: Long, val size: Long, val magic: Long, val block: Int, val crc: Int) {
    companion object {
        const val SIZE = 16
        const val MAGIC = 0x20060623L

        fun read(bytes: ByteArray, at: Int) = BlockFooter(bytes.u32(at), bytes.u32(at + 4), bytes.u32(at + 8), bytes.u16(at + 12), bytes.u16(at + 14))
    }
}

/** One block as it sits in the file: where, its footer, and whether the footer is sound (right size, magic and block number, and the CRC matches). */
data class SaveBlock(val slot: Int, val kind: BlockKind, val offset: Int, val footer: BlockFooter, val valid: Boolean)

/**
 * The layout of a HeartGold/SoulSilver save file: 512 KB of flash with two copies (slots) of the save, at 0 and at 0x40000; the game writes the
 * other slot each time, so one of them is always whole. Each slot holds the general block at +0 and the storage block at +0xF700 (the first is
 * 0xF628 bytes, the second 0x12310). Sizes and the footer from the decomp's `save.c` and `save_arrays.c`; confirmed on a real save.
 */
object SaveFormat {
    const val FILE_SIZE = 0x80000
    val SLOT_OFFSETS = listOf(0, 0x40000)
    const val GENERAL_OFFSET = 0
    const val GENERAL_SIZE = 0xF628
    const val STORAGE_OFFSET = 0xF700
    const val STORAGE_SIZE = 0x12310

    /** DeSmuME's `.dsv` ends a raw save with this text and a few numbers (122 bytes in all): cut it off to get the raw save. */
    const val DSV_FOOTER_SIZE = 122
    const val DSV_END = "|-DESMUME SAVE-|"

    /** CRC-16/CCITT-FALSE (polynomial 0x1021, start value 0xFFFF, most significant bit first): the game's `GF_CalcCRC16`. */
    fun crc16(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor ((bytes[i].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc
    }
}

/**
 * A save file: the 512 KB of save memory ([bytes]) and, for a DeSmuME `.dsv`, the footer that came after them ([dsvFooter], put back by [toFileBytes]).
 * [blocks] lists the four blocks (two per slot) with their footers checked. Nothing here changes the bytes unless asked: [withFixedChecksums] rewrites
 * the CRC of every block that has a footer, which a changed block needs before the game accepts it.
 */
class SaveFile private constructor(val bytes: ByteArray, val dsvFooter: ByteArray?) {
    val blocks: List<SaveBlock> = SaveFormat.SLOT_OFFSETS.flatMapIndexed { slot, base ->
        BlockKind.entries.map { kind ->
            val offset = base + kind.offset
            val footer = BlockFooter.read(bytes, offset + kind.size - BlockFooter.SIZE)
            val sound = footer.size == kind.size.toLong() && footer.magic == BlockFooter.MAGIC && footer.block == kind.index &&
                footer.crc == SaveFormat.crc16(bytes, offset, offset + kind.size - BlockFooter.SIZE)
            SaveBlock(slot, kind, offset, footer, sound)
        }
    }

    fun block(slot: Int, kind: BlockKind): SaveBlock = blocks.first { it.slot == slot && it.kind == kind }

    /**
     * The slot the game would load [kind] from: the only sound one, or the one with the higher save counter when both are (the counter wraps:
     * 0xFFFFFFFF is older than 0). Null when neither is sound.
     */
    fun current(kind: BlockKind): SaveBlock? {
        val a = block(0, kind)
        val b = block(1, kind)
        return when {
            a.valid && b.valid -> if (newer(b.footer.count, a.footer.count)) b else a
            a.valid -> a
            b.valid -> b
            else -> null
        }
    }

    private fun newer(count: Long, than: Long): Boolean = when {
        count == 0xFFFFFFFFL && than == 0L -> false
        count == 0L && than == 0xFFFFFFFFL -> true
        else -> count > than
    }

    /** The data of [block] without its footer. */
    fun data(block: SaveBlock): ByteArray = bytes.copyOfRange(block.offset, block.offset + block.kind.size - BlockFooter.SIZE)

    /** The [size] bytes of [chunk] in [block] (a block of the same kind as the chunk). */
    fun chunk(block: SaveBlock, chunk: SaveChunk): ByteArray {
        require(block.kind == chunk.block) { "${chunk.name} is in the ${chunk.block} block" }
        return bytes.copyOfRange(block.offset + chunk.offset, block.offset + chunk.offset + chunk.size)
    }

    /**
     * A copy with [chunk] of [block] replaced by [data] ([SaveChunk.size] bytes): the chunk's own CRC is recomputed where it keeps one and so is the
     * block's, so the game accepts the result.
     */
    fun withChunk(block: SaveBlock, chunk: SaveChunk, data: ByteArray): SaveFile {
        require(block.kind == chunk.block) { "${chunk.name} is in the ${chunk.block} block" }
        require(data.size == chunk.size) { "${chunk.name} is ${chunk.size} bytes, got ${data.size}" }
        val copy = bytes.copyOf()
        data.copyInto(copy, block.offset + chunk.offset)
        if (chunk.hasOwnCrc) {
            val rounded = (chunk.size + 3) and 3.inv()
            copy.putU16(block.offset + chunk.crcOffset, SaveFormat.crc16(copy, block.offset + chunk.offset, block.offset + chunk.offset + rounded))
        }
        return SaveFile(copy, dsvFooter).withFixedChecksums(listOf(block))
    }

    /** A copy with the bytes of [block]'s data replaced by [data] (the same length) and its CRC recomputed. */
    fun withData(block: SaveBlock, data: ByteArray): SaveFile {
        require(data.size == block.kind.size - BlockFooter.SIZE) { "a ${block.kind} block holds ${block.kind.size - BlockFooter.SIZE} bytes of data" }
        val copy = bytes.copyOf()
        data.copyInto(copy, block.offset)
        return SaveFile(copy, dsvFooter).withFixedChecksums(listOf(block))
    }

    /** A copy with the footer CRC of [only] (or of every block that has a sound size, magic and block number) recomputed. */
    fun withFixedChecksums(only: List<SaveBlock> = blocks): SaveFile {
        val copy = bytes.copyOf()
        for (block in only) {
            val footerAt = block.offset + block.kind.size - BlockFooter.SIZE
            val footer = BlockFooter.read(copy, footerAt)
            if (footer.magic != BlockFooter.MAGIC || footer.size != block.kind.size.toLong() || footer.block != block.kind.index) continue
            copy.putU16(footerAt + 14, SaveFormat.crc16(copy, block.offset, footerAt))
        }
        return SaveFile(copy, dsvFooter)
    }

    /** The file as it is stored: the save memory and, for a `.dsv`, its footer. */
    fun toFileBytes(): ByteArray = if (dsvFooter == null) bytes.copyOf() else bytes + dsvFooter

    companion object {
        /** Reads a raw save ([SaveFormat.FILE_SIZE] bytes) or a DeSmuME `.dsv` (those bytes and the 122 byte footer). */
        fun parse(file: ByteArray): FoxResult<SaveFile> {
            val raw = SaveFormat.FILE_SIZE
            return when {
                file.size == raw -> FoxResult.Ok(SaveFile(file.copyOf(), null))
                file.size == raw + SaveFormat.DSV_FOOTER_SIZE && file.decodeToString(file.size - SaveFormat.DSV_END.length) == SaveFormat.DSV_END ->
                    FoxResult.Ok(SaveFile(file.copyOfRange(0, raw), file.copyOfRange(raw, file.size)))
                else -> FoxResult.Err("A save file is $raw bytes (or ${raw + SaveFormat.DSV_FOOTER_SIZE} with the DeSmuME footer), this one is ${file.size}")
            }
        }
    }
}
