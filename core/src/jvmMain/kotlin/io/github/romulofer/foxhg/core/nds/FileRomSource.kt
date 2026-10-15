package io.github.romulofer.foxhg.core.nds

import java.io.RandomAccessFile
import java.nio.file.Path

/** [RomSource] over a file on disk. Close it when the project closes. */
class FileRomSource(path: Path) : RomSource, AutoCloseable {
    private val file = RandomAccessFile(path.toFile(), "r")

    override val size: Long = file.length()

    @Synchronized
    override fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset + length <= size) { "read $offset+$length outside $size" }
        val buffer = ByteArray(length)
        file.seek(offset)
        file.readFully(buffer)
        return buffer
    }

    override fun close() = file.close()
}
