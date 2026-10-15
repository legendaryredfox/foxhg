package io.github.romulofer.foxhg.core.data.map

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/** A corner of a plate: [x] and [z] in 16.16 fixed point, tiles from the middle of the map (z grows to the south). */
data class BdhcPoint(val x: Int, val z: Int) {
    val tileX: Double get() = x / 65536.0
    val tileZ: Double get() = z / 65536.0
}

/** The slope a plate has, as a vector in 4096ths: [x] and [z] across the map and [up] (4096 for a flat plate). */
data class BdhcSlope(val x: Int, val up: Int, val z: Int) {
    val isFlat: Boolean get() = x == 0 && z == 0
}

/**
 * A rectangle of walkable ground: two corner points (indices into [Bdhc.points]), a slope and a height (indices into
 * [Bdhc.slopes] and [Bdhc.heights]).
 */
data class BdhcPlate(val corner1: Int, val corner2: Int, val slope: Int, val height: Int)

/** One row of the lookup grid: from [z] (16.16, tiles) southwards, the plates in [Bdhc.plateIndices] from [first] on, [count] of them. */
data class BdhcStripe(val z: Int, val count: Int, val first: Int)

/**
 * BDHC: the walking surface of a map, the last section of a map file. Plates are rectangles with a slope and a height;
 * stripes are a lookup grid so the game finds the plates under a point without scanning all of them.
 *
 * Layout: `BDHC`, u16 points, slopes, heights, plates, stripes, plate indices; then 8 byte points (s32 x, s32 z),
 * 12 byte slopes (s32 x, up, z), s32 heights (16.16, **negated**: a flat plate of height -1.0 is ground at y = 1.0), 8 byte
 * plates (4 x u16), 8 byte stripes (s32 z, u16 count, u16 first) and u16 plate indices. Every map of HeartGold parses and
 * writes back byte for byte.
 */
class Bdhc(
    val points: List<BdhcPoint>,
    val slopes: List<BdhcSlope>,
    /** 16.16 fixed point, as stored (so the ground height is the negative). */
    val heights: List<Int>,
    val plates: List<BdhcPlate>,
    val stripes: List<BdhcStripe>,
    val plateIndices: List<Int>,
) {
    /** The ground height (tiles, y up) of a flat [plate], or null for a sloped one. */
    fun flatHeight(plate: BdhcPlate): Double? =
        slopes.getOrNull(plate.slope)?.takeIf { it.isFlat }?.let { -(heights.getOrNull(plate.height) ?: return null) / 65536.0 }

    /** The corners of [plate]'s rectangle: west, north, east, south in tiles from the middle of the map. */
    fun rectangle(plate: BdhcPlate): DoubleArray? {
        val a = points.getOrNull(plate.corner1) ?: return null
        val b = points.getOrNull(plate.corner2) ?: return null
        return doubleArrayOf(a.tileX, a.tileZ, b.tileX, b.tileZ)
    }

    fun toBytes(): ByteArray {
        val out = ByteArray(HEADER + points.size * 8 + slopes.size * 12 + heights.size * 4 + plates.size * 8 + stripes.size * 8 + plateIndices.size * 2)
        "BDHC".encodeToByteArray().copyInto(out)
        out.putU16(4, points.size); out.putU16(6, slopes.size); out.putU16(8, heights.size)
        out.putU16(10, plates.size); out.putU16(12, stripes.size); out.putU16(14, plateIndices.size)
        var p = HEADER
        for (pt in points) { out.putU32(p, pt.x.toLong() and 0xFFFFFFFFL); out.putU32(p + 4, pt.z.toLong() and 0xFFFFFFFFL); p += 8 }
        for (s in slopes) { for ((i, v) in listOf(s.x, s.up, s.z).withIndex()) out.putU32(p + i * 4, v.toLong() and 0xFFFFFFFFL); p += 12 }
        for (h in heights) { out.putU32(p, h.toLong() and 0xFFFFFFFFL); p += 4 }
        for (pl in plates) { out.putU16(p, pl.corner1); out.putU16(p + 2, pl.corner2); out.putU16(p + 4, pl.slope); out.putU16(p + 6, pl.height); p += 8 }
        for (st in stripes) { out.putU32(p, st.z.toLong() and 0xFFFFFFFFL); out.putU16(p + 4, st.count); out.putU16(p + 6, st.first); p += 8 }
        for (i in plateIndices) { out.putU16(p, i); p += 2 }
        return out
    }

    companion object {
        private const val HEADER = 16

        fun parse(b: ByteArray): FoxResult<Bdhc> {
            if (b.size < HEADER || b.decodeToString(0, 4) != "BDHC") return FoxResult.Err("Not a BDHC section")
            val nPoints = b.u16(4); val nSlopes = b.u16(6); val nHeights = b.u16(8)
            val nPlates = b.u16(10); val nStripes = b.u16(12); val nIndices = b.u16(14)
            val expected = HEADER + nPoints * 8 + nSlopes * 12 + nHeights * 4 + nPlates * 8 + nStripes * 8 + nIndices * 2
            if (expected != b.size) return FoxResult.Err("BDHC: the counts say $expected bytes, the section has ${b.size}")
            var p = HEADER
            fun s32(at: Int) = b.u32(at).toInt()
            val points = (0 until nPoints).map { BdhcPoint(s32(p + it * 8), s32(p + it * 8 + 4)) }.also { p += nPoints * 8 }
            val slopes = (0 until nSlopes).map { BdhcSlope(s32(p + it * 12), s32(p + it * 12 + 4), s32(p + it * 12 + 8)) }.also { p += nSlopes * 12 }
            val heights = (0 until nHeights).map { s32(p + it * 4) }.also { p += nHeights * 4 }
            val plates = (0 until nPlates).map { BdhcPlate(b.u16(p + it * 8), b.u16(p + it * 8 + 2), b.u16(p + it * 8 + 4), b.u16(p + it * 8 + 6)) }.also { p += nPlates * 8 }
            val stripes = (0 until nStripes).map { BdhcStripe(s32(p + it * 8), b.u16(p + it * 8 + 4), b.u16(p + it * 8 + 6)) }.also { p += nStripes * 8 }
            val indices = (0 until nIndices).map { b.u16(p + it * 2) }
            return FoxResult.Ok(Bdhc(points, slopes, heights, plates, stripes, indices))
        }
    }
}
