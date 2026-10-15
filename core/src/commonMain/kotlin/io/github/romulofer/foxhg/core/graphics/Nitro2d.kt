package io.github.romulofer.foxhg.core.graphics

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/** A decoded picture: [argb] holds `width * height` pixels, row by row, as 0xAARRGGBB. */
class RgbaImage(val width: Int, val height: Int, val argb: IntArray) {
    init { require(argb.size == width * height) { "pixel count does not match ${width}x$height" } }

    /** The rows from [top] to [top] + [rows]. */
    fun crop(top: Int, rows: Int): RgbaImage = RgbaImage(width, rows, argb.copyOfRange(top * width, (top + rows) * width))

    val opaqueColors: Int get() = argb.filter { it ushr 24 != 0 }.toSet().size

    override fun equals(other: Any?) = other is RgbaImage && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode() = argb.contentHashCode() * 31 + width
}

/** NCLR ("RLCN"): the colours of a graphic, in groups of 16 (4 bits per pixel) or as one set of 256. */
class Nclr(val colors: IntArray) {
    /** Palette [index] of 16 colours; colour 0 of a 4-bit graphic is transparent. */
    fun palette16(index: Int): IntArray = IntArray(16) { colors.getOrElse(index * 16 + it) { 0 } }

    companion object {
        fun parse(bytes: ByteArray): FoxResult<Nclr> {
            if (bytes.size < 0x28 || bytes.decodeToString(0, 4) != "RLCN") return FoxResult.Err("Not an NCLR file")
            val section = findSection(bytes, "TTLP") ?: return FoxResult.Err("NCLR has no palette section")
            val length = bytes.u32(section + 0x10).toInt()
            val offset = bytes.u32(section + 0x14).toInt()
            val start = section + 8 + offset
            val count = minOf(length, bytes.size - start) / 2
            if (count <= 0) return FoxResult.Err("NCLR holds no colours")
            return FoxResult.Ok(Nclr(IntArray(count) { rgb555(bytes.u16(start + it * 2)) }))
        }

        /** DS colour: five bits each of red, green and blue (blue in the high bits), as opaque ARGB. */
        fun rgb555(c: Int): Int {
            val r = (c and 0x1F) * 255 / 31
            val g = (c shr 5 and 0x1F) * 255 / 31
            val b = (c shr 10 and 0x1F) * 255 / 31
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/** NCGR ("RGCN"): tiles of colour indices. [indices] holds one index per pixel, [width] pixels per row. */
class Ncgr(val width: Int, val height: Int, val bitsPerPixel: Int, val indices: ByteArray) {
    /** The picture with palette [colors]; index 0 is transparent. */
    fun toImage(colors: IntArray): RgbaImage =
        RgbaImage(width, height, IntArray(indices.size) { i -> val c = indices[i].toInt() and 0xFF; if (c == 0) 0 else colors.getOrElse(c) { 0 } })

    companion object {
        /**
         * Reads the picture. Tiles are 8x8 and laid out row by row; a file that does not say how wide it is (width -1)
         * is read [defaultWidthTiles] tiles wide. A line buffer (flag 1) is read pixel row by pixel row instead.
         */
        fun parse(bytes: ByteArray, defaultWidthTiles: Int = 4): FoxResult<Ncgr> {
            if (bytes.size < 0x30 || bytes.decodeToString(0, 4) != "RGCN") return FoxResult.Err("Not an NCGR file")
            val section = findSection(bytes, "RAHC") ?: return FoxResult.Err("NCGR has no character section")
            val heightTiles = bytes.u16(section + 8).toShort().toInt()
            val widthTiles = bytes.u16(section + 10).toShort().toInt()
            val format = bytes.u32(section + 12).toInt()
            val flags = bytes.u32(section + 20).toInt()
            val length = bytes.u32(section + 24).toInt()
            val offset = bytes.u32(section + 28).toInt()
            val bpp = when (format) { 3 -> 4; 4 -> 8; else -> return FoxResult.Err("NCGR pixel format $format is not supported") }
            val dataStart = section + 8 + offset
            val data = bytes.copyOfRange(dataStart, minOf(bytes.size, dataStart + length))
            val pixels = if (bpp == 4) data.size * 2 else data.size
            fun pixel(i: Int): Byte = if (bpp == 4) ((data[i / 2].toInt() shr ((i % 2) * 4)) and 0xF).toByte() else data[i]
            val wTiles = if (widthTiles > 0) widthTiles else defaultWidthTiles
            val width = wTiles * 8
            val tiles = pixels / 64
            val hTiles = if (heightTiles > 0) heightTiles else (tiles + wTiles - 1) / wTiles
            val height = hTiles * 8
            val out = ByteArray(width * height)
            if (flags and 1 != 0) {
                for (i in 0 until minOf(pixels, out.size)) out[i] = pixel(i)
            } else {
                for (t in 0 until minOf(tiles, wTiles * hTiles)) {
                    val tx = (t % wTiles) * 8
                    val ty = (t / wTiles) * 8
                    for (p in 0 until 64) out[(ty + p / 8) * width + tx + p % 8] = pixel(t * 64 + p)
                }
            }
            return FoxResult.Ok(Ncgr(width, height, bpp, out))
        }
    }
}

/** Sections of a Nitro file follow the 16-byte header, each `signature, u32 length, ...`. */
internal fun findSection(bytes: ByteArray, signature: String): Int? {
    var p = 0x10
    while (p + 8 <= bytes.size) {
        if (bytes.decodeToString(p, p + 4) == signature) return p
        val length = bytes.u32(p + 4).toInt()
        if (length < 8) return null
        p += length
    }
    return null
}
