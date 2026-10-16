package io.github.legendaryredfox.foxhg.core.script

/** One parameter of a script command: its kind in the database (`u8`, `u16`, `var`, `flag`, ...), name and size in bytes. */
data class ParamSpec(val type: String, val name: String, val size: Int)

/**
 * A script command of HeartGold/SoulSilver. [params] is null for the few commands whose parameter list depends on a
 * mode value; [ScriptCommands.nextArgSize] knows those.
 */
class CommandInfo(val id: Int, val name: String, val params: List<ParamSpec>?)

/**
 * The command table (generated, see `scripts/gen-scrcmd.py`) plus the rules the file format adds: which commands
 * end a script, which carry a jump, and the commands with a mode-dependent parameter count.
 */
object ScriptCommands {
    private val sizes = mapOf("u8" to 1, "u16" to 2, "u32" to 4, "var" to 2, "flag" to 2, "label" to 4, "item" to 2, "trainer_id" to 2, "msg_id" to 2, "map_id" to 2, "flex" to 2)

    val all: List<CommandInfo> by lazy {
        SCRCMD_CHUNKS.flatMap { it.split('\n') }.map { line ->
            val (id, name, spec) = line.split('|')
            val params = when (spec) {
                "-" -> null
                "." -> emptyList()
                else -> spec.split(',').map { p ->
                    val (type, pname) = p.split(':', limit = 2)
                    ParamSpec(type, pname, sizes[type] ?: error("unknown parameter type '$type' for command $name"))
                }
            }
            CommandInfo(id.toInt(), name, params)
        }
    }

    private val byIdMap by lazy { all.associateBy { it.id } }
    private val byNameMap by lazy { all.associateBy { it.name } }

    fun byId(id: Int): CommandInfo? = byIdMap[id]
    fun byName(name: String): CommandInfo? = byNameMap[name]

    /** Movement (action) names by id, and the 0xFE end marker. */
    val movements: Map<Int, String> by lazy { MOVEMENT_CHUNKS.flatMap { it.split('\n') }.associate { l -> l.substringBefore('|').toInt() to l.substringAfter('|') } }
    val movementIds: Map<String, Int> by lazy { movements.entries.associate { it.value to it.key } }
    val comparisons: Map<Int, String> by lazy { COMPARISON_CHUNKS.flatMap { it.split('\n') }.associate { l -> l.substringBefore('|').toInt() to l.substringAfter('|') } }

    /** Commands after which the script (or function) stops: End, GoTo, Return. */
    val endCodes = setOf(0x02, 0x16, 0x1B)

    /** Commands with a relative jump to code, and the index of that parameter. */
    val functionJumps = mapOf(0x16 to 0, 0x17 to 1, 0x18 to 1, 0x19 to 1, 0x1A to 0, 0x1C to 1, 0x1D to 1)

    /** ApplyMovement: parameter 1 jumps to a movement list. */
    const val APPLY_MOVEMENT = 0x5E
    const val APPLY_MOVEMENT_JUMP = 1

    /** Index of the parameter that holds a jump target, or null. */
    fun jumpIndex(id: Int): Int? = functionJumps[id] ?: if (id == APPLY_MOVEMENT) APPLY_MOVEMENT_JUMP else null

    /**
     * Size in bytes of the next parameter of command [id] given the values read so far, or null when the command is
     * complete. Null as well for an unknown command (check [byId]).
     */
    fun nextArgSize(id: Int, args: List<Long>): Int? {
        val info = byId(id) ?: return null
        info.params?.let { return it.getOrNull(args.size)?.size }
        val n = args.size
        val mode = args.firstOrNull()?.toInt()
        return when (id) {
            0x190, 0x191, 0x192 -> when { n == 0 -> 1; n == 1 && mode == 2 -> 2; else -> null }
            0x1D1 -> when {
                n == 0 -> 2
                mode in 0..3 && n <= 2 -> 2
                (mode == 4 || mode == 5 || mode == 7) && n == 1 -> 2
                else -> null
            }
            0x1E9 -> when {
                n == 0 -> 2
                (mode == 1 || mode == 2 || mode == 3) && n == 1 -> 2
                (mode == 5 || mode == 6) && n <= 2 -> 2
                else -> null
            }
            else -> null
        }
    }
}
