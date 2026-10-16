package io.github.legendaryredfox.foxhg.core.script

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** A place code can jump to: script `N` (header entry), a function (any other jump target) or a movement list, numbered from 1. */
data class LabelRef(val kind: Kind, val number: Int) {
    enum class Kind { SCRIPT, FUNCTION, ACTION }

    override fun toString() = "${kind.name.lowercase()}_$number"
}

/**
 * One command: opcode and parameter values (unsigned). A jump command ([ScriptCommands.jumpIndex]) carries its destination
 * in [target]; the value at that parameter index is then unused (0).
 */
data class ScriptCommand(val id: Int, val args: List<Long> = emptyList(), val target: LabelRef? = null)

/** One step of a movement list: a movement id and how many times it repeats. A list ends with [ScriptFile.MOVEMENT_END]. */
data class Movement(val id: Int, val count: Int)

/** An item of the file body, in file order. */
sealed interface ScriptItem {
    /** What a jump or the header refers to. */
    data class Label(val ref: LabelRef) : ScriptItem
    data class Instruction(val command: ScriptCommand) : ScriptItem

    /** A step of the movement list that the preceding `action_N` label opens. */
    data class Move(val movement: Movement) : ScriptItem

    /** Zero bytes between chunks (alignment before a movement list, padding at the end of the file). */
    data class Pad(val bytes: Int) : ScriptItem
}

/**
 * The scripts of one map or object set (a file of `a/0/1/2`): a header of u32 offsets, one per script, ended by the
 * u16 0xFD13, then the body: the code of all scripts and functions and the movement lists they use, in the order they
 * are laid out. Scripts may share code and fall through into each other, and movement lists sit among the code, so
 * the body is kept as one run of [items] rather than as separate scripts.
 *
 * Labels: `script_N` are the header entries, `function_N` the other places jumps go to, `action_N` the movement lists.
 */
data class ScriptFile(val items: List<ScriptItem>) {
    /** Number of header entries. */
    val scriptCount: Int get() = items.count { it is ScriptItem.Label && it.ref.kind == LabelRef.Kind.SCRIPT }

    /** Why this file cannot be written (a missing or duplicate label), or null. */
    fun problem(): String? {
        val labels = items.filterIsInstance<ScriptItem.Label>().map { it.ref }
        labels.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.let { return "Label ${it.key} is defined twice" }
        val scripts = labels.filter { it.kind == LabelRef.Kind.SCRIPT }.map { it.number }.sorted()
        if (scripts != (1..scripts.size).toList()) return "Script labels must be numbered 1, 2, 3, ... without gaps"
        val defined = labels.toHashSet()
        for (item in items) {
            val c = (item as? ScriptItem.Instruction)?.command ?: continue
            if (ScriptCommands.jumpIndex(c.id) != null) {
                val target = c.target ?: return "Command ${ScriptCommands.byId(c.id)?.name} needs a jump target"
                if (target !in defined) return "A command jumps to $target, which is not defined"
                if ((target.kind == LabelRef.Kind.ACTION) != (c.id == ScriptCommands.APPLY_MOVEMENT)) return "${ScriptCommands.byId(c.id)?.name} cannot jump to $target"
            }
        }
        return null
    }

    fun toBytes(): ByteArray {
        problem()?.let { error(it) }
        val scripts = scriptCount
        val out = ByteBuf()
        repeat(scripts) { out.u32(0) }
        out.u16(HEADER_END)
        val offsets = HashMap<LabelRef, Int>()
        val fixups = ArrayList<Pair<Int, LabelRef>>()
        var previous: ScriptItem? = null
        for (item in items) {
            when (item) {
                is ScriptItem.Label -> {
                    if (item.ref.kind == LabelRef.Kind.ACTION && out.size % 2 == 1 && previous !is ScriptItem.Pad) out.u8(0)
                    offsets[item.ref] = out.size
                }
                is ScriptItem.Pad -> repeat(item.bytes) { out.u8(0) }
                is ScriptItem.Move -> { out.u16(item.movement.id); out.u16(item.movement.count) }
                is ScriptItem.Instruction -> {
                    val c = item.command
                    out.u16(c.id)
                    val jump = ScriptCommands.jumpIndex(c.id)
                    var i = 0
                    while (true) {
                        val size = ScriptCommands.nextArgSize(c.id, c.args.take(i)) ?: break
                        if (i == jump) {
                            fixups += out.size to c.target!!
                            out.u32(0)
                        } else out.value(c.args.getOrElse(i) { 0L }, size)
                        i++
                    }
                }
            }
            previous = item
        }
        for ((at, ref) in fixups) out.patchU32(at, (offsets.getValue(ref) - (at + 4)).toLong() and 0xFFFFFFFFL)
        for (n in 1..scripts) {
            val at = (n - 1) * 4
            out.patchU32(at, (offsets.getValue(LabelRef(LabelRef.Kind.SCRIPT, n)) - (at + 4)).toLong() and 0xFFFFFFFFL)
        }
        return out.toByteArray()
    }

