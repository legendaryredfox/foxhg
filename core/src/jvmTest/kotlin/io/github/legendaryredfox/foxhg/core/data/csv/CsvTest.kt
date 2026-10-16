package io.github.legendaryredfox.foxhg.core.data.csv

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvTest {
    private fun rows(text: String) = (Csv.parse(text) as FoxResult.Ok).value

    @Test
    fun quotingRoundTrips() {
        val table = listOf(listOf("a,b", "say \"hi\"", "line\nbreak"), listOf("", "x", "y"))
        assertEquals(table, rows(Csv.format(table)))
    }

    @Test
    fun acceptsBomLfAndBlankLines() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("﻿a,b\n\n1,2"))
    }

    @Test
    fun unterminatedQuoteIsAnError() {
        assertTrue(Csv.parse("a,\"b") is FoxResult.Err)
    }

    private data class Rec(val x: Int, val y: Int)

    private val csv = RecordCsv(
        listOf(
            CsvColumn<Rec>("x", 0..10, { it.x }, { r, v -> r.copy(x = v) }),
            CsvColumn("y", -5..5, { it.y }, { r, v -> r.copy(y = v) }),
        ),
    )

    @Test
    fun importUpdatesOnlyListedRowsAndColumns() {
        val base = listOf(Rec(1, 1), Rec(2, 2), Rec(3, 3))
        assertEquals(base, (csv.import(csv.export(base) { "n$it" }, base) as FoxResult.Ok).value)
        val out = (csv.import("id,y\n1,-4\n", base) as FoxResult.Ok).value
        assertEquals(listOf(Rec(1, 1), Rec(2, -4), Rec(3, 3)), out)
    }

    @Test
    fun importRejectsBadInput() {
        val base = listOf(Rec(0, 0))
        for (bad in listOf("id,x\n0,11\n", "id,x\n5,1\n", "id,z\n0,1\n", "id,x\n0,abc\n", "x\n1\n", "id,x\n0\n")) {
            assertTrue(csv.import(bad, base) is FoxResult.Err, bad)
        }
    }
}
