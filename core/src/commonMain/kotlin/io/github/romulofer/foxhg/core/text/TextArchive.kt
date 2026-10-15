package io.github.romulofer.foxhg.core.text

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/** One message: its 16-bit codes without the 0xFFFF terminator, and whether it is stored 9-bit compressed. */
class TextEntry(val chars: IntArray, val compressed: Boolean = false) {
    fun text(charmap: Charmap = Charmap.HGSS): String = MessageText.decode(chars, charmap)

    override fun equals(other: Any?) = other is TextEntry && compressed == other.compressed && chars.contentEquals(other.chars)

    override fun hashCode() = chars.contentHashCode() * 31 + compressed.hashCode()
}

/**
 * A Gen IV message archive (one file of `a/0/2/7` in HeartGold): `u16 count, u16 key`, then `count` pairs of
 * `u32 offset, u32 length (in 16-bit units)` XOR-masked with a key derived from [key] and the entry number, then
 * each message's 16-bit codes XOR-masked with a rolling key.
 *
 * Messages starting with 0xF100 hold their codes packed into 9-bit values, 15 bits of each 16-bit word (the top bit
 * is unused), ended by the value 0x1FF and then a plain 0xFFFF word; they are unpacked into [TextEntry.chars] and
 * packed again on [toBytes]. Trainer names (bank 729) are stored this way.
 */
class TextArchive(val key: Int, val entries: List<TextEntry>) {

    fun withEntry(index: Int, entry: TextEntry): TextArchive =
        TextArchive(key, entries.toMutableList().also { it[index] = entry })

