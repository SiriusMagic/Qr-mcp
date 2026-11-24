package com.zirakusa.qr

import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Barcodes
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.HistoryCsv
import com.zirakusa.qr.data.MapValues
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.ScanGate
import com.zirakusa.qr.data.Settings
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.Step
import com.zirakusa.qr.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** The rules of 0.2.0: events, bar codes, CSV, duplicates, «Escaneo continuo» and «Confirmar», shared text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewFeaturesTest {
    private val quito = ZoneId.of("America/Guayaquil")

    @Test fun eventsBuildAndRead() {
        val e = Content.Event("Cena, familia; amigos", LocalDateTime.of(2026, 11, 24, 18, 30), LocalDateTime.of(2026, 11, 24, 21, 0), "Quito\nCentro")
        val text = Payload.build(e)
        assertEquals("BEGIN:VEVENT\nSUMMARY:Cena\\, familia\\; amigos\nDTSTART:20261124T183000\nDTEND:20261124T210000\nLOCATION:Quito\\nCentro\nEND:VEVENT", text)
        assertEquals(e, Payload.parse(text))
        val day = Content.Event("Feriado", LocalDateTime.of(2026, 12, 24, 0, 0), LocalDateTime.of(2026, 12, 26, 0, 0), allDay = true)
        assertTrue(Payload.build(day).contains("DTSTART;VALUE=DATE:20261224\nDTEND;VALUE=DATE:20261227"))
        assertEquals(day, Payload.parse(Payload.build(day)))
        // Another app's: inside VCALENDAR, CRLF, a folded line and UTC time.
        val other = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Reunión de\r\n  equipo\r\nDTSTART:20261124T150000Z\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val read = Payload.event(other, quito)
        assertEquals("Reunión de equipo", read.title); assertEquals(LocalDateTime.of(2026, 11, 24, 10, 0), read.start)
        assertEquals(Type.EVENT, Payload.parse(other).kind)
        assertEquals("Reunión de equipo", Payload.title(read))
        // Typed dates.
        assertEquals(LocalDateTime.of(2026, 11, 24, 18, 30) to true, Payload.moment("24/11/2026 18:30"))
        assertEquals(LocalDateTime.of(2026, 11, 24, 18, 30) to true, Payload.moment("2026-11-24T18:30"))
        assertEquals(LocalDateTime.of(2026, 11, 24, 0, 0) to false, Payload.moment(" 2026-11-24 "))
        assertNull(Payload.moment("31/02/2026")); assertNull(Payload.moment("mañana")); assertNull(Payload.moment("24/11/2026 25:00"))
        assertEquals("24/11/2026 18:30", Payload.momentText(LocalDateTime.of(2026, 11, 24, 18, 30)))
        // What «Crear» says.
        assertEquals(Payload.Problem.EMPTY, Payload.problem(e.copy(title = " ")))
        assertEquals(Payload.Problem.BAD_DATE, Payload.problem(e.copy(start = null)))
        assertEquals(Payload.Problem.BAD_END, Payload.problem(e.copy(end = LocalDateTime.of(2026, 11, 24, 8, 0))))
        assertNull(Payload.problem(e))
    }

    @Test fun barcodesCheckAndDraw() {
        assertEquals(7, Barcodes.checkDigit("978030640615"))                                     // 9780306406157
        assertEquals("9780306406157", Barcodes.normalize(BarFormat.EAN_13, "978-0306-40615"))
        assertNull(Barcodes.problem(BarFormat.EAN_13, "9780306406157"))
        assertEquals(Barcodes.Problem.CHECKSUM, Barcodes.problem(BarFormat.EAN_13, "9780306406158"))
        assertEquals(Barcodes.Problem.LENGTH, Barcodes.problem(BarFormat.EAN_13, "97803064"))
        assertEquals(Barcodes.Problem.DIGITS_ONLY, Barcodes.problem(BarFormat.EAN_8, "1234A67"))
        assertEquals("96385074", Barcodes.normalize(BarFormat.EAN_8, "9638507"))
        assertEquals("036000291452", Barcodes.normalize(BarFormat.UPC_A, "03600029145"))
        assertEquals(Barcodes.Problem.EMPTY, Barcodes.problem(BarFormat.CODE_128, "  "))
        assertEquals(Barcodes.Problem.CHARACTERS, Barcodes.problem(BarFormat.CODE_128, "Año"))
        assertEquals(Barcodes.Problem.TOO_LONG, Barcodes.problem(BarFormat.CODE_128, "x".repeat(81)))
        assertEquals("CAJA-12", Barcodes.normalize(BarFormat.CODE_39, "caja-12"))
        assertNull(Barcodes.problem(BarFormat.CODE_39, "caja-12"))
        assertEquals(Barcodes.Problem.CHARACTERS, Barcodes.problem(BarFormat.CODE_39, "caja_12"))
        // Each one drawn and read back.
        listOf(BarFormat.EAN_13 to "9780306406157", BarFormat.EAN_8 to "96385074", BarFormat.UPC_A to "036000291452",
            BarFormat.CODE_128 to "Lote 42/B", BarFormat.CODE_39 to "CAJA-12").forEach { (f, text) ->
            val bars = Codes.bars(text, f)!!
            val w = bars.size * 3; val h = 80
            val argb = IntArray(w * h) { if (bars[(it % w) / 3]) 0xFF000000.toInt() else -1 }
            val read = Codes.readPixels(argb, w, h)!!
            assertEquals(f.name, text, read.text); assertEquals(f.format, read.format)
            assertTrue(Codes.picture(text, f.format)!!.size > 100)
        }
        assertNull(Codes.picture("x", "PDF_417"))
    }

    @Test fun csvQuotesCommasAndLines() {
        assertEquals("simple", HistoryCsv.cell("simple"))
        assertEquals("\"a,b\"", HistoryCsv.cell("a,b"))
        assertEquals("\"di \"\"hola\"\"\"", HistoryCsv.cell("di \"hola\""))
        assertEquals("\"uno\ndos\"", HistoryCsv.cell("uno\ndos"))
        assertEquals("'=1+1", HistoryCsv.cell("=1+1"))                                             // not a formula in a spreadsheet
        assertEquals(listOf(listOf("a", "b,c", "d\"e"), listOf("1", "x\r\ny", "")), HistoryCsv.rows("a,\"b,c\",\"d\"\"e\"\r\n1,\"x\r\ny\",\r\n\r\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), HistoryCsv.rows("a;b\n1;2"))      // semicolons
        val items = listOf(
            Entry(2, "WIFI:T:WPA;S:Casa, Norte;P:clave\"1;;", Entry.QR, Source.CAMERA, 1_763_700_000_000L, favorite = true, name = "Casa"),
            Entry(1, "línea 1\nlínea 2", "CODE_128", Source.CREATED, 1_763_600_000_000L),
            Entry(3, "-5 °C", Entry.QR, Source.IMAGE, 1_763_500_000_000L))
        val csv = HistoryCsv.build(items, quito)
        assertTrue(csv.startsWith("\uFEFFtime,format,source,favorite,name,text\r\n2025-11-20T23:40:00-05:00,QR_CODE,camera,1,Casa,"))
        assertTrue(csv.contains("-05:00"))
        val back = HistoryCsv.parse(csv, now = 0, zone = quito)
        assertEquals(items.map { it.copy(id = 0) }, back)
        // Another app's file: its own columns, no time.
        val foreign = HistoryCsv.parse("Fecha,Tipo,Contenido\n,QR,https://zirakusa.com\n,,\n", now = 5)
        assertEquals(listOf(Entry(0, "https://zirakusa.com", Entry.QR, Source.CAMERA, 5)), foreign)
        assertEquals(listOf("hola", "chao"), HistoryCsv.parse("hola\nchao", now = 5).map { it.text })  // no header: one code a line
        assertTrue(HistoryCsv.parse("", now = 5).isEmpty())
        // Importing twice adds nothing the second time.
        val h = History(MapValues()) { 9L }
        assertEquals(3, h.import(back)); assertEquals(0, h.import(back))
        assertEquals(listOf("Casa", "", ""), h.list().map { it.name }); assertEquals(listOf(3L, 2L, 1L), h.list().map { it.id }.sortedDescending())
        assertEquals(listOf(1_763_700_000_000L, 1_763_600_000_000L, 1_763_500_000_000L), h.list().map { it.at })
    }

    @Test fun duplicatesAndTheGate() {
        var t = 1_000_000L
        val h = History(MapValues()) { t }
        val a = h.add("https://a.com", "QR_CODE", Source.CAMERA, duplicates = false)
        h.update(a.copy(name = "A", favorite = true))
        h.add("https://b.com", "QR_CODE", Source.CAMERA, duplicates = false)
        t += 60_000
        val again = h.add("https://a.com", "QR_CODE", Source.IMAGE, duplicates = false)            // moves to the top, keeps name and star
        assertEquals(a.id, again.id); assertEquals(2, h.list().size); assertEquals(a.id, h.list().first().id)
        assertEquals("A", h.list().first().name); assertEquals(t, h.list().first().at)
        assertEquals(3, h.add("https://a.com", "QR_CODE", Source.CREATED, duplicates = false).id)     // made is not read: its own line
        t += 60_000
        h.add("https://a.com", "QR_CODE", Source.CAMERA, duplicates = true)
        assertEquals(4, h.list().size)

        val gate = ScanGate(1_000)
        assertTrue(gate.accept("x", 0)); assertFalse(gate.accept("x", 500)); assertFalse(gate.accept("x", 1_400)) // still in view
        assertTrue(gate.accept("x", 2_500))                                                       // out of sight for a while
        assertTrue(gate.accept("y", 2_600)); assertTrue(gate.accept("x", 2_700))
        gate.reset(); assertTrue(gate.accept("x", 2_800))

        val s = Settings(MapValues())
        assertFalse(s.sound); assertFalse(s.autoCopy); assertFalse(s.continuous); assertFalse(s.confirm); assertFalse(s.duplicates); assertFalse(s.frontCamera)
    }

    @Test fun continuousConfirmAndShared() {
        var t = 1_000_000L
        val kv = MapValues()
        val state = QrState(kv) { t }
        state.changeContinuous(true)
        val first = state.seen(Read("https://a.com", "QR_CODE"), Source.CAMERA)
        assertTrue(first is Step.Done); assertEquals(Page.MAIN, state.page); assertEquals("https://a.com", state.quick!!.text)
        t += 100
        assertEquals(Step.Ignored, state.seen(Read("https://a.com", "QR_CODE"), Source.CAMERA))      // same code in view
        assertTrue(state.seen(Read("https://b.com", "QR_CODE"), Source.CAMERA) is Step.Done)
        assertEquals(2, state.items.size)
        assertTrue(state.seen(Read("foto", "QR_CODE"), Source.IMAGE) is Step.Done); assertEquals(Page.RESULT, state.page) // a picture is shown
        state.closeResult(); state.changeContinuous(false); state.changeConfirm(true)
        assertEquals(Step.Waiting, state.seen(Read("WIFI:S:Casa;T:nopass;;", "QR_CODE"), Source.CAMERA))
        assertEquals(3, state.items.size); assertEquals(Page.MAIN, state.page)                      // nothing kept yet
        state.dropPending(); assertNull(state.pending); assertEquals(3, state.items.size)
        state.seen(Read("WIFI:S:Casa;T:nopass;;", "QR_CODE"), Source.CAMERA)
        assertEquals("Casa", state.confirmPending()!!.title); assertEquals(Page.RESULT, state.page); assertEquals(4, state.items.size)
        assertTrue(QrState(kv).confirm)

        // Shared text and «Desde el portapapeles» fill «Crear».
        state.prefill("https://zirakusa.com/menu")
        assertEquals(Tab.CREATE, state.tab); assertEquals(Page.MAIN, state.page); assertEquals(Type.LINK, state.type)
        state.prefill("Hola, mundo"); assertEquals(Type.TEXT, state.type); assertEquals("Hola, mundo", state.form.text)
        state.prefill(Payload.build(Content.Event("Cita", LocalDateTime.of(2026, 1, 5, 9, 0))))
        assertEquals(Type.EVENT, state.type); assertEquals("05/01/2026 09:00", state.form.start)
        state.form = state.form.copy(end = "no sé")
        assertEquals(Payload.Problem.BAD_END, state.problem); assertNull(state.draftText)
        state.form = state.form.copy(end = "")
        assertTrue(state.draftText!!.startsWith("BEGIN:VEVENT"))

        // A bar code made in «Crear».
        state.barFormat = BarFormat.EAN_13
        state.form = state.form.copy(code = "978030640615")
        assertEquals("9780306406157", state.draftText)
        val made = state.saveDraft()!!
        assertEquals("EAN_13", made.format); assertEquals(Source.CREATED, made.source)
        state.editAsNew(made); assertEquals(BarFormat.EAN_13, state.barFormat); assertEquals("9780306406157", state.form.code)
        state.form = state.form.copy(code = "9780306406158"); assertEquals(Barcodes.Problem.CHECKSUM, state.barProblem); assertNull(state.saveDraft())
        assertEquals(5, state.clearHistory(all = true)); assertTrue(state.items.isEmpty())
    }
}
