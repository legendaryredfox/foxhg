package io.github.legendaryredfox.foxhg.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * What the user can set. [emulatorCommand] is the command Run starts: `{rom}` stands for the built ROM, and the ROM is
 * added at the end when the command has no `{rom}`. Empty means no emulator is set.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val emulatorCommand: String = "",
    /** Whether a newly opened project saves automatically. */
    val autosave: Boolean = true,
    /** The language of foxhg's own windows; the default is Brazilian Portuguese, "system" follows the operating system. */
    val language: AppLanguage = AppLanguage.PORTUGUESE_BR,
)

/** The settings in a `key=value` text file. A missing or unreadable file gives the defaults, with an emulator found on this machine. */
class SettingsStore(private val file: Path, private val detectEmulator: () -> String = Emulator::detect) {
    fun load(): AppSettings = try {
        if (!file.exists()) AppSettings(emulatorCommand = detectEmulator())
        else {
            val p = Properties().also { props -> Files.newBufferedReader(file).use(props::load) }
            AppSettings(
                theme = ThemeChoice.entries.firstOrNull { it.name.equals(p.getProperty("theme"), ignoreCase = true) } ?: ThemeChoice.SYSTEM,
                emulatorCommand = p.getProperty("emulator", ""),
                autosave = p.getProperty("autosave")?.toBooleanStrictOrNull() ?: true,
                language = AppLanguage.fromTag(p.getProperty("language")),
            )
        }
    } catch (e: IOException) {
        AppSettings()
    } catch (e: IllegalArgumentException) {
        // A hand-edited file with a broken escape such as C:\users\...: the defaults, not a crash at start-up.
        AppSettings()
    }

    /** Returns false when the file could not be written. */
    fun save(settings: AppSettings): Boolean = try {
        Files.createDirectories(file.parent)
        val p = Properties().apply {
            setProperty("theme", settings.theme.name.lowercase())
            setProperty("emulator", settings.emulatorCommand)
            setProperty("autosave", settings.autosave.toString())
            setProperty("language", settings.language.tag)
        }
        Files.newBufferedWriter(file).use { p.store(it, "foxhg settings") }
        true
    } catch (e: IOException) {
        false
    }

    companion object {
        fun default(): SettingsStore {
            val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".config")
            return SettingsStore(base.resolve("foxhg").resolve("settings.properties"))
        }
    }
}
