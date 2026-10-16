package io.github.legendaryredfox.foxhg.core.data.fly

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u8

/**
 * One town of the fly table (HeartGold and SoulSilver, 18 bytes): the unlock flag, two switches, the map and tile the
 * player is sent to after a blackout, the map and the global position Fly lands on, and the map and global position
 * of the spot that unlocks the town. Layout as in DSPRE's fly editor.
 */
data class FlyEntry(
    val flagIndex: Int,
    val isBlackoutSpawn: Boolean,
    val isFlyPoint: Boolean,
    val blackoutHeader: Int,
    val localX: Int,
    val localY: Int,
    val flyHeader: Int,
    val flyX: Int,
    val flyY: Int,
    val unlockHeader: Int,
    val unlockX: Int,
    val unlockY: Int,
) {
    internal fun write(out: ByteArray, at: Int) {
        out[at] = flagIndex.toByte()
        out[at + 1] = ((if (isBlackoutSpawn) 1 else 0) or (if (isFlyPoint) 2 else 0)).toByte()
        out.putU16(at + 2, blackoutHeader)
        out[at + 4] = localX.toByte()
        out[at + 5] = localY.toByte()
        out.putU16(at + 6, flyHeader)
        out.putU16(at + 8, flyX)
        out.putU16(at + 10, flyY)
        out.putU16(at + 12, unlockHeader)
        out.putU16(at + 14, unlockX)
        out.putU16(at + 16, unlockY)
    }

    companion object {
        const val SIZE = 18

        internal fun read(b: ByteArray, at: Int): FlyEntry {
            val flags = b.u8(at + 1)
            return FlyEntry(
                flagIndex = b.u8(at), isBlackoutSpawn = flags and 1 != 0, isFlyPoint = flags and 2 != 0,
                blackoutHeader = b.u16(at + 2), localX = b.u8(at + 4), localY = b.u8(at + 5),
                flyHeader = b.u16(at + 6), flyX = b.u16(at + 8), flyY = b.u16(at + 10),
                unlockHeader = b.u16(at + 12), unlockX = b.u16(at + 14), unlockY = b.u16(at + 16),
            )
        }
    }
}

/**
 * The fly table of the ARM9: a fixed number of towns, so entries can be changed but not added or removed. Flag bits
 * other than the two known ones are not kept: [applyTo] writes only those two, the vanilla table uses no others.
 */
data class FlyTable(val entries: List<FlyEntry>) {
    fun with(index: Int, entry: FlyEntry): FlyTable = copy(entries = entries.toMutableList().also { it[index] = entry })

    fun applyTo(arm9: ByteArray): ByteArray {
        require(entries.size == HgssLayout.FLY_TABLE_ENTRIES) { "The fly table has ${HgssLayout.FLY_TABLE_ENTRIES} entries" }
        val out = arm9.copyOf()
        entries.forEachIndexed { i, e -> e.write(out, HgssLayout.FLY_TABLE_ARM9 + i * FlyEntry.SIZE) }
        return out
    }

    companion object {
        fun parse(arm9: ByteArray): FoxResult<FlyTable> {
            val end = HgssLayout.FLY_TABLE_ARM9 + HgssLayout.FLY_TABLE_ENTRIES * FlyEntry.SIZE
            if (arm9.size < end) return FoxResult.Err("ARM9 is too small for the fly table")
            return FoxResult.Ok(FlyTable(List(HgssLayout.FLY_TABLE_ENTRIES) { FlyEntry.read(arm9, HgssLayout.FLY_TABLE_ARM9 + it * FlyEntry.SIZE) }))
        }

        fun load(project: RomProject): FoxResult<FlyTable> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }

        fun save(project: RomProject, table: FlyTable): FoxResult<Unit> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> project.writeArm9(table.applyTo(a.value))
            is FoxResult.Err -> a
        }
    }
}
