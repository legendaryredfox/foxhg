package io.github.romulofer.foxhg.core.testing

import io.github.romulofer.foxhg.core.putAscii
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.nds.Crc16

/**
 * A tiny valid `.nds`: header + FNT + FAT + files, 512-aligned, padded to 128 KB. Files are named `a/<name>`, or
 * `<pathPrefix>/<pathName>` when given (a single file at a nested path such as `a/0/0/2`).
 */
object SyntheticRom {
    fun build(
        files: List<Pair<String, ByteArray>>,
        gameCode: String = "IPKE",
        pathPrefix: String? = null,
        pathName: String? = null,
    ): ByteArray {
        if (pathPrefix != null && pathName != null) return buildNested(files.single().second, gameCode, pathPrefix, pathName)
        val subtable0 = byteArrayOf(0x81.toByte(), 'a'.code.toByte(), 0x01, 0xF0.toByte(), 0)
        val subtable1 = ArrayList<Byte>()
        for ((name, _) in files) {
            subtable1 += name.length.toByte()
            subtable1 += name.encodeToByteArray().toList()
        }
        subtable1 += 0
        val fnt = ByteArray(16 + subtable0.size + subtable1.size)
        fnt.putU32(0, 16); fnt.putU16(4, 0); fnt.putU16(6, 2)
        fnt.putU32(8, (16 + subtable0.size).toLong()); fnt.putU16(12, 0); fnt.putU16(14, 0xF000)
        subtable0.copyInto(fnt, 16)
        subtable1.toByteArray().copyInto(fnt, 16 + subtable0.size)

        val fntOffset = 0x200L
        val fatOffset = alignUp(fntOffset + fnt.size, 0x200)
        val fat = ByteArray(files.size * 8)
        var cursor = alignUp(fatOffset + fat.size, 0x200)
        val placed = files.map { (_, data) -> (cursor).also { cursor = alignUp(cursor + data.size, 0x200) } }
        files.forEachIndexed { i, (_, data) -> fat.putU32(i * 8, placed[i]); fat.putU32(i * 8 + 4, placed[i] + data.size) }
        val usedEnd = files.indices.maxOfOrNull { placed[it] + files[it].second.size } ?: (fatOffset + fat.size)

        val out = ByteArray(0x20000.coerceAtLeast(alignUp(usedEnd, 0x20000).toInt())) { 0xFF.toByte() }
        out.fill(0, 0, 0x200)
        out.putAscii(0, "SYNTHETIC")
        out.putAscii(0x0C, gameCode)
        out.putAscii(0x10, "01")
        out[0x14] = 0
        out.putU32(0x40, fntOffset); out.putU32(0x44, fnt.size.toLong())
        out.putU32(0x48, fatOffset); out.putU32(0x4C, fat.size.toLong())
        out.putU32(0x80, usedEnd); out.putU32(0x84, 0x200)
        out.putU16(0x15E, Crc16.compute(out, 0, 0x15E))
        fnt.copyInto(out, fntOffset.toInt())
        fat.copyInto(out, fatOffset.toInt())
        files.forEachIndexed { i, (_, data) -> data.copyInto(out, placed[i].toInt()) }
        return out
    }

    private fun alignUp(v: Long, a: Long) = (v + a - 1) / a * a
    private fun alignUp(v: Int, a: Int) = (v + a - 1) / a * a

    /** One file at a nested path like `a/0/0/2`: directories a, 0, 0 chained, the file named [name] in the last. */
    private fun buildNested(data: ByteArray, gameCode: String, prefix: String, name: String): ByteArray {
        val parts = prefix.split('/')
        val dirCount = parts.size + 1
        // Main table: dirCount entries x 8 bytes, then one subtable per directory.
        val subtables = ArrayList<ByteArray>()
        for (i in parts.indices) {
            val n = parts[i].encodeToByteArray()
            val childId = 0xF000 + i + 1
            subtables += byteArrayOf((0x80 or n.size).toByte()) + n + byteArrayOf((childId and 0xFF).toByte(), (childId shr 8).toByte(), 0)
        }
        val fileName = name.encodeToByteArray()
        subtables += byteArrayOf(fileName.size.toByte()) + fileName + byteArrayOf(0)
        val mainSize = dirCount * 8
        val fnt = ByteArray(mainSize + subtables.sumOf { it.size })
        var sub = mainSize
        for (i in 0 until dirCount) {
            fnt.putU32(i * 8, sub.toLong())
            fnt.putU16(i * 8 + 4, 0)
            fnt.putU16(i * 8 + 6, if (i == 0) dirCount else 0xF000 + i - 1)
            subtables[i].copyInto(fnt, sub)
            sub += subtables[i].size
        }
        val fntOffset = 0x200L
        val fatOffset = alignUp(fntOffset + fnt.size, 0x200)
        val dataOffset = alignUp(fatOffset + 8, 0x200)
        val used = dataOffset + data.size
        val out = ByteArray(0x20000.coerceAtLeast(alignUp(used, 0x20000).toInt())) { 0xFF.toByte() }
        out.fill(0, 0, 0x200)
        out.putAscii(0, "SYNTHETIC")
        out.putAscii(0x0C, gameCode)
        out.putAscii(0x10, "01")
        out[0x14] = 0
        out.putU32(0x40, fntOffset); out.putU32(0x44, fnt.size.toLong())
        out.putU32(0x48, fatOffset); out.putU32(0x4C, 8)
        out.putU32(0x80, used); out.putU32(0x84, 0x200)
        out.putU16(0x15E, Crc16.compute(out, 0, 0x15E))
        fnt.copyInto(out, fntOffset.toInt())
        out.putU32(fatOffset.toInt(), dataOffset); out.putU32(fatOffset.toInt() + 4, dataOffset + data.size)
        data.copyInto(out, dataOffset.toInt())
        return out
    }
}
