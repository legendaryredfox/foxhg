package io.github.legendaryredfox.foxhg.core.data.map

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.parsing
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** Position on a map: a 16.16 fixed point number stored as `u16 fraction, s16 whole`. */
data class FixedPos(val fraction: Int, val whole: Int) {
    val value: Double get() = whole + fraction / 65536.0

    companion object {
        /** The nearest position to [value] that fits (whole part -32768..32767). */
        fun of(value: Double): FixedPos {
            val clamped = value.coerceIn(-32768.0, 32767.99998)
            val whole = kotlin.math.floor(clamped).toInt()
            return FixedPos(((clamped - whole) * 65536.0).toInt().coerceIn(0, 65535), whole)
        }
    }
}

/**
 * A 3D building placed on a map (48 bytes): model id, position (x, then two more fixed point coordinates),
 * rotations, scales and unused bytes that are kept as they are.
 */
data class Building(
    val modelId: Long,
    val x: FixedPos,
    val y: FixedPos,
    val z: FixedPos,
    val rotationX: Long,
    val rotationY: Long,
    val rotationZ: Long,
    val width: Long,
    val height: Long,
    val length: Long,
    /** Bytes 28 and 41..47, which no one uses. */
    val unused: List<Int>,
) {
    internal fun write(b: ByteArray, p: Int) {
        b.putU32(p, modelId)
        listOf(x, y, z).forEachIndexed { i, v -> b.putU16(p + 4 + i * 4, v.fraction); b.putU16(p + 6 + i * 4, v.whole) }
        b.putU32(p + 16, rotationX); b.putU32(p + 20, rotationY); b.putU32(p + 24, rotationZ)
        b[p + 28] = unused[0].toByte()
        b.putU32(p + 29, width); b.putU32(p + 33, height); b.putU32(p + 37, length)
        for (i in 1..7) b[p + 40 + i] = unused[i].toByte()
    }

    companion object {
        const val SIZE = 48
        fun read(b: ByteArray, p: Int) = Building(
            modelId = b.u32(p),
            x = FixedPos(b.u16(p + 4), b.u16(p + 6).toShort().toInt()),
            y = FixedPos(b.u16(p + 8), b.u16(p + 10).toShort().toInt()),
            z = FixedPos(b.u16(p + 12), b.u16(p + 14).toShort().toInt()),
            rotationX = b.u32(p + 16), rotationY = b.u32(p + 20), rotationZ = b.u32(p + 24),
            width = b.u32(p + 29), height = b.u32(p + 33), length = b.u32(p + 37),
            unused = listOf(b[p + 28].toInt() and 0xFF) + (1..7).map { b[p + 40 + it].toInt() and 0xFF },
        )
    }
}

/**
 * One map (a file of `a/0/6/5`): a 16-byte header of four section sizes, the background sound block (starts with
 * 0x1234 and its own size), the movement permissions of the 32 x 32 tiles, the buildings, the NSBMD terrain model and
 * the BDHC terrain heights. The model, BDHC and [sound] (including its 4-byte head) are kept as bytes.
 *
 * [permissions] are `u8 type, u8 collision` per tile, row by row: tile (x, y) is at `(y * 32 + x) * 2`. Collision bit
 * 0x80 blocks the tile; the low bits are other flags (4 makes grass rustle, for example).
 */
