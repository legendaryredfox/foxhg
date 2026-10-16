package io.github.legendaryredfox.foxhg.core.arm9

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.compression.Blz
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * The main ARM9 binary. In the ROM it is usually BLZ-compressed; code edits work on the decompressed image.
 *
 * The image holds a module-parameter block (offset given by the 12-byte footer after the ARM9 in the ROM:
 * `2106C0DE`, params offset, 0). Its `compressed_static_end` word (+0x14) is `RAM base + packed size` when the ARM9 is
 * stored compressed and 0 when it is stored plain; the startup code decompresses only when it is non-zero.
 */
object Arm9 {
    private const val RAM_BASE = 0x02000000L
    private const val COMPRESSED_END_FIELD = 0x14
    private const val NITRO_MARKER = 0xDEC00621L // bytes 21 06 C0 DE in the file

    /** Footer after the ARM9 in the ROM: marker, module-params offset, 0. Null when the ROM has none. */
    class Footer(val bytes: ByteArray) {
        val paramsOffset: Int get() = bytes.u32(4).toInt()
    }

    fun footer(rom: NdsRom): Footer? {
        val bytes = rom.readAfterArm9(12) ?: return null
        return if (bytes.u32(0) == NITRO_MARKER) Footer(bytes) else null
    }

    /** The decompressed ARM9 of [rom] (a plain ARM9 is returned as stored). */
    fun decompressed(rom: NdsRom): FoxResult<ByteArray> {
        val stored = rom.readArm9()
        return if (Blz.looksCompressed(stored)) Blz.decompress(stored) else FoxResult.Ok(stored)
    }

    /**
     * Turns a decompressed ARM9 into the bytes stored in the ROM. With [compress] it is BLZ-compressed (the leading boot
     * area, which holds the module parameters, stays raw) and `compressed_static_end` is set to match; otherwise it is
     * stored plain with `compressed_static_end` cleared.
     */
    fun forRom(plain: ByteArray, paramsOffset: Int, compress: Boolean): FoxResult<ByteArray> {
        val field = paramsOffset + COMPRESSED_END_FIELD
        if (paramsOffset <= 0 || field + 4 > plain.size) return FoxResult.Err("Module parameters at $paramsOffset do not fit in the ARM9")
        if (!compress) {
            return FoxResult.Ok(plain.copyOf().also { it.putU32(field, 0) })
        }
        val prefix = field + 4 // keep everything up to the parameters raw
        val work = plain.copyOf().also { it.putU32(field, 0) }
        val packed = when (val p = Blz.compress(work, rawPrefix = prefix)) {
            is FoxResult.Ok -> p.value
            is FoxResult.Err -> return p
        }
        packed.putU32(field, RAM_BASE + packed.size)
        return FoxResult.Ok(packed)
    }
}
