package io.github.legendaryredfox.foxhg.core.save

/**
 * The `FLAGS` chunk (0x44C bytes): [VARS] 16-bit script variables (variable 0x4000 + n is entry n) followed by the event flag bitmap (flag n is bit
 * n % 8 of byte n / 8 of the bitmap; flag 0 is never set). From the decomp's `SaveVarsFlags` (`save_vars_flags.h`); the bitmap offset was checked by
 * the game continuing at a place after flag 0x966 was set in a save.
 */
object SaveVarsFlags {
    const val VARS = 0x170
    const val FLAG_BYTES_OFFSET = VARS * 2
    const val FLAG_COUNT = 2912

    /** Set in a save, it makes Continue take the "dynamic warp" place (and clear the flag): the game's own way back after the Union Room, and the one path that builds the map's NPCs from its events. */
    const val CONTINUE_AT_DYNAMIC_WARP = 0x966

    fun isSet(chunk: ByteArray, flag: Int): Boolean {
        require(flag in 1 until FLAG_COUNT) { "flag $flag is outside 1..${FLAG_COUNT - 1}" }
        return chunk[FLAG_BYTES_OFFSET + flag / 8].toInt() and (1 shl (flag % 8)) != 0
    }

    fun with(chunk: ByteArray, flag: Int, on: Boolean): ByteArray {
        require(flag in 1 until FLAG_COUNT) { "flag $flag is outside 1..${FLAG_COUNT - 1}" }
        val copy = chunk.copyOf()
        val at = FLAG_BYTES_OFFSET + flag / 8
        val bit = 1 shl (flag % 8)
        copy[at] = (if (on) copy[at].toInt() or bit else copy[at].toInt() and bit.inv()).toByte()
        return copy
    }

    fun variable(chunk: ByteArray, variable: Int): Int {
        require(variable in 0 until VARS) { "variable $variable is outside 0..${VARS - 1}" }
        return (chunk[variable * 2].toInt() and 0xFF) or ((chunk[variable * 2 + 1].toInt() and 0xFF) shl 8)
    }
}