    companion object {
        const val HEADER_END = 0xFD13
        const val MOVEMENT_END = 0xFE

        /** Reads a script file. It must write back to the very same bytes, otherwise it is refused and shown raw. */
        fun parse(bytes: ByteArray): FoxResult<ScriptFile> {
            val file = when (val r = read(bytes)) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return r
            }
            file.problem()?.let { return FoxResult.Err(it) }
            if (!file.toBytes().contentEquals(bytes)) return FoxResult.Err("This script file does not write back to the same bytes")
            return FoxResult.Ok(file)
        }

        private class Decoded(val offset: Int, val id: Int, val args: List<Long>, val rawTarget: Int?, val end: Int)

        private fun read(bytes: ByteArray): FoxResult<ScriptFile> {
            fun u16(p: Int) = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
            fun u32(p: Int) = u16(p).toLong() or (u16(p + 2).toLong() shl 16)
            val scriptStarts = ArrayList<Int>()
            var p = 0
            while (true) {
                if (p + 2 > bytes.size) return FoxResult.Err("Script file: no 0xFD13 end of header")
                if (u16(p) == HEADER_END) break
                if (p + 4 > bytes.size) return FoxResult.Err("Script file: truncated header")
                scriptStarts += (u32(p) + p + 4).toInt()
                p += 4
            }
            if (scriptStarts.isEmpty()) return FoxResult.Err("Script file: no scripts")
            val bodyStart = p + 2

            fun decodeAt(offset: Int): FoxResult<Decoded> {
                if (offset < bodyStart || offset + 2 > bytes.size) return FoxResult.Err("Script code at $offset is outside the file body")
                val id = u16(offset)
                if (ScriptCommands.byId(id) == null) return FoxResult.Err("Unknown script command $id at offset $offset")
                var q = offset + 2
                val args = ArrayList<Long>()
                var rawTarget: Int? = null
                val jump = ScriptCommands.jumpIndex(id)
                while (true) {
                    val size = ScriptCommands.nextArgSize(id, args) ?: break
                    if (q + size > bytes.size) return FoxResult.Err("Command $id at offset $offset runs off the file")
                    if (args.size == jump) { rawTarget = u32(q).toInt() + q + 4; args += 0L }
                    else args += when (size) { 1 -> bytes[q].toLong() and 0xFF; 2 -> u16(q).toLong(); else -> u32(q) }
                    q += size
                }
                return FoxResult.Ok(Decoded(offset, id, args, rawTarget, q))
            }

            // Pass 1: follow the code from the script starts to find every function and movement list.
            val functionTargets = HashSet<Int>()
            val movementTargets = HashSet<Int>()
            val visited = HashSet<Int>()
            val work = ArrayDeque(scriptStarts)
            while (work.isNotEmpty()) {
                var q = work.removeFirst()
                while (q !in visited) {
                    val d = when (val r = decodeAt(q)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
                    visited += q
                    d.rawTarget?.let { t ->
                        if (d.id == ScriptCommands.APPLY_MOVEMENT) movementTargets += t
                        else if (functionTargets.add(t)) work.addLast(t)
                    }
                    if (d.id in ScriptCommands.endCodes) break
                    q = d.end
                }
            }
            if (movementTargets.any { it < bodyStart || it >= bytes.size }) return FoxResult.Err("A movement jump goes outside the file")

            // Pass 2: walk the body in order. Code nothing refers to (it exists in the game's files) can jump to places
            // pass 1 did not see; those join the sets and the walk starts over.
            // Pass 2 may meet places pass 1 could not see (code nothing refers to, movement lists nothing refers to). Those
            // join the sets and the walk starts over; a movement list that was only guessed is dropped again if the
            // walk then fails.
            val guessed = ArrayList<Int>()
            val notLists = HashSet<Int>()
            var rounds = 0
            while (rounds++ < 400) {
                when (val r = walk(bytes, bodyStart, scriptStarts, functionTargets, movementTargets, notLists, ::decodeAt)) {
                    is Walk.Done -> return FoxResult.Ok(ScriptFile(r.items))
                    is Walk.Failed -> {
                        if (guessed.isEmpty()) return FoxResult.Err(r.message)
                        val last = guessed.removeAt(guessed.size - 1)
                        movementTargets -= last
                        notLists += last
                    }
                    is Walk.MoreTargets -> {
                        functionTargets += r.functions
                        movementTargets += r.movements
                        r.guess?.let { guessed += it }
                    }
                }
            }
            return FoxResult.Err("Script file: layout could not be worked out")
        }

        private sealed interface Walk {
            class Done(val items: List<ScriptItem>) : Walk
            class Failed(val message: String) : Walk
            class MoreTargets(val functions: Set<Int>, val movements: Set<Int>, val guess: Int? = null) : Walk
        }

        /** Steps of known movements (or the 0xFE end) up to a 0xFE, starting at an even offset. */
        private fun looksLikeMovementList(bytes: ByteArray, start: Int): Boolean {
            var m = start
            while (m + 4 <= bytes.size) {
                val id = (bytes[m].toInt() and 0xFF) or ((bytes[m + 1].toInt() and 0xFF) shl 8)
                if (id == MOVEMENT_END) return true
                if (ScriptCommands.movements[id] == null) return false
                m += 4
            }
            return false
        }

        private fun walk(
            bytes: ByteArray,
            bodyStart: Int,
            scriptStarts: List<Int>,
            functionTargets: Set<Int>,
            movementTargets: Set<Int>,
            notLists: Set<Int>,
            decodeAt: (Int) -> FoxResult<Decoded>,
        ): Walk {
            fun u16(p: Int) = (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
            val entries = (scriptStarts + functionTargets + movementTargets).toSortedSet()
            val scriptAt = HashMap<Int, Int>()
            scriptStarts.forEachIndexed { i, s -> scriptAt.putIfAbsent(s, i + 1) }
            val scriptsAt = scriptStarts.withIndex().groupBy({ it.value }, { it.index + 1 })
            val functionNumber = functionTargets.filter { it !in scriptAt }.sorted().withIndex().associate { (i, t) -> t to i + 1 }
            val actionNumber = movementTargets.sorted().withIndex().associate { (i, t) -> t to i + 1 }
            val items = ArrayList<ScriptItem>()
            val emitted = HashSet<Int>()
            val moreFunctions = HashSet<Int>()
            val moreMovements = HashSet<Int>()
            var q = bodyStart
            while (q < bytes.size) {
                scriptsAt[q]?.forEach { items += ScriptItem.Label(LabelRef(LabelRef.Kind.SCRIPT, it)); emitted += q }
                functionNumber[q]?.let { items += ScriptItem.Label(LabelRef(LabelRef.Kind.FUNCTION, it)); emitted += q }
                var chunkEnded = false
                if (q in movementTargets) {
                    items += ScriptItem.Label(LabelRef(LabelRef.Kind.ACTION, actionNumber.getValue(q)))
                    emitted += q
                    while (true) {
                        if (q + 4 > bytes.size) return Walk.Failed("Script file: movement list runs off the file")
                        val step = Movement(u16(q), u16(q + 2))
                        items += ScriptItem.Move(step)
                        q += 4
                        if (step.id == MOVEMENT_END) break
                    }
                    chunkEnded = true
                } else {
                    val d = when (val r = decodeAt(q)) {
                        is FoxResult.Ok -> r.value
                        is FoxResult.Err -> {
                            // Not a command: a movement list nothing reachable refers to.
                            if (q !in notLists && looksLikeMovementList(bytes, q)) return Walk.MoreTargets(emptySet(), setOf(q), guess = q)
                            return Walk.Failed(r.message)
                        }
                    }
                    val target = d.rawTarget?.let { t ->
                        when {
                            d.id == ScriptCommands.APPLY_MOVEMENT -> actionNumber[t]?.let { LabelRef(LabelRef.Kind.ACTION, it) } ?: run { moreMovements += t; null }
                            t in scriptAt -> LabelRef(LabelRef.Kind.SCRIPT, scriptAt.getValue(t))
                            else -> functionNumber[t]?.let { LabelRef(LabelRef.Kind.FUNCTION, it) } ?: run { moreFunctions += t; null }
                        }
                    }
                    items += ScriptItem.Instruction(ScriptCommand(d.id, d.args, target))
                    q = d.end
                    chunkEnded = d.id in ScriptCommands.endCodes
                }
                // After a finished chunk: zeros up to a movement list nothing refers to are padding, and so is a zero run
                // up to the next entry point (or the end of the file).
                if (chunkEnded && q < bytes.size && q !in entries) {
                    var s = q
                    while (s < bytes.size && bytes[s].toInt() == 0) s++
                    if (s < bytes.size && s % 2 == 0 && s !in entries && s !in notLists && looksLikeMovementList(bytes, s)) return Walk.MoreTargets(emptySet(), setOf(s), guess = s)
                    val next = entries.firstOrNull { it > q } ?: bytes.size
                    if ((q until next).all { bytes[it].toInt() == 0 }) {
                        items += ScriptItem.Pad(next - q)
                        q = next
                    }
                }
            }
            if (moreFunctions.isNotEmpty() || moreMovements.isNotEmpty()) return Walk.MoreTargets(moreFunctions, moreMovements)
            if (!emitted.containsAll(entries)) return Walk.Failed("Script file: an entry point is not at the start of a command")
            return Walk.Done(items)
        }

        val SPEC = RecordSpec(HgssLayout.SCRIPTS, ::parse, ScriptFile::toBytes)
    }
}

/** Growable little-endian byte buffer. */
internal class ByteBuf {
    private var data = ByteArray(256)
    var size = 0
        private set

