package io.github.romulofer.foxhg.core.nds

/** CRC-16/MODBUS as used by the DS cartridge header (bytes 0x000..0x15D, stored at 0x15E). */
object Crc16 {
    fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    const val HEADER_CRC_LENGTH = 0x15E
    const val HEADER_CRC_OFFSET = 0x15E
}