class MapFile(
    val sound: ByteArray,
    val permissions: ByteArray,
    val buildings: List<Building>,
    val model: ByteArray,
    val bdhc: ByteArray,
) {
    /** The offset of tile (x, y) in [permissions]; a tile outside the map would read another row's tile, so it is refused. */
    private fun tileOffset(x: Int, y: Int): Int {
        require(x in 0 until SIZE && y in 0 until SIZE) { "Tile $x, $y is outside the $SIZE x $SIZE map" }
        return (y * SIZE + x) * 2
    }

    fun type(x: Int, y: Int): Int = permissions[tileOffset(x, y)].toInt() and 0xFF
    fun collision(x: Int, y: Int): Int = permissions[tileOffset(x, y) + 1].toInt() and 0xFF

    /** The walking surface in [bdhc], decoded; the section itself is kept as bytes. */
    fun walkingSurface(): FoxResult<Bdhc> = Bdhc.parse(bdhc)

    fun withTile(x: Int, y: Int, type: Int? = null, collision: Int? = null): MapFile {
        val p = permissions.copyOf()
        val i = tileOffset(x, y)
        type?.let { p[i] = it.toByte() }
        collision?.let { p[i + 1] = it.toByte() }
        return MapFile(sound, p, buildings, model, bdhc)
    }

    /**
     * Flood fill from (x, y) over the connected tiles (4 neighbours) that have the same value as it in every field that
     * is given: [type] and/or [collision] are both the new value and the field to compare.
     */
    fun filled(x: Int, y: Int, type: Int? = null, collision: Int? = null): MapFile {
        if (type == null && collision == null) return this
        val startType = type(x, y)
        val startCollision = collision(x, y)
        if ((type == null || startType == type) && (collision == null || startCollision == collision)) return this
        val p = permissions.copyOf()
        val seen = BooleanArray(SIZE * SIZE)
        val work = ArrayDeque<Int>().also { it.add(y * SIZE + x); seen[y * SIZE + x] = true }
        while (work.isNotEmpty()) {
            val t = work.removeFirst()
            type?.let { p[t * 2] = it.toByte() }
            collision?.let { p[t * 2 + 1] = it.toByte() }
            val tx = t % SIZE
            val ty = t / SIZE
            for ((nx, ny) in listOf(tx - 1 to ty, tx + 1 to ty, tx to ty - 1, tx to ty + 1)) {
                if (nx !in 0 until SIZE || ny !in 0 until SIZE || seen[ny * SIZE + nx]) continue
                if ((type != null && type(nx, ny) != startType) || (collision != null && collision(nx, ny) != startCollision)) continue
                seen[ny * SIZE + nx] = true
                work.add(ny * SIZE + nx)
            }
        }
        return MapFile(sound, p, buildings, model, bdhc)
    }

    fun withBuildings(list: List<Building>) = MapFile(sound, permissions, list, model, bdhc)

    fun toBytes(): ByteArray {
        val out = ByteArray(16 + sound.size + PERMISSION_BYTES + buildings.size * Building.SIZE + model.size + bdhc.size)
        out.putU32(0, PERMISSION_BYTES.toLong()); out.putU32(4, (buildings.size * Building.SIZE).toLong())
        out.putU32(8, model.size.toLong()); out.putU32(12, bdhc.size.toLong())
        var p = 16
        sound.copyInto(out, p); p += sound.size
        permissions.copyInto(out, p); p += PERMISSION_BYTES
        for (b in buildings) { b.write(out, p); p += Building.SIZE }
        model.copyInto(out, p); p += model.size
        bdhc.copyInto(out, p)
        return out
    }

    override fun equals(other: Any?) = other is MapFile && sound.contentEquals(other.sound) && permissions.contentEquals(other.permissions) &&
        buildings == other.buildings && model.contentEquals(other.model) && bdhc.contentEquals(other.bdhc)

    override fun hashCode() = ((permissions.contentHashCode() * 31 + buildings.hashCode()) * 31 + model.contentHashCode()) * 31 + bdhc.contentHashCode()

    companion object {
        const val SIZE = 32
        const val PERMISSION_BYTES = SIZE * SIZE * 2
        const val SOUND_SIGNATURE = 0x1234

        fun parse(bytes: ByteArray): FoxResult<MapFile> = parsing("The map file") { parseChecked(bytes) }

        private fun parseChecked(bytes: ByteArray): FoxResult<MapFile> {
            if (bytes.size < 20) return FoxResult.Err("Map file is shorter than its header")
            val permissionLength = bytes.u32(0)
            val buildingLong = bytes.u32(4)
            val modelLong = bytes.u32(8)
            val bdhcLong = bytes.u32(12)
            val soundSum = 4 + bytes.u16(18).toLong() + PERMISSION_BYTES + buildingLong + modelLong + bdhcLong
            // Compared as longs: three huge lengths must not wrap around to the file size.
            if (bytes.size < 20 || 16 + soundSum != bytes.size.toLong()) return FoxResult.Err("Map file: section sizes do not add up to the file size")
            val buildingLength = buildingLong.toInt()
            val modelLength = modelLong.toInt()
            val bdhcLength = bdhcLong.toInt()
            if (permissionLength != PERMISSION_BYTES.toLong()) return FoxResult.Err("Map file: permissions section is $permissionLength bytes, expected $PERMISSION_BYTES")
            if (buildingLength % Building.SIZE != 0) return FoxResult.Err("Map file: buildings section is not a multiple of ${Building.SIZE}")
            if (bytes.u16(16) != SOUND_SIGNATURE) return FoxResult.Err("Map file: no background sound block")
            val soundLength = 4 + bytes.u16(18)
            var p = 16
            if (p + soundLength + PERMISSION_BYTES + buildingLength + modelLength + bdhcLength != bytes.size) return FoxResult.Err("Map file: section sizes do not add up to the file size")
            fun take(n: Int) = bytes.copyOfRange(p, p + n).also { p += n }
            val sound = take(soundLength)
            val permissions = take(PERMISSION_BYTES)
            val buildings = List(buildingLength / Building.SIZE) { Building.read(bytes, p + it * Building.SIZE) }
            p += buildingLength
            val model = take(modelLength)
            val bdhc = take(bdhcLength)
            return FoxResult.Ok(MapFile(sound, permissions, buildings, model, bdhc))
        }

        val SPEC = RecordSpec(HgssLayout.MAPS, ::parse, MapFile::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<MapFile>> = NarcRecords.load(project, SPEC)
    }
}
