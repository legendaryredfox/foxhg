package io.github.romulofer.foxhg.core.text

import io.github.romulofer.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextArchiveTest {
    private val charmap = Charmap.HGSS

    private fun entry(text: String, compressed: Boolean = false) =
        TextEntry((charmap.encode(text) as FoxResult.Ok).value, compressed)

    @Test
    fun charmapRoundTripsText() {
        val text = "PIKACHU used THUNDER 25\\nIt is super effective."
        val codes = (charmap.encode(text) as FoxResult.Ok).value
        assertEquals(text, charmap.decode(codes))
    }

    @Test
    fun charmapKeepsUnknownCodesAsHexEscapes() {
        assertEquals("\\xFFFE", charmap.render(0xFFFE))
        assertContentEquals(intArrayOf(0xFFFE, 0x0100), (charmap.encode("\\xFFFE{STRVAR_1}") as FoxResult.Ok).value)
    }

    @Test
    fun charmapRejectsCharactersTheGameCannotShow() {
        assertIs<FoxResult.Err>(charmap.encode("😀"))
    }

    @Test
    fun archiveRoundTripsPlainAndCompressedEntries() {
        val archive = TextArchive(
            key = 0x1234,
            entries = listOf(entry("Hello"), entry(""), entry("Compressed text here", compressed = true), entry("Last one")),
        )
        val bytes = archive.toBytes()
        val parsed = (TextArchive.parse(bytes) as FoxResult.Ok).value
        assertEquals(archive.key, parsed.key)
        assertEquals(archive.entries, parsed.entries)
        assertContentEquals(bytes, parsed.toBytes())
        assertEquals("Compressed text here", parsed.entries[2].text())
    }

    @Test
    fun rejectsTruncatedArchive() {
        val bytes = TextArchive(7, listOf(entry("abc"))).toBytes()
        assertIs<FoxResult.Err>(TextArchive.parse(bytes.copyOf(bytes.size - 4)))
        assertIs<FoxResult.Err>(TextArchive.parse(ByteArray(2)))
    }
}
