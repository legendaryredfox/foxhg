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

    @Test
    fun moveAndItemCsvRefuseValuesTheGameHasNoNameFor() {
        val move = io.github.legendaryredfox.foxhg.core.data.move.MoveData.CSV
        val item = io.github.legendaryredfox.foxhg.core.data.item.ItemData.CSV
        val moves = listOf((io.github.legendaryredfox.foxhg.core.data.move.MoveData.parse(ByteArray(16)) as FoxResult.Ok).value)
        val items = listOf((io.github.legendaryredfox.foxhg.core.data.item.ItemData.parse(ByteArray(34)) as FoxResult.Ok).value)
        assertTrue(move.import("id,category\n0,2\n", moves) is FoxResult.Ok)
        assertTrue(move.import("id,category\n0,3\n", moves) is FoxResult.Err)
        assertTrue(move.import("id,type\n0,17\n", moves) is FoxResult.Ok)
        assertTrue(move.import("id,type\n0,18\n", moves) is FoxResult.Err)
        assertTrue(item.import("id,field_pocket\n0,7\n", items) is FoxResult.Ok)
        assertTrue(item.import("id,field_pocket\n0,8\n", items) is FoxResult.Err)
    }
}
