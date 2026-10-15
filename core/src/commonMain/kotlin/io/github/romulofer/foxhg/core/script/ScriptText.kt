package io.github.romulofer.foxhg.core.script

import io.github.romulofer.foxhg.core.FoxResult

/**
 * A script file as text, and back.
 *
 * ```
 * script_1:                  ; a header entry
 *     LockAll
 *     GoToIf 5, function_1   ; the jump parameter is written as a label
 *     End
 * function_1:
 *     ...
 * action_1:                  ; a movement list: movement name and repeat count, ended by End
 *     FaceNorth 1
 *     End
 * .pad 2                     ; zero bytes
 * ```
 *
 * Commands use the names of the script command database. Numbers are decimal or `0x` hexadecimal. `;` and `//` start a
 * comment. Labels may be numbered freely but `script_N` must run 1..n; the order of lines is the layout of the file.
 */
object ScriptText {
    fun toText(file: ScriptFile): String = buildString {
        for (item in file.items) {
            when (item) {
                is ScriptItem.Label -> {
                    if (isNotEmpty() && !endsWith("\n\n") && !(this.lines().dropLast(1).lastOrNull()?.endsWith(":") ?: false)) append('\n')
                    append(item.ref).append(":\n")
                }
                is ScriptItem.Pad -> append("    .pad ").append(item.bytes).append('\n')
                is ScriptItem.Move -> append("    ").append(moveText(item.movement)).append('\n')
                is ScriptItem.Instruction -> append("    ").append(commandText(item.command)).append('\n')
            }
        }
    }

    private fun moveText(m: Movement): String = when {
        m.id == ScriptFile.MOVEMENT_END -> if (m.count == 0) "End" else "End ${m.count}"
        ScriptCommands.movements[m.id] != null -> "${ScriptCommands.movements[m.id]} ${m.count}"
        else -> "Move ${m.id}, ${m.count}"
    }

    private fun commandText(c: ScriptCommand): String {
        val name = ScriptCommands.byId(c.id)?.name ?: "Command_${c.id}"
        val jump = ScriptCommands.jumpIndex(c.id)
        val parts = ArrayList<String>()
        var i = 0
        while (ScriptCommands.nextArgSize(c.id, c.args.take(i)) != null) {
            parts += if (i == jump) c.target.toString() else c.args.getOrElse(i) { 0L }.toString()
            i++
        }
        return if (parts.isEmpty()) name else name + " " + parts.joinToString(", ")
    }

    fun parse(text: String): FoxResult<ScriptFile> {
        val items = ArrayList<ScriptItem>()
        var inAction = false
        for ((n, raw) in text.lines().withIndex()) {
            val line = raw.substringBefore(';').substringBefore("//").trim()
            if (line.isEmpty()) continue
            fun err(message: String) = FoxResult.Err("Line ${n + 1}: $message")
            if (line.endsWith(":")) {
                val ref = parseLabel(line.dropLast(1).trim()) ?: return err("'${line.dropLast(1)}' is not a label (use script_N, function_N or action_N)")
                items += ScriptItem.Label(ref)
                inAction = ref.kind == LabelRef.Kind.ACTION
                continue
            }
            val name = line.substringBefore(' ').trim()
            val rest = line.substring(name.length).trim()
            val args = if (rest.isEmpty()) emptyList() else rest.split(',').map { it.trim() }
            if (name == ".pad") {
                val count = args.singleOrNull()?.let(::number)?.takeIf { it in 0..0xFFFF } ?: return err(".pad needs a byte count")
                items += ScriptItem.Pad(count.toInt())
                continue
            }
            if (inAction) {
                val step = when (val r = parseMove(name, args)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return err(r.message) }
                items += ScriptItem.Move(step)
                if (step.id == ScriptFile.MOVEMENT_END) inAction = false
                continue
            }
            val info = ScriptCommands.byName(name) ?: return err("unknown command '$name'")
            val command = when (val r = parseCommand(info, args)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return err(r.message) }
            items += ScriptItem.Instruction(command)
        }
        val file = ScriptFile(items)
        file.problem()?.let { return FoxResult.Err(it) }
        return FoxResult.Ok(file)
    }

    private fun number(s: String): Long? = (if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toLongOrNull(16) else s.toLongOrNull())

    private fun parseLabel(s: String): LabelRef? {
        val kind = LabelRef.Kind.entries.firstOrNull { s.startsWith(it.name.lowercase() + "_") } ?: return null
        val number = s.substringAfter('_').toIntOrNull()?.takeIf { it >= 1 } ?: return null
        return LabelRef(kind, number)
    }

    private fun parseMove(name: String, args: List<String>): FoxResult<Movement> {
        if (name == "End") {
            val count = if (args.isEmpty()) 0L else args.singleOrNull()?.let(::number) ?: return FoxResult.Err("End takes at most a count")
            return FoxResult.Ok(Movement(ScriptFile.MOVEMENT_END, count.toInt()))
        }
        if (name == "Move") {
            val id = args.getOrNull(0)?.let(::number)?.takeIf { it in 0..0xFFFF }
            val count = args.getOrNull(1)?.let(::number)?.takeIf { it in 0..0xFFFF }
            if (args.size != 2 || id == null || count == null) return FoxResult.Err("Move needs a movement id and a count, 0 to 65535")
            return FoxResult.Ok(Movement(id.toInt(), count.toInt()))
        }
        val id = ScriptCommands.movementIds[name] ?: return FoxResult.Err("unknown movement '$name' (inside a movement list, which ends with End)")
        val count = if (args.isEmpty()) 1L else args.singleOrNull()?.let(::number)?.takeIf { it in 0..0xFFFF } ?: return FoxResult.Err("$name takes one repeat count, 0 to 65535")
        return FoxResult.Ok(Movement(id, count.toInt()))
    }

    private fun parseCommand(info: CommandInfo, args: List<String>): FoxResult<ScriptCommand> {
        val jump = ScriptCommands.jumpIndex(info.id)
        val values = ArrayList<Long>()
        var target: LabelRef? = null
        var i = 0
        while (true) {
            val size = ScriptCommands.nextArgSize(info.id, values) ?: break
            val token = args.getOrNull(i) ?: return FoxResult.Err("${info.name} needs another parameter (${describe(info, i)})")
            if (i == jump) {
                target = parseLabel(token) ?: return FoxResult.Err("${info.name}: '$token' is not a label")
                values += 0L
            } else {
                val v = number(token) ?: return FoxResult.Err("${info.name}: '$token' is not a number")
                val max = if (size >= 4) 0xFFFFFFFFL else (1L shl (8 * size)) - 1
                if (v < 0 || v > max) return FoxResult.Err("${info.name}: $v does not fit in $size byte(s)")
                values += v
            }
            i++
        }
        if (args.size > i) return FoxResult.Err("${info.name} takes $i parameter(s), got ${args.size}")
        return FoxResult.Ok(ScriptCommand(info.id, values, target))
    }

    private fun describe(info: CommandInfo, index: Int): String = info.params?.getOrNull(index)?.let { "${it.name}: ${it.type}" } ?: "parameter ${index + 1}"
}
