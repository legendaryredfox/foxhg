package io.github.legendaryredfox.foxhg.app

import java.io.File
import java.io.IOException
import java.nio.file.Path

/** Starting an emulator for the Run button: the command is a template the user wrote, so it is split here and not by a shell. */
object Emulator {
    /** Starts the process and returns without waiting; an error message, or null when it started. Tests replace this. */
    var launcher: (List<String>) -> String? = ::start

    private fun start(command: List<String>): String? = try {
        ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        null
    } catch (e: IOException) {
        e.message ?: "could not start ${command.firstOrNull()}"
    }

    /**
     * The words of [template], with `{rom}` replaced by [rom]. Words are separated by spaces; text in single or double
     * quotes is one word. A template with no `{rom}` gets the ROM added as the last word.
     */
    fun command(template: String, rom: Path): List<String> {
        val words = ArrayList<String>()
        val word = StringBuilder()
        var quote = ' '
        var started = false
        for (c in template.trim()) {
            when {
                quote != ' ' -> if (c == quote) quote = ' ' else word.append(c)
                c == '"' || c == '\'' -> { quote = c; started = true }
                c.isWhitespace() -> if (started || word.isNotEmpty()) { words += word.toString(); word.clear(); started = false }
                else -> word.append(c)
            }
        }
        if (started || word.isNotEmpty()) words += word.toString()
        val hasRom = words.any { it.contains("{rom}") }
        val replaced = words.map { it.replace("{rom}", rom.toString()) }
        return if (hasRom) replaced else replaced + rom.toString()
    }

    /** A command for an emulator found on this machine, or empty. DeSmuME and melonDS by name, RetroArch with its DeSmuME core. */
    fun detect(path: List<File> = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)): String {
        fun onPath(name: String) = path.any { File(it, name).canExecute() }
        val core = listOf("/usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so", "/usr/lib/libretro/desmume_libretro.so").firstOrNull { File(it).exists() }
        return when {
            onPath("desmume") -> "desmume {rom}"
            onPath("melonDS") -> "melonDS {rom}"
            onPath("retroarch") && core != null -> "retroarch -L $core {rom}"
            else -> ""
        }
    }
}
