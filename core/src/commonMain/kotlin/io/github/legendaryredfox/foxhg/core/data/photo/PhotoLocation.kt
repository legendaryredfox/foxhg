package io.github.legendaryredfox.foxhg.core.data.photo

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/**
 * One place where the player can have a photo taken (`a/2/5/4`, one 16 byte file each, used by the `TakePhoto` script command; wiki guide "Photo
 * Locations", struct from the decomp's `photo_album.h`): the map header, the icon of the photo album, the player's global position in the picture,
 * a constant word that is always 0xFF01 in the game ([unknown]), the overworld entry of the NPC in the picture (0 for a group photo of the party)
 * and two parameters whose meaning is not known ([param1], [param2]). The guide says a photo with no NPC must have both parameters 0, but nine
 * entries of the real ROM have no NPC and a non-zero [param1] (and entry 53 a second one), so that is not enforced.
 */
data class PhotoLocation(
    val header: Int, val icon: Int, val x: Int, val y: Int, val unknown: Int, val npc: Int, val param1: Int, val param2: Int,
) {
    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        listOf(header, icon, x, y, unknown, npc, param1, param2).forEachIndexed { i, v -> b.putU16(i * 2, v) }
    }

    companion object {
        const val SIZE = 16

        /** The album icons, by value (the guide says only some are used in the game). */
        val ICONS = listOf("Mountain landscape", "Sea", "Yellow plain", "Rough terrain", "Cave (interior)", "Building (exterior)", "Building (interior)", "Grassland", "Forest", "Lake")

        fun parse(bytes: ByteArray): FoxResult<PhotoLocation> {
            if (bytes.size != SIZE) return FoxResult.Err("A photo location is $SIZE bytes, got ${bytes.size}")
            fun w(i: Int) = bytes.u16(i * 2)
            return FoxResult.Ok(PhotoLocation(w(0), w(1), w(2), w(3), w(4), w(5), w(6), w(7)))
        }

        val SPEC = RecordSpec({ it.photoLocations }, ::parse, PhotoLocation::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<PhotoLocation>> = NarcRecords.load(project, SPEC)
    }
}
