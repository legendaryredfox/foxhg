package io.github.romulofer.foxhg.core.nds

/** Random-access read of a ROM image, so a 128 MB `.nds` is never loaded whole just to list or read a few files. */
interface RomSource {
    val size: Long

    fun read(offset: Long, length: Int): ByteArray
}

class ByteArrayRomSource(private val bytes: ByteArray) : RomSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "read $offset+$length outside ${bytes.size}" }
        return bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
    }
}