    /**
     * Replaces message [index] with the codes of [text] (see [MessageText]). The message keeps its storage kind; a 9-bit
     * packed one cannot hold a code above 0x1FE.
     */
    fun withText(index: Int, text: String): FoxResult<TextArchive> {
        val old = entries.getOrNull(index) ?: return FoxResult.Err("No message $index (the bank has ${entries.size})")
        val chars = when (val r = MessageText.encode(text)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        if (old.compressed && chars.any { it > 0x1FE }) return FoxResult.Err("This message is stored packed (9-bit codes): it cannot hold {commands} or codes above 01FE")
        return FoxResult.Ok(withEntry(index, TextEntry(chars, old.compressed)))
    }

    override fun equals(other: Any?) = other is TextArchive && key == other.key && entries == other.entries

    override fun hashCode() = entries.hashCode() * 31 + key

    fun toBytes(): ByteArray {
        val encoded = entries.mapIndexed { i, entry -> encodeEntry(i, entry) }
        val tableEnd = 4 + 8 * entries.size
        val total = tableEnd + encoded.sumOf { it.size * 2 }
        val out = ByteArray(total)
        out.putU16(0, entries.size)
        out.putU16(2, key)
        var offset = tableEnd
        val seed = tableSeed(key)
        encoded.forEachIndexed { i, words ->
            val mask = tableMask(seed, i)
            out.putU32(4 + 8 * i, offset.toLong() xor mask)
            out.putU32(8 + 8 * i, words.size.toLong() xor mask)
            for ((w, word) in words.withIndex()) out.putU16(offset + 2 * w, word)
            offset += words.size * 2
        }
        return out
    }

    companion object {
        val SPEC = RecordSpec(HgssLayout.MESSAGES, ::parse, TextArchive::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<TextArchive>> = NarcRecords.load(project, SPEC)

        private const val COMPRESSED_MARKER = 0xF100
        private const val TERMINATOR = 0xFFFF

        private fun tableSeed(key: Int) = (key * 0x2FD) and 0xFFFF

        private fun tableMask(seed: Int, index: Int): Long {
            val k = (seed * (index + 1)) and 0xFFFF
            return ((k shl 16) or k).toLong() and 0xFFFFFFFFL
        }

        private fun entryKey(index: Int) = (0x91BD3 * (index + 1)) and 0xFFFF

        fun parse(bytes: ByteArray): FoxResult<TextArchive> {
            if (bytes.size < 4) return FoxResult.Err("Text archive: shorter than its header")
            val count = bytes.u16(0)
            val key = bytes.u16(2)
            if (4 + 8 * count > bytes.size) return FoxResult.Err("Text archive: table of $count entries does not fit")
            val seed = tableSeed(key)
            val entries = ArrayList<TextEntry>(count)
            for (i in 0 until count) {
                val mask = tableMask(seed, i)
                val offset = (bytes.u32(4 + 8 * i) xor mask).toInt()
                val length = (bytes.u32(8 + 8 * i) xor mask).toInt()
                if (offset < 0 || length < 0 || offset.toLong() + 2L * length > bytes.size) {
                    return FoxResult.Err("Text archive: entry $i range $offset+${length * 2} is outside the file")
                }
                var k = entryKey(i)
                val words = IntArray(length)
                for (w in 0 until length) {
                    words[w] = bytes.u16(offset + 2 * w) xor k
                    k = (k + 0x493D) and 0xFFFF
                }
                entries += decodeEntry(i, words) ?: return FoxResult.Err("Text archive: entry $i is malformed")
            }
            return FoxResult.Ok(TextArchive(key, entries))
        }

        private fun decodeEntry(index: Int, words: IntArray): TextEntry? {
            if (words.isNotEmpty() && words[0] == COMPRESSED_MARKER) {
                val packed = unpack(words)
                // Vanilla has stray messages that start with 0xF100 but are not valid 9-bit streams. Only trust the
                // compressed reading when packing it again gives back the very same words; otherwise keep the codes.
                if (packed != null && encodeEntry(index, packed).contentEquals(encrypt(index, words))) return packed
            }
            if (words.isEmpty() || words.last() != TERMINATOR) return null
            return TextEntry(words.copyOf(words.size - 1), compressed = false)
        }

        private fun unpack(words: IntArray): TextEntry? {
            if (words.size < 2 || words.last() != TERMINATOR) return null
            val chars = ArrayList<Int>()
            var container = 0
            var bits = 0
            // The last word is the plain terminator; a string whose bits fill the words exactly ends there.
            for (w in 1 until words.size - 1) {
                container = container or ((words[w] and 0x7FFF) shl bits)
                bits += 15
                while (bits >= 9) {
                    val value = container and 0x1FF
                    container = container ushr 9
                    bits -= 9
                    if (value == 0x1FF) return TextEntry(chars.toIntArray(), compressed = true)
                    chars += value
                }
            }
            return TextEntry(chars.toIntArray(), compressed = true)
        }

        private fun encrypt(index: Int, plain: IntArray): IntArray {
            var k = entryKey(index)
            return IntArray(plain.size) { w ->
                val encrypted = plain[w] xor k
                k = (k + 0x493D) and 0xFFFF
                encrypted
            }
        }

        private fun encodeEntry(index: Int, entry: TextEntry): IntArray {
            val plain: IntArray = if (entry.compressed) {
                val words = ArrayList<Int>()
                words += COMPRESSED_MARKER
                var container = 0
                var bits = 0
                fun push(value: Int) {
                    container = container or (value shl bits)
                    bits += 9
                    while (bits >= 15) {
                        words += container and 0x7FFF
                        container = container ushr 15
                        bits -= 15
                    }
                }
                entry.chars.forEach(::push)
                // The end mark 0x1FF is written only when it fits in the bits that are left in the last word; a
                // string that fills its words is ended by the plain 0xFFFF word alone, as in the game's data.
                if (bits != 0 && 15 - bits >= 9) push(0x1FF)
                // The unused bits of the last word are ones, like the game's own data.
                if (bits > 0) words += (container or (0x7FFF shl bits)) and 0x7FFF
                words += TERMINATOR
                words.toIntArray()
            } else {
                entry.chars + TERMINATOR
            }
            return encrypt(index, plain)
        }
    }
}
