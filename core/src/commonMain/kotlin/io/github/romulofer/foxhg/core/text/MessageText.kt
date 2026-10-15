package io.github.romulofer.foxhg.core.text

import io.github.romulofer.foxhg.core.FoxResult

/**
 * Message codes as readable text. Plain characters come from the [Charmap]. A command is the code 0xFFFE, a command
 * word, an argument count and that many argument words; it is written `{NAME arg, arg}`:
 *
 * - commands named by their whole word (`{YESNO 0}`, `{PAUSE 30}`, `{CURSOR_Y 24}`, `{ALN_CENTER}`, `{COLOR 1}`, ...);
 * - variables, where the high byte of the command word names the kind and the low byte is the first value:
 *   `{STRVAR_1 3, 0, 0}` is command 0x0103 with arguments 0, 0;
 * - `{CMD 0xHHHH, arg, ...}` for any other command word.
 *
 * A 0xFFFE without a complete command after it, and any code whose charmap token would read like a command, is
 * written `\xNNNN`. `encode(decode(x))` always gives back `x`.
 */
object MessageText {
    private const val COMMAND = 0xFFFE

    private val named = mapOf(
        0x0200 to "YESNO", 0x0201 to "PAUSE", 0x0202 to "WAIT", 0x0203 to "CURSOR_X", 0x0204 to "CURSOR_Y",
        0x0205 to "ALN_CENTER", 0x0206 to "ALN_RIGHT", 0x0207 to "UNK_207", 0x0208 to "UNK_208",
        0xFF00 to "COLOR", 0xFF01 to "SIZE", 0xFF02 to "UNK_FF02",
    )
    private val variables = mapOf(0x01 to "STRVAR_1", 0x03 to "STRVAR_3", 0x04 to "STRVAR_4", 0x34 to "STRVAR_34")
    private val namedByName = named.entries.associate { it.value to it.key }
    private val variablesByName = variables.entries.associate { it.value to it.key }

    fun decode(chars: IntArray, charmap: Charmap = Charmap.HGSS): String = buildString {
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            val count = if (c == COMMAND && i + 2 < chars.size) chars[i + 2] else -1
            if (count >= 0 && i + 3 + count <= chars.size) {
                val word = chars[i + 1]
                val args = (0 until count).map { chars[i + 3 + it] }
                append('{')
                val exact = named[word]
                val variable = variables[word shr 8]
                when {
                    exact != null -> { append(exact); if (args.isNotEmpty()) append(' ').append(args.joinToString(", ")) }
                    variable != null -> { append(variable).append(' ').append(word and 0xFF); args.forEach { append(", ").append(it) } }
                    else -> { append("CMD 0x").append(word.toString(16).uppercase().padStart(4, '0')); args.forEach { append(", ").append(it) } }
                }
                append('}')
                i += 3 + count
            } else {
                val token = charmap.render(c)
                if (token.startsWith("{")) append("\\x").append(c.toString(16).uppercase().padStart(4, '0')) else append(token)
                i++
            }
        }
    }

    fun encode(text: String, charmap: Charmap = Charmap.HGSS): FoxResult<IntArray> {
        val out = ArrayList<Int>(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val close = text.indexOf('}', i)
                if (close < 0) return FoxResult.Err("A '{' at position $i is never closed")
                when (val r = command(text.substring(i + 1, close), i)) {
                    is FoxResult.Ok -> out += r.value
                    is FoxResult.Err -> return r
                }
                i = close + 1
            } else {
                val next = text.indexOf('{', i).let { if (it < 0) text.length else it }
                when (val r = charmap.encode(text.substring(i, next))) {
                    is FoxResult.Ok -> out += r.value.toList()
                    is FoxResult.Err -> return r
                }
                i = next
            }
        }
        return FoxResult.Ok(out.toIntArray())
    }

    private fun command(body: String, at: Int): FoxResult<List<Int>> {
        val name = body.trim().substringBefore(' ')
        val numbers = body.trim().removePrefix(name).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        fun number(s: String): Int? = (if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toIntOrNull(16) else s.toIntOrNull())?.takeIf { it in 0..0xFFFF }
        val values = numbers.map { number(it) ?: return FoxResult.Err("'{$body}' at position $at: '$it' is not a number from 0 to 65535") }
        val exact = namedByName[name]
        val variable = variablesByName[name]
        return when {
            exact != null -> FoxResult.Ok(listOf(COMMAND, exact, values.size) + values)
            variable != null -> {
                val low = values.firstOrNull()?.takeIf { it <= 0xFF } ?: return FoxResult.Err("'{$body}' at position $at: {$name} needs a first value from 0 to 255")
                FoxResult.Ok(listOf(COMMAND, (variable shl 8) or low, values.size - 1) + values.drop(1))
            }
            name == "CMD" -> {
                val word = values.firstOrNull() ?: return FoxResult.Err("'{$body}' at position $at: {CMD} needs the command word first")
                FoxResult.Ok(listOf(COMMAND, word, values.size - 1) + values.drop(1))
            }
            else -> FoxResult.Err("'{$body}' at position $at: unknown command '$name'")
        }
    }
}
