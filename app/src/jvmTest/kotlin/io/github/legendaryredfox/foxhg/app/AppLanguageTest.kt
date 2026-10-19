package io.github.legendaryredfox.foxhg.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The string files of the app languages (docs/spec.md section 12): the same keys and the same placeholders everywhere, and the language choice. */
class AppLanguageTest {
    private val resources = Path.of("src/jvmMain/composeResources")

    private fun strings(dir: String): Map<String, String> {
        val text = Files.readString(resources.resolve(dir).resolve("strings.xml"))
        val plain = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        // a plural is one entry whose text is all its forms, so the placeholders of every form count
        val plurals = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        return plain + plurals
    }

    private fun placeholders(text: String) = Regex("""%\d+\$[sd]""").findAll(text).map { it.value }.toSortedSet().toList()

    @Test
    fun everyLanguageHasTheKeysOfEnglishAndNoOthers() {
        val english = strings("values")
        assertTrue(english.isNotEmpty())
        val dirs = Files.list(resources).use { list -> list.map { it.name }.filter { it.startsWith("values-") }.sorted().toList() }
        assertEquals(listOf("values-es", "values-pt-rBR", "values-zh-rCN"), dirs, "one folder for each app language")
        for (dir in dirs) {
            val other = strings(dir)
            assertEquals(english.keys, other.keys, "$dir has the keys of English")
            for ((key, text) in other) {
                assertTrue(text.isNotBlank(), "$dir $key is empty")
                assertEquals(placeholders(english.getValue(key)), placeholders(text), "$dir $key has the placeholders of English")
            }
        }
    }

    @Test
    fun everyLanguageOfTheSettingsHasAFolder() {
        for (language in AppLanguage.entries.filter { it != AppLanguage.SYSTEM && it != AppLanguage.ENGLISH }) {
            val folder = "values-" + language.language + if (language.region.isEmpty()) "" else "-r" + language.region
            assertTrue(Files.isDirectory(resources.resolve(folder)), "$language reads $folder")
        }
    }

    @Test
    fun systemFollowsTheLanguageOfTheMachineAndFallsBackToEnglish() {
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("pt-BR")))
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("pt")), "no country: the one Portuguese we have")
        assertEquals(AppLanguage.SPANISH, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("es-MX")))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("zh-CN")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.resolved(Locale.forLanguageTag("de-DE")))
        assertEquals(AppLanguage.SPANISH, AppLanguage.SPANISH.resolved(Locale.forLanguageTag("zh-CN")), "a chosen language wins")
    }

    @Test
    fun theSettingsFileKeepsTheLanguage() {
        val store = testSettings()
        store.save(AppSettings(language = AppLanguage.CHINESE_SIMPLIFIED))
        assertEquals(AppLanguage.CHINESE_SIMPLIFIED, store.load().language)
        assertEquals(AppLanguage.PORTUGUESE_BR, AppLanguage.fromTag("klingon"), "an unknown tag is the standard language")
        assertEquals(AppLanguage.PORTUGUESE_BR, AppSettings().language, "a new install starts in Brazilian Portuguese")
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
    }
}
