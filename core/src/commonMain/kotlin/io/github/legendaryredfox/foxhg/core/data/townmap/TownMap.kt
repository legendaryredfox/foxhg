package io.github.legendaryredfox.foxhg.core.data.townmap

import io.github.legendaryredfox.foxhg.core.game.GameLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u8

/**
 * One town of the PokeGear map that is drawn red once visited and replaced by a gray block before (14 bytes, wiki `GearMapTownOverlay`).
 * Positions are tile positions in the map picture (8x8 pixel tiles); [redX] and [redY] are stored one tile off (the picture position is
 * `redX - 1`, `redY + 1`). [padding] and the other bytes the wiki does not explain are kept as they are.
 */
data class TownMapSpot(
    val mapHeader: Int, val gateHeader: Int, val entry: Int, val visitedEntry: Int,
    val redX: Int, val redY: Int, val grayX: Int, val grayY: Int,
    val townDimX: Int, val townDimY: Int, val replacementDimX: Int, val replacementDimY: Int, val offsetX: Int, val offsetY: Int,
    val padding: Int = 0,
) {
    internal fun check(): String? = when {
        mapHeader !in 0..0xFFFF || gateHeader !in 0..0xFFFF -> "A map header is 0 to 65535"
        listOf(entry, visitedEntry, redX, redY, grayX, grayY, padding).any { it !in 0..255 } -> "Entries and positions are 0 to 255"
        listOf(townDimX, townDimY, replacementDimX, replacementDimY, offsetX, offsetY).any { it !in 0..15 } -> "Sizes and offsets are 0 to 15"
        else -> null
    }

    internal fun write(out: ByteArray, at: Int) {
        out.putU16(at, mapHeader); out.putU16(at + 2, gateHeader)
        out[at + 4] = entry.toByte(); out[at + 5] = visitedEntry.toByte()
        out[at + 6] = redX.toByte(); out[at + 7] = redY.toByte(); out[at + 8] = grayX.toByte(); out[at + 9] = grayY.toByte()
        out[at + 10] = ((townDimY shl 4) or townDimX).toByte()
        out[at + 11] = ((replacementDimY shl 4) or replacementDimX).toByte()
        out[at + 12] = ((offsetY shl 4) or offsetX).toByte()
        out[at + 13] = padding.toByte()
    }

    companion object {
        const val SIZE = 14

        internal fun read(b: ByteArray, at: Int) = TownMapSpot(
            mapHeader = b.u16(at), gateHeader = b.u16(at + 2), entry = b.u8(at + 4), visitedEntry = b.u8(at + 5),
            redX = b.u8(at + 6), redY = b.u8(at + 7), grayX = b.u8(at + 8), grayY = b.u8(at + 9),
            townDimX = b.u8(at + 10) and 15, townDimY = b.u8(at + 10) shr 4,
            replacementDimX = b.u8(at + 11) and 15, replacementDimY = b.u8(at + 11) shr 4,
            offsetX = b.u8(at + 12) and 15, offsetY = b.u8(at + 12) shr 4,
            padding = b.u8(at + 13),
        )
    }
}

/**
 * One selectable block of the PokeGear map: the orange overlay shown when the cursor is on a town or route (16 bytes, wiki
 * `GearMapTownSelectionOverlay`). [baseX], [baseY], [dimX] and [dimY] place it in the PokeGear screen; the orange fields place its picture in
 * the map graphics. [textEntry] picks the blurb in text bank 273 and a non-zero [flySpot] marks a town the player can fly to. [flags] and
 * [padding] (a u32) are kept as they are: what the flags do is not documented.
 */
