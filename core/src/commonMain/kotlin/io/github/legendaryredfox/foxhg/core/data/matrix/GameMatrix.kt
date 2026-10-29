package io.github.legendaryredfox.foxhg.core.data.matrix

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u8

/**
 * A map matrix (`a/0/0/9`): the grid of map files that makes up an area. Layout (DSPRE `GameMatrix`, DS Pokémon Hacking
 * wiki): width, height, "has headers" byte, "has altitudes" byte, name length, name (UTF-8), then row-major grids of
 * u16 header ids (optional), u8 altitudes (optional) and u16 map-file ids. [EMPTY] marks a cell with no map.
 *
 * Cells are stored row by row: index = y * width + x. [nameBytes] keeps the name exactly as stored.
 */
class GameMatrix(
    val width: Int,
    val height: Int,
    val nameBytes: ByteArray,
    val headers: IntArray?,
    val altitudes: IntArray?,
    val maps: IntArray,
    /** Bytes after the last grid (none in vanilla); kept so a record round-trips whatever it holds. */
    val trailing: ByteArray = ByteArray(0),
) {
    init {
        require(width in 1..255 && height in 1..255) { "matrix size $width x $height is outside 1..255" }
        require(maps.size == width * height) { "maps has ${maps.size} cells, expected ${width * height}" }
        require(headers == null || headers.size == maps.size) { "headers grid has the wrong size" }
        require(altitudes == null || altitudes.size == maps.size) { "altitudes grid has the wrong size" }
        require(nameBytes.size <= 255) { "name is longer than 255 bytes" }
    }

    val name: String get() = nameBytes.decodeToString()
    val hasHeaders: Boolean get() = headers != null
    val hasAltitudes: Boolean get() = altitudes != null

    fun map(x: Int, y: Int): Int = maps[y * width + x]
    fun header(x: Int, y: Int): Int? = headers?.get(y * width + x)
    fun altitude(x: Int, y: Int): Int? = altitudes?.get(y * width + x)

    fun withMap(x: Int, y: Int, value: Int): GameMatrix = copyWith(maps = maps.copyOf().also { it[y * width + x] = value and 0xFFFF })
    fun withHeader(x: Int, y: Int, value: Int): GameMatrix =
        if (headers == null) this else copyWith(headers = headers.copyOf().also { it[y * width + x] = value and 0xFFFF })
    fun withAltitude(x: Int, y: Int, value: Int): GameMatrix =
        if (altitudes == null) this else copyWith(altitudes = altitudes.copyOf().also { it[y * width + x] = value and 0xFF })
    fun withName(newName: String): GameMatrix = copyWith(nameBytes = newName.encodeToByteArray().copyOf(minOf(255, newName.encodeToByteArray().size)))

    /** Adds or removes the optional sections (new ones start at 0). */
    fun withSections(headers: Boolean, altitudes: Boolean): GameMatrix = GameMatrix(
        width, height, nameBytes,
        if (headers) (this.headers ?: IntArray(maps.size)) else null,
        if (altitudes) (this.altitudes ?: IntArray(maps.size)) else null,
        maps, trailing,
    )

    /** A matrix of another size; cells outside the old one start empty (map [EMPTY], header and altitude 0). */
    fun resized(newWidth: Int, newHeight: Int): GameMatrix {
        fun <T> remap(old: IntArray?, fill: Int): IntArray? = old?.let { src ->
            IntArray(newWidth * newHeight) { i ->
                val x = i % newWidth
                val y = i / newWidth
                if (x < width && y < height) src[y * width + x] else fill
            }
        }
        return GameMatrix(newWidth, newHeight, nameBytes, remap<Int>(headers, 0), remap<Int>(altitudes, 0), remap<Int>(maps, EMPTY)!!, trailing)
    }

    private fun copyWith(
        nameBytes: ByteArray = this.nameBytes,
        headers: IntArray? = this.headers,
        altitudes: IntArray? = this.altitudes,
        maps: IntArray = this.maps,
    ) = GameMatrix(width, height, nameBytes, headers, altitudes, maps, trailing)

    fun toBytes(): ByteArray {
        val cells = width * height
        val size = 5 + nameBytes.size + (if (headers != null) 2 * cells else 0) + (if (altitudes != null) cells else 0) + 2 * cells + trailing.size
        val out = ByteArray(size)
        out[0] = width.toByte(); out[1] = height.toByte()
        out[2] = if (headers != null) 1 else 0
        out[3] = if (altitudes != null) 1 else 0
        out[4] = nameBytes.size.toByte()
        nameBytes.copyInto(out, 5)
        var p = 5 + nameBytes.size
        headers?.forEach { out.putU16(p, it); p += 2 }
        altitudes?.forEach { out[p++] = it.toByte() }
        maps.forEach { out.putU16(p, it); p += 2 }
        trailing.copyInto(out, p)
        return out
    }

    override fun equals(other: Any?) = other is GameMatrix && width == other.width && height == other.height &&
        nameBytes.contentEquals(other.nameBytes) && headers.contentEqualsOrNull(other.headers) &&
        altitudes.contentEqualsOrNull(other.altitudes) && maps.contentEquals(other.maps) && trailing.contentEquals(other.trailing)

    override fun hashCode() = (width * 31 + height) * 31 + maps.contentHashCode()

    private fun IntArray?.contentEqualsOrNull(o: IntArray?) = if (this == null || o == null) this == null && o == null else contentEquals(o)

    companion object {
        const val EMPTY = 0xFFFF

        fun parse(bytes: ByteArray): FoxResult<GameMatrix> {
            if (bytes.size < 5) return FoxResult.Err("Matrix shorter than its header")
            val width = bytes.u8(0)
            val height = bytes.u8(1)
            if (width == 0 || height == 0) return FoxResult.Err("Matrix has size $width x $height")
            val hasHeaders = bytes.u8(2) != 0
            val hasAltitudes = bytes.u8(3) != 0
            val nameLength = bytes.u8(4)
            val cells = width * height
            val need = 5 + nameLength + (if (hasHeaders) 2 * cells else 0) + (if (hasAltitudes) cells else 0) + 2 * cells
            if (bytes.size < need) return FoxResult.Err("Matrix needs $need bytes but has ${bytes.size}")
            var p = 5
            val nameBytes = bytes.copyOfRange(p, p + nameLength)
            p += nameLength
            val headers = if (hasHeaders) IntArray(cells) { bytes.u16(p + 2 * it) }.also { p += 2 * cells } else null
            val altitudes = if (hasAltitudes) IntArray(cells) { bytes.u8(p + it) }.also { p += cells } else null
            val maps = IntArray(cells) { bytes.u16(p + 2 * it) }
            p += 2 * cells
            return FoxResult.Ok(GameMatrix(width, height, nameBytes, headers, altitudes, maps, bytes.copyOfRange(p, bytes.size)))
        }

        val SPEC = RecordSpec({ it.matrices }, ::parse, GameMatrix::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<GameMatrix>> = NarcRecords.load(project, SPEC)
    }
}
