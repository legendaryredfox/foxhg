package io.github.romulofer.foxhg.core.text

import io.github.romulofer.foxhg.core.FoxResult

/**
 * Maps the game's 16-bit text codes to readable tokens and back. Token spelling follows the community charmap:
 * letters are themselves, `\n` `\r` `\f` are line controls, `{NAME}` are commands, and a code without a token is
 * written `\xNNNN`. Decoding then encoding the same text always gives the same codes.
 */
class Charmap(private val tokens: Map<Int, String>) {
    private val codes: Map<String, Int>
    private val longestToken: Int

    init {
        val reverse = HashMap<String, Int>()
        for ((code, token) in tokens.toSortedMap()) reverse.getOrPut(token) { code }
        codes = reverse
        longestToken = reverse.keys.maxOf { it.length }
    }

    fun render(code: Int): String = tokens[code] ?: "\\x" + code.toString(16).uppercase().padStart(4, '0')

    fun decode(chars: IntArray): String = buildString { for (c in chars) append(render(c)) }

    fun encode(text: String): FoxResult<IntArray> {
        val out = ArrayList<Int>(text.length)
        var i = 0
        while (i < text.length) {
            if (text.startsWith("\\x", i) && i + 6 <= text.length && text.substring(i + 2, i + 6).all { it.isHexDigit() }) {
                out += text.substring(i + 2, i + 6).toInt(16)
                i += 6
                continue
            }
            var matched = 0
            for (len in minOf(longestToken, text.length - i) downTo 1) {
                val code = codes[text.substring(i, i + len)]
                if (code != null) {
                    out += code
                    matched = len
                    break
                }
            }
            if (matched == 0) return FoxResult.Err("No character code for '${text[i]}' at position $i")
            i += matched
        }
        return FoxResult.Ok(out.toIntArray())
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    companion object {
        /** The HeartGold/SoulSilver table. */
        val HGSS: Charmap by lazy {
            val map = HashMap<Int, String>()
            for (chunk in CHARMAP_CHUNKS) {
                for (line in chunk.split('\n')) {
                    if (line.length < 5 || line[4] != '=') continue
                    map[line.substring(0, 4).toInt(16)] = line.substring(5)
                }
            }
            Charmap(map)
        }
    }
}
