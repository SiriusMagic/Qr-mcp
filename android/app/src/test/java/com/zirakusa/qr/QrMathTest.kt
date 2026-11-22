package com.zirakusa.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Filter
import com.zirakusa.qr.data.Grid
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.LinkCheck
import com.zirakusa.qr.data.MapValues
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.data.Warning
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.data.Read
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pure rules: what codes hold, building them, link warnings, the history, making and reading codes, the summary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrMathTest {
    @Test fun readsTheCommonFormats() {
        assertEquals(Content.Wifi("Casa;Norte", "p:ss\\word", Security.WPA, hidden = true), Payload.parse("""WIFI:T:WPA;S:Casa\;Norte;P:p\:ss\\word;H:true;;"""))
        assertEquals(Content.Wifi("Café", "", Security.OPEN), Payload.parse("WIFI:S:Café;T:nopass;;"))
        assertEquals(Content.Contact("Ana Pérez", "+593 99 123 4567", "ana@correo.ec"), Payload.parse("MECARD:N:Pérez,Ana;TEL:+593 99 123 4567;EMAIL:ana@correo.ec;;"))
        assertEquals("Luis Mora", (Payload.parse("BEGIN:VCARD\nVERSION:3.0\nN:Mora;Luis\nTEL;TYPE=CELL:0991234567\nEND:VCARD") as Content.Contact).name)
        assertEquals("0991234567", (Payload.parse("BEGIN:VCARD\nFN:Luis Mora\nTEL;TYPE=CELL:0991234567\nEND:VCARD") as Content.Contact).phone)
        assertEquals(Content.Phone("+593991234567"), Payload.parse("tel:+593991234567"))
        assertEquals(Content.Email("a@b.ec", "Hola mundo", "Qué tal"), Payload.parse("mailto:a@b.ec?subject=Hola%20mundo&body=Qu%C3%A9%20tal"))
        assertEquals(Content.Email("a@b.ec", "S", "B"), Payload.parse("MATMSG:TO:a@b.ec;SUB:S;BODY:B;;"))
        assertEquals(Content.Sms("0991234567", "Voy: en camino"), Payload.parse("SMSTO:0991234567:Voy: en camino"))
        assertEquals(Content.Place(-0.18, -78.47), Payload.parse("geo:-0.18,-78.47?z=12"))
        assertEquals(Content.Text("geo:200,1"), Payload.parse("geo:200,1"))
        assertEquals(Content.Link("https://zirakusa.com/a?b=1"), Payload.parse(" https://zirakusa.com/a?b=1 "))
        assertEquals(Type.TEXT, Payload.parse("javascript:alert(1)").kind)                       // only http(s) are links
        assertEquals(Type.TEXT, Payload.parse("intent://x#Intent;end").kind)
        assertEquals(Type.TEXT, Payload.parse("https://a b.com").kind)
        assertEquals(Type.TEXT, Payload.parse("9780306406157").kind)
    }

    @Test fun buildsAndChecks() {
        val wifi = Content.Wifi("Casa;Norte", "clave:segura", Security.WPA)
        assertEquals("""WIFI:T:WPA;S:Casa\;Norte;P:clave\:segura;;""", Payload.build(wifi))
        assertEquals(wifi, Payload.parse(Payload.build(wifi)))
        val contact = Content.Contact("Ana Pérez", "+593 99", "ana@x.ec", "Zirakusa", "Hola; amiga")
        assertEquals(contact, Payload.parse(Payload.build(contact)))
        assertEquals("https://zirakusa.com", Payload.build(Content.Link("zirakusa.com")))
        assertEquals("tel:+59399", Payload.build(Content.Phone("+593 (99)")))
        assertEquals(Content.Sms("099123", "hola"), Payload.parse(Payload.build(Content.Sms("099 123", "hola"))))
        assertEquals(Payload.Problem.EMPTY, Payload.problem(Content.Text("  ")))
        assertEquals(Payload.Problem.WIFI_PASSWORD, Payload.problem(Content.Wifi("Casa", "corta", Security.WPA)))
        assertNull(Payload.problem(Content.Wifi("Casa", "", Security.OPEN)))
        assertEquals(Payload.Problem.BAD_EMAIL, Payload.problem(Content.Email("no-es-correo")))
        assertEquals(Payload.Problem.BAD_LINK, Payload.problem(Content.Link("https://")))
        assertEquals(Payload.Problem.TOO_LONG, Payload.problem(Content.Text("x".repeat(Payload.MAX + 1))))
        assertEquals("Casa", Payload.title(wifi.copy(ssid = "Casa")))
        assertEquals("zirakusa.com/x", Payload.title(Content.Link("https://zirakusa.com/x")))
    }

    @Test fun linkWarnings() {
        assertEquals(LinkCheck.Result("zirakusa.com", emptyList()), LinkCheck.check("https://zirakusa.com/menu"))
        assertEquals(listOf(Warning.NOT_HTTPS, Warning.IP_ADDRESS, Warning.ODD_PORT), LinkCheck.check("http://192.168.1.10:8080/")!!.warnings)
        assertEquals(listOf(Warning.LOOKALIKE), LinkCheck.check("https://xn--pypal-4ve.com")!!.warnings)
        assertEquals(listOf(Warning.SHORTENER), LinkCheck.check("https://bit.ly/abc")!!.warnings)
        val trick = LinkCheck.check("https://banco.com@malo.ru/login")!!
        assertEquals("malo.ru", trick.host); assertTrue(Warning.USER_INFO in trick.warnings)
        assertEquals(listOf(Warning.APP_DOWNLOAD), LinkCheck.check("https://x.com/app.APK")!!.warnings)
        assertNull(LinkCheck.check("ftp://x.com")); assertNull(LinkCheck.check("no es enlace"))
    }

    @Test fun historyKeepsAndTrims() {
        var t = 1_000_000L
        val h = History(MapValues()) { t }
        val a = h.add("https://a.com", "QR_CODE", Source.CAMERA)
        assertEquals(a, h.add("https://a.com", "QR_CODE", Source.CAMERA))                     // the camera sees it again: not twice
        t += History.REPEAT_MS + 1
        val b = h.add("https://a.com", "QR_CODE", Source.CAMERA)
        assertEquals(listOf(b.id, a.id), h.list().map { it.id })
        h.update(a.copy(favorite = true, name = "Menú"))
        assertEquals(listOf(a.id), History.filter(h.list(), Filter.FAVORITES).map { it.id })
        assertEquals(listOf(a.id), History.filter(h.list(), Filter.ALL, "menu").map { it.id }) // name, accents ignored
        assertEquals(1, h.clear()); assertEquals(listOf(a.id), h.list().map { it.id })        // favourites stay
        repeat(History.MAX + 5) { t += 10_000; h.add("c$it", "QR_CODE", Source.CREATED) }
        assertEquals(History.MAX, h.list().size); assertTrue(h.list().any { it.id == a.id })   // the favourite survives trimming
        assertTrue(h.delete(a.id)); assertFalse(h.delete(a.id))
        assertEquals(listOf("c${History.MAX + 4}"), History.filter(h.list(), Filter.CREATED).take(1).map { it.text })
    }

    private fun frame(grid: Grid, scale: Int = 4): Triple<ByteArray, Int, Int> {
        val side = grid.size * scale
        return Triple(ByteArray(side * side) { i -> if (grid[(i % side) / scale, (i / side) / scale]) 0 else 0xFF.toByte() }, side, side)
    }

    @Test fun makesAndReadsCodes() {
        val text = "WIFI:T:WPA;S:Casa;P:clave segura ñ;;"
        val (y, w, h) = frame(Codes.encode(text))
        assertEquals(Read(text, "QR_CODE"), Codes.readFrame(y, w, h))
        assertEquals(Read(text, "QR_CODE"), Codes.readFrame(y.map { (255 - (it.toInt() and 0xFF)).toByte() }.toByteArray(), w, h)) // white on black
        val padded = ByteArray((w + 16) * h).also { out -> for (r in 0 until h) System.arraycopy(y, r * w, out, r * (w + 16), w) }
        assertEquals(text, Codes.readFrame(padded, w, h, rowStride = w + 16)!!.text)
        assertNull(Codes.readFrame(ByteArray(w * h) { 0x7F }, w, h))
        // A product bar code held sideways in the frame.
        val m = MultiFormatWriter().encode("9780306406157", BarcodeFormat.EAN_13, 400, 120)
        val bar = ByteArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0 else 0xFF.toByte() }
        val sideways = Codes.rotate(bar, m.width, m.height)
        assertEquals(Read("9780306406157", "EAN_13"), Codes.readFrame(sideways, m.height, m.width))
        assertNull(Codes.readFrame(sideways, m.height, m.width, withBarcodes = false))
        val argb = IntArray(w * h) { if (y[it].toInt() == 0) 0xFF000000.toInt() else -1 }
        assertEquals(text, Codes.readPixels(argb, w, h)!!.text)
        val png = Codes.png(Codes.encode("hola"), scale = 3)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), png.copyOf(4))
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(Codes.encode("hola").size * 3, bitmap.width)
    }

    @Test fun summaryAndState() {
        val now = 10L * 86_400_000L
        val h = History(MapValues()) { now - 3_600_000 }
        h.add("WIFI:T:WPA;S:Casa;P:secreta123;;", "QR_CODE", Source.CAMERA)
        val old = History(MapValues()) { 0L }.add("viejo", "QR_CODE", Source.CAMERA)
        val lines = SummaryLines.lines(h.list() + old, now) { it.content.kind.key }
        assertEquals(listOf("wifi · Casa"), lines)                                              // the password never shows
        assertTrue(SummaryLines.lines(emptyList(), now) { it.content.kind.key }.isEmpty())

        val kv = MapValues()
        val state = QrState(kv)
        state.scanned(Read("https://zirakusa.com", "QR_CODE"), Source.CAMERA)
        assertEquals(Page.RESULT, state.page); assertEquals(1, state.items.size)
        state.editAsNew(state.shown!!)
        assertEquals(Type.LINK, state.type); assertEquals("https://zirakusa.com", state.form.url); assertEquals(Page.MAIN, state.page)
        state.form = state.form.copy(url = "")
        assertEquals(Payload.Problem.BAD_LINK, state.problem); assertNull(state.saveDraft())
        state.changeKeepHistory(false)
        state.scanned(Read("otro", "QR_CODE"), Source.IMAGE)
        assertEquals(0L, state.shown!!.id); assertEquals(1, QrState(kv).items.size)           // not kept
        assertFalse(QrState(kv).keepHistory)
    }
}
