package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/** A place on a map: [map] is the map header number, [warp] the warp it came through (-1 for none), ([x], [y]) the tile and [direction] 0 north, 1 south, 2 west, 3 east. The decomp's `Location`: five 32-bit words. */
data class Location(val map: Int, val warp: Int, val x: Int, val y: Int, val direction: Int) {
    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        b.putU32(0, map.toLong() and 0xFFFFFFFFL); b.putU32(4, warp.toLong() and 0xFFFFFFFFL)
        b.putU32(8, x.toLong() and 0xFFFFFFFFL); b.putU32(12, y.toLong() and 0xFFFFFFFFL); b.putU32(16, direction.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        const val SIZE = 20
        val DIRECTIONS = listOf("North", "South", "West", "East")

        fun read(b: ByteArray, at: Int) = Location(b.u32(at).toInt(), b.u32(at + 4).toInt(), b.u32(at + 8).toInt(), b.u32(at + 12).toInt(), b.u32(at + 16).toInt())
    }
}

/**
 * The `LOCAL_FIELD_DATA` chunk (0x80 bytes): where the player is and how the field is set. Layout from the decomp's `struct LocalFieldData`
 * (`save_local_field_data.c`); the five places, the warp-less -1, the music, weather, camera and the rest were read on real saves, and the position
 * was changed with foxhg and followed by the game (`MovedSaveInGameEmulatorTest`). Bytes that are not fields ([gap] after the camera type, [filler]
 * at the end) are kept so an unedited chunk writes back unchanged.
 */
data class LocalFieldData(
    /** Where the player stands now: what the game puts them at when the save is continued. */
    val current: Location,
    val entrance: Location,
    val previous: Location,
    val dynamicWarp: Location,
    val specialSpawn: Location,
    val musicId: Int,
    val weather: Int,
    val lastSpawn: Int,
    val cameraType: Int,
    val gap: Int,
    val hasRunningShoes: Int,
    val runningShoesLock: Int,
    val playerState: Int,
    val poisonSteps: Int,
    val safariSteps: Int,
    val safariBalls: Int,
    val filler: List<Int>,
) {
    /** A copy that stands at [place] now, with the place "before" set to it as well, as the game leaves them right after a save. */
    fun movedTo(place: Location): LocalFieldData = copy(current = place, previous = place)

    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        listOf(current, entrance, previous, dynamicWarp, specialSpawn).forEachIndexed { i, l -> l.toBytes().copyInto(b, i * Location.SIZE) }
        b.putU16(0x64, musicId); b.putU16(0x66, weather); b.putU16(0x68, lastSpawn); b[0x6A] = cameraType.toByte(); b[0x6B] = gap.toByte()
        b.putU16(0x6C, hasRunningShoes); b.putU16(0x6E, runningShoesLock); b.putU32(0x70, playerState.toLong() and 0xFFFFFFFFL)
        b.putU16(0x74, poisonSteps); b.putU16(0x76, safariSteps); b.putU16(0x78, safariBalls)
        filler.forEachIndexed { i, v -> b[0x7A + i] = v.toByte() }
    }

    companion object {
        const val SIZE = 0x80
        const val FILLER = 6

        fun parse(b: ByteArray): FoxResult<LocalFieldData> {
            if (b.size != SIZE) return FoxResult.Err("The field data is $SIZE bytes, got ${b.size}")
            return FoxResult.Ok(
                LocalFieldData(
                    current = Location.read(b, 0), entrance = Location.read(b, 20), previous = Location.read(b, 40), dynamicWarp = Location.read(b, 60), specialSpawn = Location.read(b, 80),
                    musicId = b.u16(0x64), weather = b.u16(0x66), lastSpawn = b.u16(0x68), cameraType = b.u8(0x6A), gap = b.u8(0x6B),
                    hasRunningShoes = b.u16(0x6C), runningShoesLock = b.u16(0x6E), playerState = b.u32(0x70).toInt(),
                    poisonSteps = b.u16(0x74), safariSteps = b.u16(0x76), safariBalls = b.u16(0x78), filler = List(FILLER) { b.u8(0x7A + it) },
                ),
            )
        }

        fun read(save: SaveFile): FoxResult<LocalFieldData> {
            val block = save.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.LOCAL_FIELD_DATA)))
        }
    }
}

/**
 * A copy of the save that Continue starts at [place]: the position in the field data (current, previous and the dynamic warp) and the event flag
 * 0x966 set. The game restores the player and the NPCs from the saved map objects when it continues, so a changed position alone leaves the player at the
 * old tile; with 0x966 set it takes the dynamic warp instead and builds the map's NPCs from the map's own events (it clears the flag again), which is
 * the game's own way to start somewhere else. The dynamic warp is a place scripts such as the Union Room exit use, so it stays [place] afterwards.
 */
fun SaveFile.movedTo(place: Location): FoxResult<SaveFile> {
    val field = when (val r = LocalFieldData.read(this)) {
        is FoxResult.Ok -> r.value
        is FoxResult.Err -> return r
    }
    val placed = when (val r = withLocalFieldData(field.movedTo(place).copy(dynamicWarp = place))) {
        is FoxResult.Ok -> r.value
        is FoxResult.Err -> return r
    }
    val block = placed.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    val flags = SaveChunks.byId(SaveChunks.FLAGS)
    return FoxResult.Ok(placed.withChunk(block, flags, SaveVarsFlags.with(placed.chunk(block, flags), SaveVarsFlags.CONTINUE_AT_DYNAMIC_WARP, true)))
}

/** A copy of the save with [field] written into the current general block (its checksums recomputed). */
fun SaveFile.withLocalFieldData(field: LocalFieldData): FoxResult<SaveFile> {
    val block = current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.LOCAL_FIELD_DATA), field.toBytes()))
}
