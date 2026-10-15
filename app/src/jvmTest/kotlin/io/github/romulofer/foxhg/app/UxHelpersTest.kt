package io.github.romulofer.foxhg.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UxHelpersTest {
    @Test
    fun pluralAddsAnSOnlyUnlessThereIsExactlyOne() {
        assertEquals("1 file", plural(1, "file"))
        assertEquals("0 files", plural(0, "file"))
        assertEquals("2 areas", plural(2, "area"))
    }

    @Test
    fun aLongListIsCutToTheLimitWithTheRestCounted() {
        assertEquals("a, b", summarize(listOf("a", "b")))
        assertEquals("a, b, c, d", summarize(listOf("a", "b", "c", "d")))
        assertEquals("a, b and 3 more", summarize(listOf("a", "b", "c", "d", "e"), limit = 2))
    }

    @Test
    fun encounterChangesReadInPlainWords() {
        assertEquals("Grass slot 8, morning species", friendlyChange("land 8 morning"))
        assertEquals("Grass slot 9, level", friendlyChange("land 9 level"))
        assertEquals("Rate, walking", friendlyChange("rate walking"))
        assertEquals("Surf 1 species", friendlyChange("surf 1 species"))
    }

    private fun commandColor(dark: Boolean): Color {
        val styled = ScriptHighlight(dark).filter(AnnotatedString("script_1:\n    PlaySE 1500\n")).text
        val span = styled.spanStyles.first { styled.text.substring(it.start, it.end) == "PlaySE" }
        return span.item.color
    }

    @Test
    fun scriptColoursAreReadableOnBothThemes() {
        // Contrast against the dark background (about 0.01 luminance) and the light one (about 0.9) needs a clear gap.
        assertTrue(commandColor(dark = true).luminance() > 0.3f, "command names are light enough on the dark theme")
        assertTrue(commandColor(dark = false).luminance() < 0.2f, "and dark enough on the light theme")
    }
}
