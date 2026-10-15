package io.github.romulofer.foxhg.core.nds

import java.io.RandomAccessFile
import java.nio.file.Path

/** [RomSink] over a new file on disk. */
class FileRomSink(path: Path) : RomSink, AutoCloseable {
    private val file = RandomAccessFile(path.toFile(), "rw").also { it.setLength(0) }

    @Synchronized
    override fun write(offset: Long, bytes: ByteArray) {
        file.seek(offset)
        file.write(bytes)
    }

    @Synchronized
    override fun extendTo(size: Long) {
        val current = file.length()
        if (size <= current) return
        file.seek(current)
        val pad = ByteArray(1 shl 20) { 0xFF.toByte() }
        var left = size - current
        while (left > 0) {
            val n = minOf(left, pad.size.toLong()).toInt()
            file.write(pad, 0, n)
            left -= n
        }
    }

    override fun close() = file.close()
}
