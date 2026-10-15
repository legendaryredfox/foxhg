package io.github.romulofer.foxhg.core

/** Little-endian readers and writers for the DS file formats. All offsets are absolute. */

fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

fun ByteArray.u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

fun ByteArray.u32(offset: Int): Long =
    u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)

fun ByteArray.ascii(offset: Int, length: Int): String {
    val chars = CharArray(length) { this[offset + it].toInt().toChar() }
    return chars.concatToString()
}

fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
}

fun ByteArray.putU32(offset: Int, value: Long) {
    putU16(offset, (value and 0xFFFF).toInt())
    putU16(offset + 2, ((value ushr 16) and 0xFFFF).toInt())
}

fun ByteArray.putAscii(offset: Int, text: String) {
    text.forEachIndexed { i, c -> this[offset + i] = c.code.toByte() }
}
