package io.github.legendaryredfox.foxhg.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import java.util.Locale

/**
 * The language of foxhg's own windows and buttons (never the language of the ROM, see docs/spec.md section 12). [language] and [region]
 * are the resource qualifiers of its `values-<language>[-r<REGION>]` folder; [reviewed] is false until a native speaker has read the strings.
 */
enum class AppLanguage(val tag: String, val nativeName: String, val language: String, val region: String, val reviewed: Boolean) {
    SYSTEM("system", "", "", "", true),
    ENGLISH("en", "English", "en", "", true),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)", "pt", "BR", true),
    SPANISH("es", "Español", "es", "", false),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文", "zh", "CN", false);

    /**
     * The language the resources are read in: this one, or for [SYSTEM] the language named by the environment variable `FOXHG_LANG` (tests,
     * screenshots), else the language of the operating system when foxhg has it, else English.
     */
    fun resolved(system: Locale = osLocale()): AppLanguage =
        if (this != SYSTEM) this
        else entries.firstOrNull { it != SYSTEM && it.language == system.language && (it.region.isEmpty() || it.region == system.country || system.country.isEmpty()) }
            ?: entries.firstOrNull { it != SYSTEM && it.language == system.language }
            ?: ENGLISH

    companion object {
        private val operatingSystem: Locale = Locale.getDefault()

        private fun osLocale(): Locale = System.getenv("FOXHG_LANG")?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: operatingSystem

        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Shows [content] in [language]. The resource library reads the JVM's default locale when a screen is composed and offers no public way to
 * override it, so this sets the default locale and composes [content] again from scratch when the language changes (the state of the
 * editors lives in the view models, so only the open dialog and text being typed are lost). Numbers keep a plain format whatever the
 * language (no decimal commas), so what is shown is what is typed in a field (docs/spec.md section 12).
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val resolved = language.resolved()
    remember(resolved) {
        Locale.setDefault(Locale.forLanguageTag(resolved.tag))
        Locale.setDefault(Locale.Category.FORMAT, Locale.ROOT)
    }
    key(resolved) { content() }
}
