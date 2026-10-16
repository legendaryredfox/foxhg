package io.github.legendaryredfox.foxhg.core.data.mapheader

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.arm9.Arm9
import io.github.legendaryredfox.foxhg.core.nds.Game
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/**
 * One 24-byte HeartGold/SoulSilver map header. Only the fields foxhg needs are named; [areaProperties] and [flags32]
 * keep the packed bit fields (icon, weather, camera, battle background, ...) so a header round-trips exactly.
 */
data class MapHeader(
    /** Index of the wild-encounter file, or [NO_ENCOUNTERS]. */
    val wildEncounterFile: Int,
    val areaData: Int,
    val coordinates: Int,
    val matrix: Int,
    val script: Int,
    val levelScript: Int,
    val textArchive: Int,
    val musicDay: Int,
    val musicNight: Int,
    val eventFile: Int,
    /** Index into the location-name message bank. */
    val locationName: Int,
    val areaProperties: Int,
    val flags32: Long,
) {
    // --- packed fields (layout from DSPRE's HeaderHGSS; every bit of the three packed words is covered) -------------

    private fun bits(word: Long, shift: Int, width: Int): Int = ((word ushr shift) and ((1L shl width) - 1)).toInt()

    private fun setBits(word: Long, shift: Int, width: Int, value: Int): Long {
        val mask = ((1L shl width) - 1) shl shift
        return (word and mask.inv()) or ((value.toLong() shl shift) and mask)
    }

    /** First four bits of the coordinate word; unused by the game, kept for exactness. */
    val coordinateUnknown: Int get() = bits(coordinates.toLong(), 0, 4)
    val worldMapX: Int get() = bits(coordinates.toLong(), 4, 6)
    val worldMapY: Int get() = bits(coordinates.toLong(), 10, 6)
    val areaIcon: Int get() = areaProperties and 0xF
    val momCallIntro: Int get() = (areaProperties ushr 4) and 0xF
    /** Bit 0 of the flag word: the map belongs to Kanto. */
    val isKanto: Boolean get() = flags32 and 1L == 1L
    val weather: Int get() = bits(flags32, 1, 7)
    val locationType: Int get() = bits(flags32, 8, 4)
    val camera: Int get() = bits(flags32, 12, 6)
    val followMode: Int get() = bits(flags32, 18, 2)
    val battleBackground: Int get() = bits(flags32, 20, 5)
    /** Seven permission bits, see [Permission]. */
    val permissions: Int get() = bits(flags32, 25, 7)

    fun withWorldMap(x: Int, y: Int): MapHeader =
        copy(coordinates = setBits(setBits(coordinates.toLong(), 4, 6, x), 10, 6, y).toInt())
    fun withAreaIcon(v: Int): MapHeader = copy(areaProperties = (areaProperties and 0xF0) or (v and 0xF))
    fun withMomCallIntro(v: Int): MapHeader = copy(areaProperties = (areaProperties and 0x0F) or ((v and 0xF) shl 4))
    fun withKanto(v: Boolean): MapHeader = copy(flags32 = setBits(flags32, 0, 1, if (v) 1 else 0))
    fun withWeather(v: Int): MapHeader = copy(flags32 = setBits(flags32, 1, 7, v))
    fun withLocationType(v: Int): MapHeader = copy(flags32 = setBits(flags32, 8, 4, v))
    fun withCamera(v: Int): MapHeader = copy(flags32 = setBits(flags32, 12, 6, v))
    fun withFollowMode(v: Int): MapHeader = copy(flags32 = setBits(flags32, 18, 2, v))
    fun withBattleBackground(v: Int): MapHeader = copy(flags32 = setBits(flags32, 20, 5, v))
    fun withPermission(p: Permission, on: Boolean): MapHeader =
        copy(flags32 = setBits(flags32, 25, 7, if (on) permissions or (1 shl p.bit) else permissions and (1 shl p.bit).inv()))

    /** What a map allows. The first four are the field abilities; the rest are Pokégear features. */
    enum class Permission(val bit: Int, val label: String) {
        BICYCLE(0, "Bicycle"),
        RUNNING(1, "Running (unused)"),
        ESCAPE_ROPE(2, "Escape Rope"),
        FLY(3, "Fly"),
        CALLS_OUT(4, "Outgoing calls"),
        CALLS_IN(5, "Incoming calls"),
        RADIO(6, "Radio signal"),
    }

    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b[0] = wildEncounterFile.toByte(); b[1] = areaData.toByte()
        b.putU16(2, coordinates); b.putU16(4, matrix); b.putU16(6, script); b.putU16(8, levelScript)
        b.putU16(10, textArchive); b.putU16(12, musicDay); b.putU16(14, musicNight); b.putU16(16, eventFile)
        b[18] = locationName.toByte(); b[19] = areaProperties.toByte()
        b.putU32(20, flags32)
        return b
    }

    companion object {
        const val SIZE = 24
        const val NO_ENCOUNTERS = 0xFF

        fun parse(bytes: ByteArray, offset: Int): MapHeader = MapHeader(
            wildEncounterFile = bytes.u8(offset), areaData = bytes.u8(offset + 1), coordinates = bytes.u16(offset + 2),
            matrix = bytes.u16(offset + 4), script = bytes.u16(offset + 6), levelScript = bytes.u16(offset + 8),
            textArchive = bytes.u16(offset + 10), musicDay = bytes.u16(offset + 12), musicNight = bytes.u16(offset + 14),
            eventFile = bytes.u16(offset + 16), locationName = bytes.u8(offset + 18), areaProperties = bytes.u8(offset + 19),
            flags32 = bytes.u32(offset + 20),
        )
    }
}

