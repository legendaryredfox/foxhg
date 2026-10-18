package io.github.legendaryredfox.foxhg.core.data.event

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.data.csv.CsvColumn
import io.github.legendaryredfox.foxhg.core.data.csv.RecordCsv
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

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

        /** CSV columns of the signs of one map (the `id` column is the position in the list); `z` is the signed 16.16 height. */
        val CSV = RecordCsv(
            listOf(
                CsvColumn<Spawnable>("script", 0..0xFFFF, { it.script }, { r, v -> r.copy(script = v) }),
                CsvColumn("type", 0..0xFFFF, { it.type }, { r, v -> r.copy(type = v) }),
                CsvColumn("x", 0..0xFFFF, { it.x }, { r, v -> r.copy(x = v) }),
                CsvColumn("unknown2", 0..0xFFFF, { it.unknown2 }, { r, v -> r.copy(unknown2 = v) }),
                CsvColumn("y", 0..0xFFFF, { it.y }, { r, v -> r.copy(y = v) }),
                CsvColumn("z", Int.MIN_VALUE..Int.MAX_VALUE, { it.z }, { r, v -> r.copy(z = v) }),
                CsvColumn("unknown4", 0..0xFFFF, { it.unknown4 }, { r, v -> r.copy(unknown4 = v) }),
                CsvColumn("direction", 0..0xFFFF, { it.direction }, { r, v -> r.copy(direction = v) }),
                CsvColumn("unknown5", 0..0xFFFF, { it.unknown5 }, { r, v -> r.copy(unknown5 = v) }),
            ),
        )

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
    /** Entry (key, not position) of the overworld sprite table of overlay 1 this object uses; see [OverworldSprites]. */
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

        /** CSV columns of the objects of one map (the `id` column is the position in the list, `object_id` the game's own id). */
        val CSV = RecordCsv(
            listOf(
                CsvColumn<OverworldEvent>("object_id", 0..0xFFFF, { it.id }, { r, v -> r.copy(id = v) }),
                CsvColumn("sprite_entry", 0..0xFFFF, { it.spriteEntry }, { r, v -> r.copy(spriteEntry = v) }),
                CsvColumn("movement", 0..0xFFFF, { it.movement }, { r, v -> r.copy(movement = v) }),
                CsvColumn("type", 0..0xFFFF, { it.type }, { r, v -> r.copy(type = v) }),
                CsvColumn("flag", 0..0xFFFF, { it.flag }, { r, v -> r.copy(flag = v) }),
                CsvColumn("script", 0..0xFFFF, { it.script }, { r, v -> r.copy(script = v) }),
                CsvColumn("orientation", 0..0xFFFF, { it.orientation }, { r, v -> r.copy(orientation = v) }),
                CsvColumn("sight_range", 0..0xFFFF, { it.sightRange }, { r, v -> r.copy(sightRange = v) }),
                CsvColumn("unknown1", 0..0xFFFF, { it.unknown1 }, { r, v -> r.copy(unknown1 = v) }),
                CsvColumn("unknown2", 0..0xFFFF, { it.unknown2 }, { r, v -> r.copy(unknown2 = v) }),
                CsvColumn("x_range", 0..0xFFFF, { it.xRange }, { r, v -> r.copy(xRange = v) }),
                CsvColumn("y_range", 0..0xFFFF, { it.yRange }, { r, v -> r.copy(yRange = v) }),
                CsvColumn("x", 0..0xFFFF, { it.x }, { r, v -> r.copy(x = v) }),
                CsvColumn("y", 0..0xFFFF, { it.y }, { r, v -> r.copy(y = v) }),
                CsvColumn("z", Int.MIN_VALUE..Int.MAX_VALUE, { it.z }, { r, v -> r.copy(z = v) }),
            ),
        )
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

        /** CSV columns of the warps of one map; `height` is the 32 bit word shown as a signed number. */
        val CSV = RecordCsv(
            listOf(
                CsvColumn<Warp>("x", 0..0xFFFF, { it.x }, { r, v -> r.copy(x = v) }),
                CsvColumn("y", 0..0xFFFF, { it.y }, { r, v -> r.copy(y = v) }),
                CsvColumn("header", 0..0xFFFF, { it.header }, { r, v -> r.copy(header = v) }),
                CsvColumn("anchor", 0..0xFFFF, { it.anchor }, { r, v -> r.copy(anchor = v) }),
                CsvColumn("height", Int.MIN_VALUE..Int.MAX_VALUE, { it.height.toInt() }, { r, v -> r.copy(height = v.toLong() and 0xFFFFFFFFL) }),
            ),
        )
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

        /** CSV columns of the triggers of one map. */
        val CSV = RecordCsv(
            listOf(
                CsvColumn<Trigger>("script", 0..0xFFFF, { it.script }, { r, v -> r.copy(script = v) }),
                CsvColumn("x", 0..0xFFFF, { it.x }, { r, v -> r.copy(x = v) }),
                CsvColumn("y", 0..0xFFFF, { it.y }, { r, v -> r.copy(y = v) }),
                CsvColumn("width", 0..0xFFFF, { it.width }, { r, v -> r.copy(width = v) }),
                CsvColumn("height", 0..0xFFFF, { it.height }, { r, v -> r.copy(height = v) }),
                CsvColumn("z", 0..0xFFFF, { it.z }, { r, v -> r.copy(z = v) }),
                CsvColumn("expected_value", 0..0xFFFF, { it.expectedValue }, { r, v -> r.copy(expectedValue = v) }),
                CsvColumn("variable", 0..0xFFFF, { it.variable }, { r, v -> r.copy(variable = v) }),
            ),
        )
        fun read(b: ByteArray, p: Int): Trigger {
            val w = IntArray(8) { b.u16(p + it * 2) }
            return Trigger(w[0], w[1], w[2], w[3], w[4], w[5], w[6], w[7])
        }
    }
}

