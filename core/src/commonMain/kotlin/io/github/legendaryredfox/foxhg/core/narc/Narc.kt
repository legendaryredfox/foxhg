package io.github.legendaryredfox.foxhg.core.narc

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.ascii
import io.github.legendaryredfox.foxhg.core.putAscii
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * A Nitro Archive: FATB (file offsets) + FNTB (names) + FIMG (data), most Pokémon data files in the ROM.
 *
 * [nameTable] is kept as the raw FNTB payload so archives without names (almost all of them) and archives with
 * names both serialize back unchanged. Files are padded to 4 bytes with 0xFF when written.
 *
 * Status: round trip is tested on synthetic archives only. **[UNVERIFIED]** against real HeartGold NARCs until the
 * ROM file system reader lands (plan.md M1).
 */
class Narc(val files: List<ByteArray>, val nameTable: ByteArray = EMPTY_NAME_TABLE) {

    fun toBytes(): ByteArray {
        val offsets = ArrayList<Pair<Int, Int>>(files.size)
        var cursor = 0
        for (file in files) {
            offsets += cursor to cursor + file.size
            cursor += align4(file.size)
        }
        val fatbSize = 12 + 8 * files.size
        val fntbSize = 8 + nameTable.size
        val fimgSize = 8 + cursor
        val total = HEADER_SIZE + fatbSize + fntbSize + fimgSize

        val out = ByteArray(total) { 0xFF.toByte() }
        out.putAscii(0, "NARC")
        out.putU16(4, 0xFFFE)
        out.putU16(6, 0x0100)
        out.putU32(8, total.toLong())
        out.putU16(12, HEADER_SIZE)
        out.putU16(14, 3)

        var p = HEADER_SIZE
        out.putAscii(p, "BTAF")
        out.putU32(p + 4, fatbSize.toLong())
        out.putU16(p + 8, files.size)
        out.putU16(p + 10, 0)
        offsets.forEachIndexed { i, (start, end) ->
            out.putU32(p + 12 + 8 * i, start.toLong())
            out.putU32(p + 16 + 8 * i, end.toLong())
        }
        p += fatbSize

        out.putAscii(p, "BTNF")
        out.putU32(p + 4, fntbSize.toLong())
        nameTable.copyInto(out, p + 8)
        p += fntbSize

        out.putAscii(p, "GMIF")
        out.putU32(p + 4, fimgSize.toLong())
        val dataStart = p + 8
        files.forEachIndexed { i, file -> file.copyInto(out, dataStart + offsets[i].first) }
        return out
    }

    companion object {
        const val HEADER_SIZE = 16

        /** FNTB of an archive without file names: root directory entry only. */
        val EMPTY_NAME_TABLE = byteArrayOf(4, 0, 0, 0, 0, 0, 1, 0)

        private fun align4(n: Int) = (n + 3) and 3.inv()

        fun parse(bytes: ByteArray): FoxResult<Narc> {
            if (bytes.size < HEADER_SIZE + 12 || bytes.ascii(0, 4) != "NARC") {
                return FoxResult.Err("Not a NARC: missing magic")
            }
            var p = bytes.u16(12)
            if (p < HEADER_SIZE || p + 12 > bytes.size) return FoxResult.Err("NARC: bad header size $p")
            if (bytes.ascii(p, 4) != "BTAF") return FoxResult.Err("NARC: expected BTAF at $p")
            val fatbSize = bytes.u32(p + 4).toInt()
            val count = bytes.u16(p + 8)
            if (fatbSize != 12 + 8 * count || fatbSize > bytes.size - p) {
                return FoxResult.Err("NARC: BTAF size $fatbSize does not match $count files")
            }
            val ranges = List(count) { bytes.u32(p + 12 + 8 * it).toInt() to bytes.u32(p + 16 + 8 * it).toInt() }
            p += fatbSize

            if (p + 8 > bytes.size || bytes.ascii(p, 4) != "BTNF") return FoxResult.Err("NARC: expected BTNF at $p")
            val fntbSize = bytes.u32(p + 4).toInt()
            if (fntbSize < 8 || fntbSize > bytes.size - p) return FoxResult.Err("NARC: bad BTNF size $fntbSize")
            val nameTable = bytes.copyOfRange(p + 8, p + fntbSize)
            p += fntbSize

            if (p + 8 > bytes.size || bytes.ascii(p, 4) != "GMIF") return FoxResult.Err("NARC: expected GMIF at $p")
            val dataStart = p + 8
            val files = ArrayList<ByteArray>(count)
            for ((i, range) in ranges.withIndex()) {
                val (start, end) = range
                if (start < 0 || end < start || end > bytes.size - dataStart) {
                    return FoxResult.Err("NARC: file $i range $start..$end is outside the archive")
                }
                files += bytes.copyOfRange(dataStart + start, dataStart + end)
            }
            return FoxResult.Ok(Narc(files, nameTable))
        }
    }
}
