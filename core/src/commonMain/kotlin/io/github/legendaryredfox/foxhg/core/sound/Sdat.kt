package io.github.legendaryredfox.foxhg.core.sound

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** A sequence of the archive: its index (the sound id the game and the scripts use), name and the files it plays. */
class SequenceInfo(val id: Int, val name: String, val file: Int, val bank: Int, val volume: Int)

/** A bank of instruments: its file and the (up to four) wave archives its instruments pick samples from, -1 for none. */
class BankInfo(val id: Int, val name: String, val file: Int, val waveArchives: List<Int>)

/**
 * The sound archive (`data/sound/gs_sound_data.sdat`, an "SDAT"): sequences (music and sound effects), banks of
 * instruments and wave archives of samples, each a file inside the archive, with names in a symbol block.
 */
class Sdat private constructor(
    private val bytes: ByteArray,
    val sequences: List<SequenceInfo>,
    val banks: List<BankInfo?>,
    private val waveArchiveFiles: List<Int?>,
    private val files: List<IntRange>,
) {
    fun file(index: Int): ByteArray? = files.getOrNull(index)?.let { bytes.copyOfRange(it.first, it.last + 1) }

    fun sequence(id: Int): SequenceInfo? = sequences.getOrNull(id)?.takeIf { it.file >= 0 }

    fun sequenceNamed(name: String): SequenceInfo? = sequences.firstOrNull { it.name == name }

    fun bank(id: Int): BankInfo? = banks.getOrNull(id)

    fun waveArchive(id: Int): ByteArray? = waveArchiveFiles.getOrNull(id)?.let { file(it) }

    companion object {
        fun parse(bytes: ByteArray): FoxResult<Sdat> {
            if (bytes.size < 0x40 || bytes.decodeToString(0, 4) != "SDAT") return FoxResult.Err("Not a sound archive (SDAT)")
            val symb = bytes.u32(0x10).toInt()
            val info = bytes.u32(0x18).toInt()
            val fat = bytes.u32(0x20).toInt()
            if (bytes.decodeToString(info, info + 4) != "INFO" || bytes.decodeToString(fat, fat + 4) != "FAT ") return FoxResult.Err("SDAT blocks are missing")
            val hasNames = symb > 0 && bytes.decodeToString(symb, symb + 4) == "SYMB"

            val fileCount = bytes.u32(fat + 8).toInt()
            val files = List(fileCount) { i ->
                val offset = bytes.u32(fat + 12 + i * 16).toInt()
                val size = bytes.u32(fat + 16 + i * 16).toInt()
                offset until minOf(bytes.size, offset + size)
            }

            fun recordOffsets(listOffset: Int): List<Int?> {
                val count = bytes.u32(listOffset).toInt()
                return List(count) { i -> bytes.u32(listOffset + 4 + i * 4).toInt().takeIf { it != 0 } }
            }
            fun names(kind: Int, count: Int): List<String> {
                if (!hasNames) return List(count) { "" }
                val list = symb + bytes.u32(symb + 8 + kind * 4).toInt()
                val n = bytes.u32(list).toInt()
                return List(count) { i ->
                    val off = if (i < n) bytes.u32(list + 4 + i * 4).toInt() else 0
                    if (off == 0) "" else cString(bytes, symb + off)
                }
            }

            val seqOffsets = recordOffsets(info + bytes.u32(info + 8).toInt())
            val seqNames = names(0, seqOffsets.size)
            val sequences = seqOffsets.mapIndexed { i, off ->
                if (off == null) SequenceInfo(i, seqNames[i], -1, 0, 0)
                else SequenceInfo(i, seqNames[i], bytes.u32(info + off).toInt(), bytes.u16(info + off + 4), bytes[info + off + 6].toInt() and 0xFF)
            }
            val bankOffsets = recordOffsets(info + bytes.u32(info + 16).toInt())
            val bankNames = names(2, bankOffsets.size)
            val banks = bankOffsets.mapIndexed { i, off ->
                off?.let { BankInfo(i, bankNames[i], bytes.u32(info + it).toInt(), (0 until 4).map { w -> bytes.u16(info + it + 4 + w * 2).let { v -> if (v == 0xFFFF) -1 else v } }) }
            }
            val warOffsets = recordOffsets(info + bytes.u32(info + 20).toInt())
            val waveArchives = warOffsets.map { off -> off?.let { bytes.u32(info + it).toInt() } }
            return FoxResult.Ok(Sdat(bytes, sequences, banks, waveArchives, files))
        }

        private fun cString(bytes: ByteArray, offset: Int): String {
            var end = offset
            while (end < bytes.size && bytes[end].toInt() != 0) end++
            return bytes.decodeToString(offset, end)
        }
    }
}
