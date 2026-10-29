package io.github.legendaryredfox.foxhg.core.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule of [GameLayout]: an editor or data type reads where things are from `project.layout`, so another game or language is another
 * layout and not a change to every editor. These checks read the source files of `core` and `app`; the folders that are English HeartGold
 * on purpose are listed, and shrink as M8 makes them per-language (docs/plan.md).
 */
class LayoutUsageTest {
    private val roots = listOf(Path.of("src/commonMain"), Path.of("src/jvmMain"), Path.of("../app/src/jvmMain"))

    private fun sources(): List<Pair<String, String>> = roots.filter { Files.exists(it) }.flatMap { root ->
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.map { it.toString().replace('\\', '/') to Files.readString(it) }.toList()
        }
    }

    /** Folders and files that name the English layout themselves: the layout itself, the ROM patches and battle rules (code and offsets of one build of the game). */
    private val englishOnly = listOf("/core/game/", "/core/patch/", "/core/data/battlerule/", "/core/project/RomProject.kt")

    @Test
    fun theOldLayoutObjectIsGone() {
        assertTrue(sources().size > 150, "the check reads the sources of core and app")
        val found = sources().filter { (_, text) -> Regex("""\bHgssLayout\b""").containsMatchIn(text) }.map { it.first }
        assertEquals(emptyList(), found)
    }

    @Test
    fun noEditorOrDataTypeNamesTheEnglishLayout() {
        val found = sources().filter { (path, text) -> englishOnly.none { path.contains(it) } && (text.contains("HgssEnglishLayout") || text.contains("HgssLayoutBase")) }.map { it.first }
        assertEquals(emptyList(), found, "read the layout from project.layout (or take a GameLayout parameter)")
    }

    @Test
    fun noEditorOrDataTypeHoldsAFilePathOfTheRom() {
        val path = Regex(""""[a-z]/[0-9]/[0-9]/[0-9]+"|"data/[a-z_]+/[a-z_]+\.[a-z]+"""")
        val found = sources().filter { (file, text) -> englishOnly.none { file.contains(it) } && path.containsMatchIn(text) }.map { it.first }
        assertEquals(emptyList(), found, "put the path in GameLayout")
    }

    @Test
    fun theEnglishLayoutHasTheValuesTheEditorsWereWrittenAgainst() {
        assertEquals("a/0/2/7", HgssEnglishLayout.messages)
        assertEquals("a/0/0/2", HgssEnglishLayout.personal)
        assertEquals(0x1000CC, HgssEnglishLayout.tmMovesArm9Offset)
        assertEquals(0x5100, HgssEnglishLayout.classTableAreaPrizes)
        assertEquals(0x5500, HgssEnglishLayout.encounterMusicArea)
        assertTrue(HgssEnglishLayout.rivalScriptFiles.contains(850))
    }

    @Test
    fun aLanguageLayoutOverridesOnlyWhatDiffers() {
        val other = object : HgssLayoutBase() { override val messages = "a/9/9/9" }
        assertEquals("a/9/9/9", other.messages)
        assertEquals(HgssEnglishLayout.personal, other.personal)
        assertEquals(HgssEnglishLayout.encounterMusicArea, other.encounterMusicArea)
    }
}
