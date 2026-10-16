package io.github.legendaryredfox.foxhg.core.script

/**
 * Names of the script flags and variables (from the decomp via the script command database). A script may use a name
 * wherever it takes a number; the other way round, the editor shows the name next to a flag or variable number.
 */
object ScriptSymbols {
    private fun parse(chunks: List<String>): Pair<Map<Int, String>, Map<String, Int>> {
        val byId = LinkedHashMap<Int, String>()
        val byName = HashMap<String, Int>()
        for (line in chunks.flatMap { it.split('\n') }) {
            val id = line.substringBefore('|').toInt()
            val name = line.substringAfter('|')
            byId.putIfAbsent(id, name)
            byName[name] = id
        }
        return byId to byName
    }

    private val flags by lazy { parse(FLAG_CHUNKS) }
    private val variables by lazy { parse(VARIABLE_CHUNKS) }

    fun flagName(id: Int): String? = flags.first[id]
    fun variableName(id: Int): String? = variables.first[id]

    /** The number a flag or variable name stands for. */
    fun idOf(name: String): Int? = flags.second[name] ?: variables.second[name]

    /** Variables live at 0x4000 and up (and the special ones at 0x8000); no flag number reaches that far in HeartGold except 0x403F. */
    const val FIRST_VARIABLE = 0x4000
}

/** What a parameter means to the person reading a script. */
enum class ParamRole { PLAIN, FLAG, MESSAGE }

private val MESSAGE_PARAM = Regex("""text_slot|option_msg|highlight_msg|male_text|female_text|top_screen_text|^message$|^msg_id$""")

/** The role of parameter [index] of command [id]: a flag number, a message number, or anything else. */
fun ScriptCommands.role(id: Int, index: Int): ParamRole {
    val param = byId(id)?.params?.getOrNull(index) ?: return ParamRole.PLAIN
    return when {
        id in 30..32 && index == 0 -> ParamRole.FLAG
        MESSAGE_PARAM.containsMatchIn(param.name) -> ParamRole.MESSAGE
        else -> ParamRole.PLAIN
    }
}