data class TownMapSelection(
    val mapHeader: Int, val baseX: Int, val baseY: Int, val dimX: Int, val dimY: Int, val flags: Int, val textEntry: Int, val flySpot: Int,
    val orangeX: Int, val orangeY: Int, val orangeDimX: Int, val orangeDimY: Int, val padding: Long = 0,
) {
    internal fun check(): String? = when {
        mapHeader !in 0..0xFFFF -> "A map header is 0 to 65535"
        listOf(baseX, baseY, flags, textEntry, flySpot, orangeX, orangeY, orangeDimX, orangeDimY).any { it !in 0..255 } -> "Positions, sizes and flags are 0 to 255"
        dimX !in 0..15 || dimY !in 0..15 -> "A selection size is 0 to 15"
        padding !in 0..0xFFFFFFFFL -> "The padding is a 32 bit number"
        else -> null
    }

    internal fun write(out: ByteArray, at: Int) {
        out.putU16(at, mapHeader)
        out[at + 2] = baseX.toByte(); out[at + 3] = baseY.toByte()
        out[at + 4] = ((dimY shl 4) or dimX).toByte()
        out[at + 5] = flags.toByte(); out[at + 6] = textEntry.toByte(); out[at + 7] = flySpot.toByte()
        for (i in 0 until 4) out[at + 8 + i] = (padding shr (8 * i)).toByte()
        out[at + 12] = orangeX.toByte(); out[at + 13] = orangeY.toByte(); out[at + 14] = orangeDimX.toByte(); out[at + 15] = orangeDimY.toByte()
    }

    companion object {
        const val SIZE = 16

        internal fun read(b: ByteArray, at: Int) = TownMapSelection(
            mapHeader = b.u16(at), baseX = b.u8(at + 2), baseY = b.u8(at + 3), dimX = b.u8(at + 4) and 15, dimY = b.u8(at + 4) shr 4,
            flags = b.u8(at + 5), textEntry = b.u8(at + 6), flySpot = b.u8(at + 7),
            orangeX = b.u8(at + 12), orangeY = b.u8(at + 13), orangeDimX = b.u8(at + 14), orangeDimY = b.u8(at + 15),
            padding = (0 until 4).fold(0L) { acc, i -> acc or (b.u8(at + 8 + i).toLong() shl (8 * i)) },
        )
    }
}

/**
 * The two PokeGear map tables of overlay 101 (HeartGold English): the 27 visited-town entries at 0x10274 and the 100 selection entries at
 * 0xFC32. Both have a fixed length, so entries can be changed but not added. See the wiki pages "Editing the Town Map" and "Town Map Spots".
 */
data class TownMap(val spots: List<TownMapSpot>, val selections: List<TownMapSelection>) {
    fun withSpot(index: Int, spot: TownMapSpot) = copy(spots = spots.toMutableList().also { it[index] = spot })
    fun withSelection(index: Int, selection: TownMapSelection) = copy(selections = selections.toMutableList().also { it[index] = selection })

    /** Writes both tables into a copy of [overlay]. */
    fun applyTo(overlay: ByteArray, layout: GameLayout): FoxResult<ByteArray> {
        if (spots.size != layout.townMapSpots || selections.size != layout.townMapSelections) return FoxResult.Err("The town map tables have a fixed length")
        if (overlay.size < end(layout)) return FoxResult.Err("Overlay ${layout.townMapOverlay} is too short for the town map tables")
        spots.forEachIndexed { i, s -> s.check()?.let { return FoxResult.Err("Town $i: $it") } }
        selections.forEachIndexed { i, s -> s.check()?.let { return FoxResult.Err("Selection $i: $it") } }
        val out = overlay.copyOf()
        spots.forEachIndexed { i, s -> s.write(out, layout.townMapSpotsOffset + i * TownMapSpot.SIZE) }
        selections.forEachIndexed { i, s -> s.write(out, layout.townMapSelectionsOffset + i * TownMapSelection.SIZE) }
        return FoxResult.Ok(out)
    }

    companion object {
        private fun end(layout: GameLayout) = layout.townMapSpotsOffset + layout.townMapSpots * TownMapSpot.SIZE

        fun parse(overlay: ByteArray, layout: GameLayout): FoxResult<TownMap> {
            if (overlay.size < end(layout)) return FoxResult.Err("Overlay ${layout.townMapOverlay} is too short for the town map tables")
            return FoxResult.Ok(TownMap(
                List(layout.townMapSpots) { TownMapSpot.read(overlay, layout.townMapSpotsOffset + it * TownMapSpot.SIZE) },
                List(layout.townMapSelections) { TownMapSelection.read(overlay, layout.townMapSelectionsOffset + it * TownMapSelection.SIZE) },
            ))
        }

        fun load(project: RomProject): FoxResult<TownMap> = when (val o = project.readOverlay(project.layout.townMapOverlay)) {
            is FoxResult.Ok -> parse(o.value, project.layout)
            is FoxResult.Err -> o
        }

        fun save(project: RomProject, map: TownMap): FoxResult<Unit> = when (val o = project.readOverlay(project.layout.townMapOverlay)) {
            is FoxResult.Ok -> when (val patched = map.applyTo(o.value, project.layout)) {
                is FoxResult.Ok -> project.writeOverlay(project.layout.townMapOverlay, patched.value)
                is FoxResult.Err -> patched
            }
            is FoxResult.Err -> o
        }
    }
}