    private fun ensure(extra: Int) { if (size + extra > data.size) data = data.copyOf(maxOf(data.size * 2, size + extra)) }
    fun u8(v: Int) { ensure(1); data[size++] = v.toByte() }
    fun u16(v: Int) { u8(v); u8(v shr 8) }
    fun u32(v: Long) { u16(v.toInt()); u16((v shr 16).toInt()) }
    fun value(v: Long, size: Int) { when (size) { 1 -> u8(v.toInt()); 2 -> u16(v.toInt()); else -> u32(v) } }
    fun patchU32(at: Int, v: Long) { for (i in 0 until 4) data[at + i] = (v shr (8 * i)).toByte() }
    fun toByteArray(): ByteArray = data.copyOf(size)
}

/**
 * A file of the script NARC as the editor holds it: a [Script] or a [Level] script that parsed and writes back exactly,
 * or [Raw] bytes kept untouched (empty files and the few files whose layout cannot be reproduced yet).
 */
sealed interface ScriptEntry {
    fun toBytes(): ByteArray

    class Script(val file: ScriptFile) : ScriptEntry {
        override fun toBytes() = file.toBytes()
        override fun equals(other: Any?) = other is Script && file == other.file
        override fun hashCode() = file.hashCode()
    }

    class Level(val file: LevelScript) : ScriptEntry {
        override fun toBytes() = file.toBytes()
        override fun equals(other: Any?) = other is Level && file == other.file
        override fun hashCode() = file.hashCode()
    }

    class Raw(val bytes: ByteArray, val reason: String) : ScriptEntry {
        override fun toBytes() = bytes.copyOf()
        override fun equals(other: Any?) = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }

    companion object {
        fun parse(bytes: ByteArray): FoxResult<ScriptEntry> = FoxResult.Ok(
            when (val r = ScriptFile.parse(bytes)) {
                is FoxResult.Ok -> Script(r.value)
                is FoxResult.Err -> when {
                    !r.message.contains("no 0xFD13") -> Raw(bytes, r.message)
                    else -> when (val level = LevelScript.parse(bytes)) {
                        is FoxResult.Ok -> Level(level.value)
                        is FoxResult.Err -> Raw(bytes, level.message)
                    }
                }
            },
        )

        val SPEC = RecordSpec(HgssLayout.SCRIPTS, ::parse, ScriptEntry::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<ScriptEntry>> = NarcRecords.load(project, SPEC)
    }
}
