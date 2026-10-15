package io.github.romulofer.foxhg.core.data.learnset

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16

data class LearnsetEntry(val move: Int, val level: Int)

/**
 * Level-up moves of one species (`a/0/3/3`). Each entry is a u16: move id in the low 9 bits, level in the high 7;
 * the list ends with 0xFFFF. Bytes after the terminator (padding) are kept in [trailing] so records round-trip.
 */
data class Learnset(val entries: List<LearnsetEntry>, val trailing: ByteArray = ByteArray(0)) {
    /** The game reads at most [MAX_ENTRIES] moves of a learnset before it runs into the next data. */
    val overLimit: Boolean get() = entries.size > MAX_ENTRIES

    fun sorted(): Learnset = copy(entries = entries.sortedBy { it.level }) // stable: same-level order is kept

    fun toBytes(): ByteArray {
        val out = ByteArray(entries.size * 2 + 2 + trailing.size)
        entries.forEachIndexed { i, e -> out.putU16(i * 2, (e.move and 0x1FF) or (e.level shl 9)) }
        out.putU16(entries.size * 2, END)
        trailing.copyInto(out, entries.size * 2 + 2)
        return out
    }

    override fun equals(other: Any?) = other is Learnset && entries == other.entries && trailing.contentEquals(other.trailing)

    override fun hashCode() = entries.hashCode() * 31 + trailing.contentHashCode()

    companion object {
        const val END = 0xFFFF
        const val MAX_ENTRIES = 20

        fun parse(bytes: ByteArray): FoxResult<Learnset> {
            val entries = ArrayList<LearnsetEntry>()
            var p = 0
            while (true) {
                if (p + 2 > bytes.size) return FoxResult.Err("Learnset has no 0xFFFF terminator")
                val word = bytes.u16(p)
                p += 2
                if (word == END) break
                entries += LearnsetEntry(move = word and 0x1FF, level = word ushr 9)
            }
            return FoxResult.Ok(Learnset(entries, bytes.copyOfRange(p, bytes.size)))
        }

        val SPEC = RecordSpec(HgssLayout.LEARNSETS, ::parse, Learnset::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<Learnset>> = NarcRecords.load(project, SPEC)
    }
}
