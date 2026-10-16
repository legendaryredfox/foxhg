package io.github.legendaryredfox.foxhg.core.graphics

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * NSBTX / BTX0: the textures and palettes of a Nitro 3D model (overworld sprites, map tilesets). A BMD0 model file
 * with an embedded TEX0 block reads the same way. Textures are listed in the order of the file's name dictionary. Only the plain formats are decoded; the block-compressed 4x4 format (5) is not.
 */
class Nsbtx private constructor(
    private val bytes: ByteArray,
    private val textureData: Int,
    private val textures: List<Texture>,
    private val palettes: List<Int>,
    private val textureNames: List<String>,
    private val paletteNames: List<String>,
) {
    private class Texture(val offset: Int, val format: Int, val width: Int, val height: Int, val transparentZero: Boolean)

    val textureCount: Int get() = textures.size

    fun textureIndex(name: String): Int = textureNames.indexOf(name)

    /** The texture called [name] with the palette called [paletteName] (or the first palette), as in a model's material. */
    fun image(name: String, paletteName: String?): RgbaImage? {
        val texture = textureNames.indexOf(name).takeIf { it >= 0 } ?: return null
        return image(texture, paletteNames.indexOf(paletteName).coerceAtLeast(0))
    }

    /** Texture [index] drawn with palette [palette] (0 when the file has fewer palettes); null if the format is not supported. */
    fun image(index: Int, palette: Int = 0): RgbaImage? {
        val t = textures.getOrNull(index) ?: return null
        val paletteAt = palettes.getOrNull(palette) ?: palettes.firstOrNull() ?: return null
        val start = textureData + t.offset
        val pixels = t.width * t.height
        val bitsPerPixel = when (t.format) { 1, 6 -> 8; 2 -> 2; 3 -> 4; 4 -> 8; 7 -> 16; else -> return null }
        if (start + pixels * bitsPerPixel / 8 > bytes.size) return null
        fun color(i: Int): Int = if (paletteAt + i * 2 + 2 <= bytes.size) Nclr.rgb555(bytes.u16(paletteAt + i * 2)) else 0
        val out = IntArray(pixels) { k ->
            when (t.format) {
                2 -> (bytes[start + k / 4].toInt() shr ((k % 4) * 2)) and 3
                3 -> (bytes[start + k / 2].toInt() shr ((k % 2) * 4)) and 15
                4 -> bytes[start + k].toInt() and 0xFF
                1 -> bytes[start + k].toInt() and 31 // colour index; alpha below
                6 -> bytes[start + k].toInt() and 7
                else -> bytes.u16(start + k * 2)
            }.let { v ->
                when (t.format) {
                    7 -> if (v and 0x8000 == 0) 0 else Nclr.rgb555(v)
                    1 -> color(v).withAlpha((bytes[start + k].toInt() and 0xFF) shr 5, 7)
                    6 -> color(v).withAlpha((bytes[start + k].toInt() and 0xFF) shr 3, 31)
                    else -> if (v == 0 && t.transparentZero) 0 else color(v)
                }
            }
        }
        return RgbaImage(t.width, t.height, out)
    }

    private fun Int.withAlpha(level: Int, max: Int): Int = if (level == 0) 0 else (this and 0xFFFFFF) or ((level * 255 / max) shl 24)

    companion object {
        private fun names(bytes: ByteArray, at: Int, count: Int): List<String> = (0 until count).map { i ->
            val start = at + i * 16
            if (start + 16 > bytes.size) "" else bytes.decodeToString(start, start + 16).substringBefore('\u0000')
        }

        fun parse(bytes: ByteArray): FoxResult<Nsbtx> {
            if (bytes.size < 0x14 || bytes.decodeToString(0, 4).let { it != "BTX0" && it != "BMD0" }) return FoxResult.Err("Not an NSBTX file")
            val blockCount = bytes.u16(0xE)
            var block = -1
            for (i in 0 until blockCount) {
                val at = bytes.u32(0x10 + i * 4).toInt()
                if (at + 0x40 <= bytes.size && bytes.decodeToString(at, at + 4) == "TEX0") block = at
            }
            if (block < 0) return FoxResult.Err("NSBTX has no TEX0 block")
            val textureData = block + bytes.u32(block + 0x14).toInt()
            val paletteDictionary = block + bytes.u32(block + 0x34).toInt()
            val paletteData = block + bytes.u32(block + 0x38).toInt()
            val textureCount = bytes[block + 0x3D].toInt() and 0xFF
            // A dictionary is a 16 byte head and 4 bytes per entry, then the entries.
            val entries = block + 0x3C + 0x10 + textureCount * 4
            if (entries + textureCount * 8 > bytes.size) return FoxResult.Err("NSBTX texture list is outside the file")
            val textures = (0 until textureCount).map { i ->
                val param = bytes.u16(entries + i * 8 + 2)
                Texture(
                    offset = bytes.u16(entries + i * 8) * 8,
                    format = (param shr 10) and 7,
                    width = 8 shl ((param shr 4) and 7),
                    height = 8 shl ((param shr 7) and 7),
                    transparentZero = (param shr 13) and 1 != 0,
                )
            }
            val paletteCount = bytes[paletteDictionary + 1].toInt() and 0xFF
            val paletteEntries = paletteDictionary + 0x10 + paletteCount * 4
            if (paletteEntries + paletteCount * 4 > bytes.size) return FoxResult.Err("NSBTX palette list is outside the file")
            val palettes = (0 until paletteCount).map { paletteData + bytes.u16(paletteEntries + it * 4) * 8 }
            val textureNames = names(bytes, entries + textureCount * 8, textureCount)
            val paletteNames = names(bytes, paletteEntries + paletteCount * 4, paletteCount)
            return FoxResult.Ok(Nsbtx(bytes, textureData, textures, palettes, textureNames, paletteNames))
        }
    }
}