/** Reads the map header table from the (decompressed) ARM9. Read-only: writing needs ARM9 replacement. */
object MapHeaderTable {
    /** Number of headers in the English HeartGold/SoulSilver table (the entries after it are not headers). */
    const val COUNT = 540

    /**
     * Offset of header 0 in the decompressed ARM9 image. HeartGold: confirmed (the dummy headers 0 and 1 start there,
     * the table has [COUNT] valid entries and the 135 encounter ids are all below the 142 encounter files).
     * SoulSilver: **[UNVERIFIED]**, the same offset: DSPRE's `RomInfo` uses one English offset for both games, as every other ARM9
     * offset here is shared (no SoulSilver ROM was available to check).
     */
    fun offsetFor(game: Game): Int? = when (game) {
        Game.HEARTGOLD_EN -> 0xF6BE0
        Game.SOULSILVER_EN -> 0xF6BE0
        else -> null
    }

    /** The headers including the project's ARM9 edits. */
    fun load(project: RomProject, count: Int = COUNT): FoxResult<List<MapHeader>> =
        when (val arm9 = project.readArm9()) {
            is FoxResult.Ok -> read(arm9.value, project.rom.header.game, count)
            is FoxResult.Err -> arm9
        }

    /** The headers as the ROM originally has them, ignoring edits. */
    fun original(project: RomProject, count: Int = COUNT): List<MapHeader> {
        val plain = (Arm9.decompressed(project.rom) as? FoxResult.Ok)?.value ?: return emptyList()
        return (read(plain, project.rom.header.game, count) as? FoxResult.Ok)?.value ?: emptyList()
    }

    /** Writes [headers] into the project's ARM9. An unchanged table leaves the project clean. */
    fun write(project: RomProject, headers: List<MapHeader>): FoxResult<Unit> {
        val offset = offsetFor(project.rom.header.game) ?: return FoxResult.Err("No map header table known for ${project.rom.header.game}")
        val plain = when (val arm9 = project.readArm9()) {
            is FoxResult.Ok -> arm9.value
            is FoxResult.Err -> return arm9
        }
        if (offset + headers.size * MapHeader.SIZE > plain.size) return FoxResult.Err("Map header table does not fit in the ARM9")
        headers.forEachIndexed { i, h -> h.toBytes().copyInto(plain, offset + i * MapHeader.SIZE) }
        return project.writeArm9(plain)
    }

    fun read(plainArm9: ByteArray, game: Game, count: Int = COUNT): FoxResult<List<MapHeader>> {
        val offset = offsetFor(game) ?: return FoxResult.Err("No map header table known for $game")
        if (offset + count * MapHeader.SIZE > plainArm9.size) return FoxResult.Err("Map header table does not fit in the ARM9")
        return FoxResult.Ok(List(count) { MapHeader.parse(plainArm9, offset + it * MapHeader.SIZE) })
    }
}