/** The four kinds of event of an [EventFile]. */
enum class EventKind(val label: String) { OVERWORLD("Object"), WARP("Warp"), TRIGGER("Trigger"), SIGN("Sign") }

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

    fun count(kind: EventKind): Int = when (kind) {
        EventKind.OVERWORLD -> overworlds.size
        EventKind.WARP -> warps.size
        EventKind.TRIGGER -> triggers.size
        EventKind.SIGN -> spawnables.size
    }

    /** Where event [index] of [kind] stands on the matrix (the top left tile for a trigger). */
    fun positionOf(kind: EventKind, index: Int): Pair<Int, Int>? = when (kind) {
        EventKind.OVERWORLD -> overworlds.getOrNull(index)?.let { it.x to it.y }
        EventKind.WARP -> warps.getOrNull(index)?.let { it.x to it.y }
        EventKind.TRIGGER -> triggers.getOrNull(index)?.let { it.x to it.y }
        EventKind.SIGN -> spawnables.getOrNull(index)?.let { it.x to it.y }
    }

    /** The events that cover tile ([x], [y]) of the matrix, objects and warps first, triggers (which are areas) last. */
    fun at(x: Int, y: Int): List<Pair<EventKind, Int>> = buildList {
        overworlds.forEachIndexed { i, e -> if (e.x == x && e.y == y) add(EventKind.OVERWORLD to i) }
        warps.forEachIndexed { i, e -> if (e.x == x && e.y == y) add(EventKind.WARP to i) }
        spawnables.forEachIndexed { i, e -> if (e.x == x && e.y == y) add(EventKind.SIGN to i) }
        triggers.forEachIndexed { i, e -> if (x >= e.x && x < e.x + maxOf(1, e.width) && y >= e.y && y < e.y + maxOf(1, e.height)) add(EventKind.TRIGGER to i) }
    }

    fun moved(kind: EventKind, index: Int, x: Int, y: Int): EventFile = when (kind) {
        EventKind.OVERWORLD -> copy(overworlds = overworlds.replaced(index) { it.copy(x = x, y = y) })
        EventKind.WARP -> copy(warps = warps.replaced(index) { it.copy(x = x, y = y) })
        EventKind.TRIGGER -> copy(triggers = triggers.replaced(index) { it.copy(x = x, y = y) })
        EventKind.SIGN -> copy(spawnables = spawnables.replaced(index) { it.copy(x = x, y = y) })
    }

    fun removed(kind: EventKind, index: Int): EventFile = when (kind) {
        EventKind.OVERWORLD -> copy(overworlds = overworlds.filterIndexed { i, _ -> i != index })
        EventKind.WARP -> copy(warps = warps.filterIndexed { i, _ -> i != index })
        EventKind.TRIGGER -> copy(triggers = triggers.filterIndexed { i, _ -> i != index })
        EventKind.SIGN -> copy(spawnables = spawnables.filterIndexed { i, _ -> i != index })
    }

    /** A copy of event [index] one tile to the right, added at the end; objects get the next free object id. */
    fun duplicated(kind: EventKind, index: Int): EventFile = when (kind) {
        EventKind.OVERWORLD -> overworlds.getOrNull(index)?.let { copy(overworlds = overworlds + it.copy(id = nextObjectId(), x = it.x + 1)) } ?: this
        EventKind.WARP -> warps.getOrNull(index)?.let { copy(warps = warps + it.copy(x = it.x + 1)) } ?: this
        EventKind.TRIGGER -> triggers.getOrNull(index)?.let { copy(triggers = triggers + it.copy(x = it.x + 1)) } ?: this
        EventKind.SIGN -> spawnables.getOrNull(index)?.let { copy(spawnables = spawnables + it.copy(x = it.x + 1)) } ?: this
    }

    /** A new event of [kind] at ([x], [y]), like the last one of its kind if there is one. It is the last of its list afterwards. */
    fun added(kind: EventKind, x: Int, y: Int): EventFile = when (kind) {
        EventKind.OVERWORLD -> copy(overworlds = overworlds + (overworlds.lastOrNull() ?: OverworldEvent(1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)).copy(id = nextObjectId(), x = x, y = y))
        EventKind.WARP -> copy(warps = warps + (warps.lastOrNull() ?: Warp(0, 0, 0, 0, 0)).copy(x = x, y = y))
        EventKind.TRIGGER -> copy(triggers = triggers + (triggers.lastOrNull() ?: Trigger(0, 0, 0, 1, 1, 0, 0, 0)).copy(x = x, y = y))
        EventKind.SIGN -> copy(spawnables = spawnables + (spawnables.lastOrNull() ?: Spawnable(0, 0, 0, 0, 0, 0, 0, 0, 0)).copy(x = x, y = y))
    }

    private fun nextObjectId(): Int = (overworlds.maxOfOrNull { it.id } ?: 0) + 1

    private fun <T> List<T>.replaced(index: Int, change: (T) -> T): List<T> = mapIndexed { i, e -> if (i == index) change(e) else e }

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
