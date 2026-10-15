package io.github.romulofer.foxhg.core.data.event

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/**
 * Coordinates are the game's own: a position on the whole map matrix, [MAP_SIZE] tiles per matrix cell, so tile x of the
 * cell at column c is `c * 32 + x`. Heights are fixed point 16.16 where the game stores 32 bits.
 */
const val MAP_SIZE = 32

/** A sign, board or hidden item (20 bytes). */
data class Spawnable(
    val script: Int,
    /** 0 miscellaneous, 1 board, 2 hidden item. */
    val type: Int,
    val x: Int,
    val unknown2: Int,
    val y: Int,
    val z: Int,
    val unknown4: Int,
    val direction: Int,
    val unknown5: Int,
) {
    internal fun write(b: ByteArray, p: Int) {
        b.putU16(p, script); b.putU16(p + 2, type); b.putU16(p + 4, x); b.putU16(p + 6, unknown2)
        b.putU16(p + 8, y); b.putU32(p + 10, z.toLong() and 0xFFFFFFFFL)
        b.putU16(p + 14, unknown4); b.putU16(p + 16, direction); b.putU16(p + 18, unknown5)
    }

    companion object {
        const val SIZE = 20
        val TYPES = listOf("Miscellaneous", "Board", "Hidden item")
        fun read(b: ByteArray, p: Int) = Spawnable(
            b.u16(p), b.u16(p + 2), b.u16(p + 4), b.u16(p + 6), b.u16(p + 8), b.u32(p + 10).toInt(),
            b.u16(p + 14), b.u16(p + 16), b.u16(p + 18),
        )
    }
}

/** An NPC, trainer or item ball (32 bytes). A script of 0xFFFF marks an alias of another object. */
data class OverworldEvent(
    val id: Int,
    /** Row of the overworld sprite table this object uses. */
    val spriteEntry: Int,
    val movement: Int,
    /** 0 normal, 1 trainer, 3 item. */
    val type: Int,
    val flag: Int,
    val script: Int,
    val orientation: Int,
    val sightRange: Int,
    val unknown1: Int,
    val unknown2: Int,
    val xRange: Int,
    val yRange: Int,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    val isAlias: Boolean get() = script == 0xFFFF

    internal fun write(b: ByteArray, p: Int) {
        val words = intArrayOf(id, spriteEntry, movement, type, flag, script, orientation, sightRange, unknown1, unknown2, xRange, yRange, x, y)
        words.forEachIndexed { i, w -> b.putU16(p + i * 2, w) }
        b.putU32(p + 28, z.toLong() and 0xFFFFFFFFL)
    }

    companion object {
        const val SIZE = 32
        const val ALIAS_SCRIPT = 0xFFFF
        val TYPES = mapOf(0 to "Normal", 1 to "Trainer", 3 to "Item")
        fun read(b: ByteArray, p: Int): OverworldEvent {
            val w = IntArray(14) { b.u16(p + it * 2) }
            return OverworldEvent(w[0], w[1], w[2], w[3], w[4], w[5], w[6], w[7], w[8], w[9], w[10], w[11], w[12], w[13], b.u32(p + 28).toInt())
        }
    }
}

/** A door or exit leading to another map header (12 bytes). */
data class Warp(val x: Int, val y: Int, val header: Int, val anchor: Int, val height: Long) {
    internal fun write(b: ByteArray, p: Int) {
        b.putU16(p, x); b.putU16(p + 2, y); b.putU16(p + 4, header); b.putU16(p + 6, anchor); b.putU32(p + 8, height)
    }

    companion object {
        const val SIZE = 12
        fun read(b: ByteArray, p: Int) = Warp(b.u16(p), b.u16(p + 2), b.u16(p + 4), b.u16(p + 6), b.u32(p + 8))
    }
}

/** A script that runs when the player steps on an area, optionally only while a variable has a value (16 bytes). */
data class Trigger(
    val script: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val z: Int,
    val expectedValue: Int,
    /** 0 means no condition. */
    val variable: Int,
) {
    internal fun write(b: ByteArray, p: Int) {
        intArrayOf(script, x, y, width, height, z, expectedValue, variable).forEachIndexed { i, w -> b.putU16(p + i * 2, w) }
    }

    companion object {
        const val SIZE = 16
        fun read(b: ByteArray, p: Int): Trigger {
            val w = IntArray(8) { b.u16(p + it * 2) }
            return Trigger(w[0], w[1], w[2], w[3], w[4], w[5], w[6], w[7])
        }
    }
}

/**
 * The events of one map (a file of `a/0/3/2`): four counted lists in a fixed order, each `u32 count` then fixed-size
 * records: spawnables, overworld objects, warps, triggers. Bytes after the last record are kept in [trailing].
 */
data class EventFile(
    val spawnables: List<Spawnable> = emptyList(),
    val overworlds: List<OverworldEvent> = emptyList(),
    val warps: List<Warp> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    val trailing: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = spawnables.isEmpty() && overworlds.isEmpty() && warps.isEmpty() && triggers.isEmpty()

    val count: Int get() = spawnables.size + overworlds.size + warps.size + triggers.size

    fun toBytes(): ByteArray {
        val out = ByteArray(16 + spawnables.size * Spawnable.SIZE + overworlds.size * OverworldEvent.SIZE + warps.size * Warp.SIZE + triggers.size * Trigger.SIZE + trailing.size)
        var p = 0
        out.putU32(p, spawnables.size.toLong()); p += 4
        for (e in spawnables) { e.write(out, p); p += Spawnable.SIZE }
        out.putU32(p, overworlds.size.toLong()); p += 4
        for (e in overworlds) { e.write(out, p); p += OverworldEvent.SIZE }
        out.putU32(p, warps.size.toLong()); p += 4
        for (e in warps) { e.write(out, p); p += Warp.SIZE }
        out.putU32(p, triggers.size.toLong()); p += 4
        for (e in triggers) { e.write(out, p); p += Trigger.SIZE }
        trailing.forEachIndexed { i, v -> out[p + i] = v.toByte() }
        return out
    }

    companion object {
        /** More than the game ever uses; a larger count means the file is not an event file. */
        private const val MAX_PER_LIST = 4096

        fun parse(bytes: ByteArray): FoxResult<EventFile> {
            var p = 0
            fun <T> list(name: String, size: Int, read: (ByteArray, Int) -> T): FoxResult<List<T>> {
                if (p + 4 > bytes.size) return FoxResult.Err("Event file ends before the $name count")
                val n = bytes.u32(p)
                p += 4
                if (n > MAX_PER_LIST || p + n * size > bytes.size) return FoxResult.Err("Event file: $n $name do not fit")
                val items = List(n.toInt()) { read(bytes, p + it * size) }
                p += n.toInt() * size
                return FoxResult.Ok(items)
            }
            val spawnables = when (val r = list("spawnables", Spawnable.SIZE, Spawnable::read)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            val overworlds = when (val r = list("overworlds", OverworldEvent.SIZE, OverworldEvent::read)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            val warps = when (val r = list("warps", Warp.SIZE, Warp::read)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            val triggers = when (val r = list("triggers", Trigger.SIZE, Trigger::read)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            return FoxResult.Ok(EventFile(spawnables, overworlds, warps, triggers, bytes.drop(p).map { it.toInt() and 0xFF }))
        }

        val SPEC = RecordSpec(HgssLayout.EVENTS, ::parse, EventFile::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<EventFile>> = NarcRecords.load(project, SPEC)
    }
}
