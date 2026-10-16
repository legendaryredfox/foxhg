package io.github.legendaryredfox.foxhg.core.data.event

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * The overworld sprite table of overlay 1: the "sprite entry" of an overworld event maps to a sprite file of
 * `a/0/8/1` (a texture archive) and a properties word. Entries are not consecutive.
 */
class OverworldSprites(val entries: Map<Int, Entry>) {
    data class Entry(val spriteFile: Int, val properties: Int)

    companion object {
        /** RAM address of the pointer to the table inside overlay 1 (English HeartGold and SoulSilver). */
        const val POINTER_ADDRESS = 0x021F92FCL
        const val OVERLAY = 1
        private const val END = 0xFFFF

        fun load(project: RomProject): FoxResult<OverworldSprites> {
            val entry = project.rom.arm9Overlays.firstOrNull { it.id == OVERLAY } ?: return FoxResult.Err("The ROM has no overlay $OVERLAY")
            val image = when (val r = project.readOverlay(OVERLAY)) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return r
            }
            val pointerAt = (POINTER_ADDRESS - entry.ramAddress).toInt()
            if (pointerAt < 0 || pointerAt + 4 > image.size) return FoxResult.Err("The sprite table pointer is outside overlay $OVERLAY")
            val pointer = image.u32(pointerAt)
            if (pointer ushr 24 != 2L) return FoxResult.Err("The overworld sprite table pointer is not a main memory address")
            var p = (pointer - entry.ramAddress).toInt()
            if (p < 0) return FoxResult.Err("The overworld sprite table is outside overlay $OVERLAY")
            val table = LinkedHashMap<Int, Entry>()
            while (p + 6 <= image.size) {
                val id = image.u16(p)
                if (id == END) return FoxResult.Ok(OverworldSprites(table))
                table[id] = Entry(image.u16(p + 2), image.u16(p + 4))
                p += 6
            }
            return FoxResult.Err("The overworld sprite table has no end marker")
        }
    }
}
