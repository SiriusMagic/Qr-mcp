package com.zirakusa.qr.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Exportar» and «Importar» of «Historial» as CSV (RFC 4180: commas, quotes doubled, line breaks inside quotes),
 * with a UTF‑8 mark so spreadsheets show accents. Pure: the activity only writes and reads the file the user picks.
 *
 * A cell that a spreadsheet would run as a formula (starting with = + - @) gets a leading «'», taken off on import.
 */
object HistoryCsv {
    val HEADER = listOf("time", "format", "source", "favorite", "name", "text")
    const val MAX_CHARS = 2_000_000
    private const val BOM = '\uFEFF'

    fun build(items: List<Entry>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append(BOM)
        line(HEADER)
        items.forEach { e ->
            line(listOf(Instant.ofEpochMilli(e.at).atZone(zone).toOffsetDateTime().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), e.format, e.source.key,
                if (e.favorite) "1" else "0", e.name, e.text))
        }
    }

    private fun StringBuilder.line(cells: List<String>) { cells.joinTo(this, ",") { cell(it) }; append("\r\n") }

    /** One cell, quoted when it has a comma, a quote, a line break or spaces at the ends. */
    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA) "'$value" else value
        val quote = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' } || safe != safe.trim()
        return if (quote) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** The rows of a CSV text (comma or semicolon, guessed from the first line), cells unquoted. */
    fun rows(csv: String): List<List<String>> {
        val text = csv.take(MAX_CHARS).removePrefix(BOM.toString())
        val sep = delimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.clear() }
        fun endRow() { endCell(); if (row.any { it.isNotEmpty() }) rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> cell.append(c)
                c == '"' && cell.isEmpty() -> quoted = true
                c == sep -> endCell()
                c == '\r' && text.getOrNull(i + 1) == '\n' -> { endRow(); i++ }
                c == '\n' || c == '\r' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * The codes in a CSV: this app's own file or another app's (a column named text, texto, contenido, content…; if
     * none, the last one). Times and formats that can't be read fall back to [now] and QR. At most [History.MAX].
     */
    fun parse(csv: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Entry> {
        val all = rows(csv)
        if (all.isEmpty()) return emptyList()
        val head = all[0].map { History.plain(it.trim()) }
        fun col(vararg names: String) = head.indexOfFirst { it in names }
        val textCol = col("text", "texto", "contenido", "content", "contents", "value", "valor", "codigo", "code", "data", "datos")
        val hasHeader = textCol >= 0 || head.any { it in KNOWN }
        val body = if (hasHeader) all.drop(1) else all
        val t = col("time", "fecha", "date", "hora", "timestamp"); val f = col("format", "formato")
        val s = col("source", "origen"); val fav = col("favorite", "favorito"); val n = col("name", "nombre", "etiqueta")
        return body.mapNotNull { r ->
            fun at(i: Int) = r.getOrNull(i)?.let(::unformula).orEmpty()
            val text = if (textCol >= 0) at(textCol) else at(r.lastIndex)
            if (text.isBlank()) null
            else Entry(0, text, at(f).trim().uppercase().takeIf { it.matches(Regex("[A-Z0-9_]{2,20}")) } ?: Entry.QR,
                Source.from(at(s).trim().lowercase()), time(at(t).trim(), zone) ?: now, at(fav).trim() in setOf("1", "true", "sí", "si", "yes"),
                at(n).trim())
        }.take(History.MAX)
    }

    /** ISO with offset, ISO instant, local ISO or milliseconds. */
    internal fun time(s: String, zone: ZoneId): Long? = if (s.isEmpty()) null else
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: s.toLongOrNull()?.takeIf { it > 0 }

    private fun unformula(s: String) = if (s.length > 1 && s[0] == '\'' && s[1] in FORMULA) s.substring(1) else s

    private fun delimiter(text: String): Char {
        val first = text.lineSequence().firstOrNull().orEmpty()
        return if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
    }

    private const val FORMULA = "=+-@\t\r"
    private val KNOWN = setOf("time", "fecha", "date", "format", "formato", "source", "origen", "favorite", "favorito", "name", "nombre")
}
