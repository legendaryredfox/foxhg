package io.github.romulofer.foxhg.core.data.csv

import io.github.romulofer.foxhg.core.FoxResult

/** Minimal RFC 4180 CSV: comma separated, double-quote quoting with `""` escapes, quoted fields may hold newlines. */
object Csv {
    fun format(rows: List<List<String>>): String =
        rows.joinToString("") { row -> row.joinToString(",") { quote(it) } + "\r\n" }

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field

    fun parse(text: String): FoxResult<List<List<String>>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var fieldStarted = false
        var i = 0
        val s = text.removePrefix("﻿")
        fun endField() { row.add(field.toString()); field.setLength(0); fieldStarted = false }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row) // blank lines are skipped
            row = ArrayList()
        }
        while (i < s.length) {
            val c = s[i]
            when {
                quoted -> when {
                    c == '"' && s.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                    c == '"' -> quoted = false
                    else -> field.append(c)
                }
                c == '"' && !fieldStarted -> { quoted = true; fieldStarted = true }
                c == ',' -> endField()
                c == '\r' -> { if (s.getOrNull(i + 1) == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> { field.append(c); fieldStarted = true }
            }
            i++
        }
        if (quoted) return FoxResult.Err("CSV ends inside a quoted field")
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) endRow()
        return FoxResult.Ok(rows)
    }
}

/** One integer column of a record type: its CSV header, accepted range, and how to read and write it. */
class CsvColumn<T>(val name: String, val range: IntRange, val get: (T) -> Int, val set: (T, Int) -> T)

/**
 * CSV import/export of a table of records. Layout: `id`, `name` (for reading only, ignored on import), then one column
 * per [columns] entry. Import matches rows by `id`; records without a row, and columns missing from the header, stay as
 * they are. Any bad id, unknown column, non-integer or out-of-range value rejects the whole file.
 */
class RecordCsv<T>(private val columns: List<CsvColumn<T>>) {
    fun export(records: List<T>, nameOf: (Int) -> String): String =
        Csv.format(
            listOf(listOf("id", "name") + columns.map { it.name }) +
                records.mapIndexed { i, r -> listOf(i.toString(), nameOf(i)) + columns.map { it.get(r).toString() } },
        )

    fun import(text: String, records: List<T>): FoxResult<List<T>> {
        val rows = when (val parsed = Csv.parse(text)) {
            is FoxResult.Ok -> parsed.value
            is FoxResult.Err -> return parsed
        }
        val header = rows.firstOrNull() ?: return FoxResult.Err("The CSV file is empty")
        val idCol = header.indexOf("id")
        if (idCol < 0) return FoxResult.Err("The CSV header has no 'id' column")
        val byName = columns.associateBy { it.name }
        val bound = ArrayList<Pair<Int, CsvColumn<T>>>()
        for ((c, h) in header.withIndex()) {
            if (c == idCol || h == "name") continue
            bound += c to (byName[h] ?: return FoxResult.Err("Unknown column '$h'"))
        }
        val out = records.toMutableList()
        for ((n, row) in rows.drop(1).withIndex()) {
            val line = n + 2
            if (row.size != header.size) return FoxResult.Err("Line $line has ${row.size} fields, expected ${header.size}")
            val id = row[idCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: bad id '${row[idCol]}'")
            if (id !in out.indices) return FoxResult.Err("Line $line: id $id is outside 0..${out.lastIndex}")
            var record = out[id]
            for ((c, col) in bound) {
                val cell = row[c].trim()
                val v = cell.toIntOrNull() ?: return FoxResult.Err("Line $line, '${col.name}': '$cell' is not a whole number")
                if (v !in col.range) return FoxResult.Err("Line $line, '${col.name}': $v is outside ${col.range.first}..${col.range.last}")
                record = col.set(record, v)
            }
            out[id] = record
        }
        return FoxResult.Ok(out)
    }
}
