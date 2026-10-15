package io.github.romulofer.foxhg.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

enum class RecentKind { PROJECT, ROM }

data class RecentEntry(val kind: RecentKind, val path: Path) {
    val displayName: String get() = path.fileName?.toString() ?: path.toString()
}

/**
 * The last few projects and ROMs opened, newest first, kept in a plain text file (`kind<TAB>path` per line; a line
 * with no tab is a ROM, which is what older versions wrote).
 */
class Recents(private val file: Path, private val limit: Int = 8) {

    /** Entries whose path still exists. A missing or unreadable file is simply an empty list. */
    fun load(): List<RecentEntry> = try {
        if (!file.exists()) emptyList()
        else Files.readAllLines(file).mapNotNull(::parse).filter { it.path.exists() }.take(limit)
    } catch (e: IOException) {
        emptyList()
    }

    fun add(kind: RecentKind, path: Path): List<RecentEntry> {
        val entry = RecentEntry(kind, path.toAbsolutePath().normalize())
        val updated = (listOf(entry) + load().filter { it != entry }).take(limit)
        try {
            Files.createDirectories(file.parent)
            Files.write(file, updated.map { "${it.kind.name.lowercase()}\t${it.path}" })
        } catch (e: IOException) {
            // Recents are a convenience; failing to remember one must never get in the way of opening something.
        }
        return updated
    }

    private fun parse(line: String): RecentEntry? {
        if (line.isBlank()) return null
        val tab = line.indexOf('\t')
        if (tab < 0) return RecentEntry(RecentKind.ROM, Path.of(line))
        val kind = RecentKind.entries.firstOrNull { it.name.equals(line.substring(0, tab), ignoreCase = true) } ?: return null
        return RecentEntry(kind, Path.of(line.substring(tab + 1)))
    }

    companion object {
        fun default(): Recents {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".config")
            return Recents(base.resolve("foxhg").resolve("recent.txt"))
        }
    }
}
