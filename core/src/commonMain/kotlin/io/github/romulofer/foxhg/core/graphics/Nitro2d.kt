package io.github.romulofer.foxhg.core.graphics

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/** A decoded picture: [argb] holds `width * height` pixels, row by row, as 0xAARRGGBB. */
class RgbaImage(val width: Int, val height: Int, val argb: IntArray) {
    init { require(argb.size == width * height) { "pixel count does not match ${width}x$height" } }

    /** The rows from [top] to [top] + [rows]. */
    fun crop(top: Int, rows: Int): RgbaImage = RgbaImage(width, rows, argb.copyOfRange(top * width, (top + rows) * width))

    /** The part from column [left] and row [top], [columns] wide and [rows] tall. */
    fun crop(left: Int, top: Int, columns: Int, rows: Int): RgbaImage =
        RgbaImage(columns, rows, IntArray(columns * rows) { argb[(top + it / columns) * width + left + it % columns] })

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

/** One sprite part of an NCER cell: a [width] x [height] block of tiles drawn at ([x], [y]) from the cell's origin. */
class Oam(val x: Int, val y: Int, val width: Int, val height: Int, val tile: Int, val palette: Int)

/**
 * NCER ("RECN"): cells, each a list of [Oam] parts, and the tile boundary shift ([mapping]) that turns an OAM tile
 * number into a position in the NCGR (tile number * 32 << mapping bytes).
 */
class Ncer(val cells: List<List<Oam>>, val mapping: Int) {
    /**
     * Draws [cell] from the tiles of [graphic] (an NCGR file) with [palette] (a 256 colour NCLR is read in groups of 16
     * for 4-bit graphics). The picture is as large as the parts' bounding box; null if the cell or graphic is unusable.
     */
    fun render(cell: Int, graphic: ByteArray, nclr: Nclr): RgbaImage? {
        val parts = cells.getOrNull(cell)?.takeIf { it.isNotEmpty() } ?: return null
        val chars = findSection(graphic, "RAHC") ?: return null
        val bpp = when (graphic.u32(chars + 12).toInt()) { 3 -> 4; 4 -> 8; else -> return null }
        val length = graphic.u32(chars + 24).toInt()
        val start = chars + 8 + graphic.u32(chars + 28).toInt()
        val end = minOf(graphic.size, start + length)
        val left = parts.minOf { it.x }
        val top = parts.minOf { it.y }
        val width = parts.maxOf { it.x + it.width } - left
        val height = parts.maxOf { it.y + it.height } - top
        val out = IntArray(width * height)
        val tileBytes = bpp * 8
        for (part in parts) {
            val colors = if (bpp == 4) nclr.palette16(part.palette) else nclr.colors
            val base = start + (part.tile shl mapping) * 32
            for (ty in 0 until part.height / 8) for (tx in 0 until part.width / 8) {
                val tileStart = base + (ty * (part.width / 8) + tx) * tileBytes
                for (p in 0 until 64) {
                    val at = if (bpp == 4) tileStart + p / 2 else tileStart + p
                    if (at >= end) continue
                    val raw = graphic[at].toInt() and 0xFF
                    val index = if (bpp == 4) (raw shr ((p % 2) * 4)) and 0xF else raw
                    if (index == 0) continue
                    val px = part.x - left + tx * 8 + p % 8
                    val py = part.y - top + ty * 8 + p / 8
                    out[py * width + px] = colors.getOrElse(index) { 0 }
                }
            }
        }
        return RgbaImage(width, height, out)
    }

    companion object {
        private val SIZES = arrayOf(
            arrayOf(8 to 8, 16 to 16, 32 to 32, 64 to 64),
            arrayOf(16 to 8, 32 to 8, 32 to 16, 64 to 32),
            arrayOf(8 to 16, 8 to 32, 16 to 32, 32 to 64),
        )

        fun parse(bytes: ByteArray): FoxResult<Ncer> {
            if (bytes.size < 0x20 || bytes.decodeToString(0, 4) != "RECN") return FoxResult.Err("Not an NCER file")
            val section = findSection(bytes, "KBEC") ?: return FoxResult.Err("NCER has no cell bank")
            val cellCount = bytes.u16(section + 8)
            val hasBounds = bytes.u16(section + 10) and 1 != 0
            val cellsAt = section + 8 + bytes.u32(section + 12).toInt()
            val mapping = bytes.u32(section + 16).toInt()
            val cellSize = if (hasBounds) 16 else 8
            val oamAt = cellsAt + cellCount * cellSize
            val cells = (0 until cellCount).map { c ->
                val at = cellsAt + c * cellSize
                if (at + 8 > bytes.size) return FoxResult.Err("NCER cell $c is outside the file")
                val count = bytes.u16(at)
                val first = oamAt + bytes.u32(at + 4).toInt()
                if (first + count * 6 > bytes.size) return FoxResult.Err("NCER cell $c has OAMs outside the file")
                (0 until count).map { i ->
                    val attr0 = bytes.u16(first + i * 6)
                    val attr1 = bytes.u16(first + i * 6 + 2)
                    val attr2 = bytes.u16(first + i * 6 + 4)
                    val shape = attr0 shr 14
                    if (shape > 2) return FoxResult.Err("NCER cell $c has an OAM with an invalid shape")
                    val (w, h) = SIZES[shape][attr1 shr 14]
                    Oam(
                        x = (attr1 and 0x1FF).let { if (it > 0xFF) it - 0x200 else it },
                        y = (attr0 and 0xFF).let { if (it > 0x7F) it - 0x100 else it },
                        width = w,
                        height = h,
                        tile = attr2 and 0x3FF,
                        palette = attr2 shr 12,
                    )
                }
            }
            return FoxResult.Ok(Ncer(cells, mapping))
        }
    }
}
