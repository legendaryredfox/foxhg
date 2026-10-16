package io.github.legendaryredfox.foxhg.core.script

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * One entry of the trigger list. [type] 2 map change, 3 screen reset, 4 game load run script number [script] of the
 * map's own script file. Type 1 ([LevelScript.VARIABLE_VALUE]) is not a trigger: it marks where the entries stop and
 * the variable triggers start, its [script] is unused.
 */
data class LevelTrigger(val type: Int, val script: Int)

/** A script that runs on map load while variable [variable] equals [value]. */
data class VariableTrigger(val variable: Int, val value: Int, val script: Int)

/**
 * The level script of a map (a file of `a/0/1/2` without the 0xFD13 header): the scripts that run when the map is
 * entered, the screen is reset or the game is loaded, and the ones guarded by a variable.
 *
 * Layout: `u8 type, u32 value` for every entry of [triggers]; a type 1 entry (at most one, anywhere in the list) has
 * as value the size of the entries after it plus 1, which points past the terminator at the variable triggers. The
 * list ends with a `00` byte; if there is a type 1 entry, `u16 variable, u16 value, u16 script` triples follow, ended
 * by a `u16 0`; then [padding] zero bytes (files are kept 4-byte aligned).
 */
data class LevelScript(val triggers: List<LevelTrigger>, val variableTriggers: List<VariableTrigger>, val padding: Int = 0) {
    val hasVariableSection: Boolean get() = triggers.any { it.type == VARIABLE_VALUE }

    fun toBytes(): ByteArray {
        val out = ByteBuf()
        triggers.forEachIndexed { i, t ->
            out.u8(t.type)
            out.u32(if (t.type == VARIABLE_VALUE) 5L * triggers.drop(i + 1).count { it.type != VARIABLE_VALUE } + 1 else t.script.toLong())
        }
        out.u8(0)
        if (hasVariableSection) {
            for (v in variableTriggers) { out.u16(v.variable); out.u16(v.value); out.u16(v.script) }
            out.u16(0)
        }
        repeat(padding) { out.u8(0) }
        return out.toByteArray()
    }

    /** Sets the variable triggers, adding the section marker in front if there is none (and dropping it if all are gone and it was empty). */
    fun withVariableTriggers(list: List<VariableTrigger>): LevelScript =
        copy(variableTriggers = list, triggers = if (hasVariableSection || list.isEmpty()) triggers else listOf(LevelTrigger(VARIABLE_VALUE, 0)) + triggers)

    /** The same file with the padding a freshly written one gets (4-byte alignment). */
    fun aligned(): LevelScript = copy(padding = 0).let { it.copy(padding = (4 - it.toBytes().size % 4) % 4) }

    companion object {
        const val VARIABLE_VALUE = 1
        val TYPES = mapOf(2 to "Map change", 3 to "Screen reset", 4 to "Game load")

        /** Reads a level script; it must write back to the same bytes, otherwise the file is refused. */
        fun parse(bytes: ByteArray): FoxResult<LevelScript> {
            val triggers = ArrayList<LevelTrigger>()
            var p = 0
            while (p < bytes.size && (bytes[p].toInt() and 0xFF) in 1..4) {
                if (p + 5 > bytes.size) return FoxResult.Err("Level script: truncated entry")
                val type = bytes[p].toInt() and 0xFF
                triggers += LevelTrigger(type, if (type == VARIABLE_VALUE) 0 else bytes.u32(p + 1).toInt())
                p += 5
            }
            if (p >= bytes.size) return FoxResult.Err("Level script: not a level script")
            val variables = ArrayList<VariableTrigger>()
            p++ // the 00 that ends the list
            if (triggers.any { it.type == VARIABLE_VALUE }) {
                while (p + 2 <= bytes.size && bytes.u16(p) != 0) {
                    if (p + 6 > bytes.size) return FoxResult.Err("Level script: truncated variable trigger")
                    variables += VariableTrigger(bytes.u16(p), bytes.u16(p + 2), bytes.u16(p + 4))
                    p += 6
                }
                if (p + 2 > bytes.size) return FoxResult.Err("Level script: no terminator")
                p += 2
            }
            val file = LevelScript(triggers, variables, bytes.size - p)
            if (!file.toBytes().contentEquals(bytes)) return FoxResult.Err("This level script does not write back to the same bytes")
            return FoxResult.Ok(file)
        }
    }
}
