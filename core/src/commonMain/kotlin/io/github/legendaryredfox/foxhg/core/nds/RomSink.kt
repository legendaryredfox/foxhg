package io.github.legendaryredfox.foxhg.core.nds

/** Random-access write target for a built ROM. */
interface RomSink {
    fun write(offset: Long, bytes: ByteArray)

    /** Grows the output to [size] bytes; new bytes are 0xFF like unused cartridge space. */
    fun extendTo(size: Long)
}

/** In-memory sink for tests and small images. */
class ByteArrayRomSink(initial: Int = 0) : RomSink {
    var bytes: ByteArray = ByteArray(initial)
        private set

    override fun write(offset: Long, bytes: ByteArray) {
        extendTo(offset + bytes.size)
        bytes.copyInto(this.bytes, offset.toInt())
    }

    override fun extendTo(size: Long) {
        if (size <= bytes.size) return
        val grown = ByteArray(size.toInt()) { 0xFF.toByte() }
        bytes.copyInto(grown)
        bytes = grown
    }
}
