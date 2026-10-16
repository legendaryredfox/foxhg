package io.github.legendaryredfox.foxhg.core.project

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/** One change of one target: its bytes before and after; null means the target had no edit then. */
class UndoChange(val target: EditTarget, val before: ByteArray?, val after: ByteArray?) {
    val size: Long get() = (before?.size ?: 0).toLong() + (after?.size ?: 0)
}

/**
 * The undo and redo steps of a project as they are saved in `history.bin`, so they survive closing the project. Each
 * step is a list of changes; [undo] is oldest first, [redo] is in the order the redo stack holds it (the next redo last).
 *
 * Format: `FHGH`, u32 version (1), u32 undo steps, u32 redo steps, then every step: u32 changes, then every change:
 * u8 kind (0 file, 1 ARM9, 2 overlay), the file path (u16 length, UTF-8) or the overlay id (u32), then twice (before,
 * after) u8 present and, when present, u32 length and the bytes.
 */
class UndoSnapshot(val undo: List<List<UndoChange>>, val redo: List<List<UndoChange>>) {
    val size: Long get() = (undo + redo).sumOf { step -> step.sumOf { it.size } }

    fun toBytes(): ByteArray {
        val out = Growable()
        out.bytes("FHGH".encodeToByteArray())
        out.u32(1); out.u32(undo.size.toLong()); out.u32(redo.size.toLong())
        for (step in undo + redo) {
            out.u32(step.size.toLong())
            for (c in step) {
                when (val t = c.target) {
                    is EditTarget.File -> { out.u8(0); val p = t.path.encodeToByteArray(); out.u16(p.size); out.bytes(p) }
                    EditTarget.Arm9 -> out.u8(1)
                    is EditTarget.Overlay -> { out.u8(2); out.u32(t.id.toLong()) }
                }
                for (side in listOf(c.before, c.after)) {
                    if (side == null) out.u8(0) else { out.u8(1); out.u32(side.size.toLong()); out.bytes(side) }
                }
            }
        }
        return out.toByteArray()
    }

    /** A snapshot of at most [maxBytes] of data: the oldest undo steps and the furthest redo steps go first. */
    fun trimmed(maxBytes: Long): UndoSnapshot {
        var budget = maxBytes
        val keptUndo = ArrayList<List<UndoChange>>()
        for (step in undo.asReversed()) {
            val s = step.sumOf { it.size }
            if (s > budget) break
            budget -= s
            keptUndo += step
        }
        val keptRedo = ArrayList<List<UndoChange>>()
        for (step in redo.asReversed()) { // the next redo is last, so it is the one to keep
            val s = step.sumOf { it.size }
            if (s > budget) break
            budget -= s
            keptRedo += step
        }
        return UndoSnapshot(keptUndo.asReversed(), keptRedo.asReversed())
    }

    companion object {
        fun parse(b: ByteArray): FoxResult<UndoSnapshot> = try {
            read(b)
        } catch (e: IndexOutOfBoundsException) {
            FoxResult.Err("history.bin is truncated")
        }

        private fun read(b: ByteArray): FoxResult<UndoSnapshot> {
            if (b.size < 16 || b.decodeToString(0, 4) != "FHGH") return FoxResult.Err("Not an undo history file")
            if (b.u32(4) != 1L) return FoxResult.Err("Unknown undo history version ${b.u32(4)}")
            val undoCount = b.u32(8).toInt()
            val redoCount = b.u32(12).toInt()
            var p = 16
            fun step(): List<UndoChange> {
                val count = b.u32(p).toInt(); p += 4
                return (0 until count).map {
                    val target = when (b.u8(p++)) {
                        0 -> { val n = b.u16(p); p += 2; EditTarget.File(b.decodeToString(p, p + n)).also { p += n } }
                        1 -> EditTarget.Arm9
                        else -> EditTarget.Overlay(b.u32(p).toInt()).also { p += 4 }
                    }
                    fun side(): ByteArray? {
                        if (b.u8(p++) == 0) return null
                        val n = b.u32(p).toInt(); p += 4
                        if (n < 0 || n > b.size - p) throw IndexOutOfBoundsException("a stored file is $n bytes but only ${b.size - p} are left")
                        return b.copyOfRange(p, p + n).also { p += n }
                    }
                    val before = side()
                    UndoChange(target, before, side())
                }
            }
            val undo = (0 until undoCount).map { step() }
            val redo = (0 until redoCount).map { step() }
            return FoxResult.Ok(UndoSnapshot(undo, redo))
        }
    }

    private class Growable {
        private var data = ByteArray(1 shl 16)
        private var size = 0
        private fun room(n: Int) { if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n)) }
        fun u8(v: Int) { room(1); data[size++] = v.toByte() }
        fun u16(v: Int) { u8(v and 0xFF); u8((v shr 8) and 0xFF) }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        fun bytes(b: ByteArray) { room(b.size); b.copyInto(data, size); size += b.size }
        fun toByteArray(): ByteArray = data.copyOf(size)
    }
}
