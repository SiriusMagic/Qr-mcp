package com.zirakusa.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.qr.data.ArtLayout
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Detail
import com.zirakusa.qr.data.Detail.Kind
import com.zirakusa.qr.data.MapValues
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.SharedContact
import com.zirakusa.qr.data.VCard
import com.zirakusa.qr.ui.ContactShare
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.Picture
import com.zirakusa.qr.ui.QrApp
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * «Compartir un contacto»: the vCard (built, escaped and read back), what is ticked, the «Arbolito» layout never touching
 * the code, the real drawn cards read back with ZXing (also at half size), the fallback to the classic code, and the
 * screens (preview, confirm before sharing, confirm before saving a scanned contact).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactShareTest {
    @get:Rule val compose = createComposeRule()

    private val ana = SharedContact("Ana María Pérez", listOf(Detail(Kind.PHONE, "+593 99 123 4567"), Detail(Kind.PHONE, "02-234 5678"),
        Detail(Kind.EMAIL, "ana@correo.ec"), Detail(Kind.ORG, "Zirakusa, S.A."), Detail(Kind.TITLE, "Diseñadora"),
        Detail(Kind.ADDRESS, "Av. Amazonas N24; Quito"), Detail(Kind.URL, "https://ana.example"), Detail(Kind.NOTE, "Línea 1\nLínea 2")))

    @Test fun vcardBuildsEscapesAndReadsBack() {
        val all = ana.all
        assertEquals(Kind.NAME, all.first().kind); assertEquals(9, all.size)
        assertEquals(setOf(0, 1, 2, 3), ana.defaults())                                         // name, phones, email
        val text = VCard.build(all)
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Pérez;Ana María;;;\r\nFN:Ana María Pérez\r\n"))
        assertTrue(text.contains("TEL;TYPE=CELL:+593991234567\r\n")); assertTrue(text.contains("ORG:Zirakusa\\, S.A.\r\n"))
        assertTrue(text.contains("NOTE:Línea 1\\nLínea 2\r\n")); assertTrue(text.endsWith("END:VCARD"))
        // Another phone's reading of it: every field comes back exactly.
        val c = Payload.parse(text) as Content.Contact
        assertEquals("Ana María Pérez", c.name); assertEquals("+593991234567", c.phone); assertEquals(listOf("022345678"), c.morePhones)
        assertEquals("ana@correo.ec", c.email); assertEquals("Zirakusa, S.A.", c.org); assertEquals("Diseñadora", c.title)
        assertEquals("Av. Amazonas N24; Quito", c.address); assertEquals("https://ana.example", c.url); assertEquals("Línea 1\nLínea 2", c.note)
        assertEquals("a\\b,c;d", VCard.unesc(VCard.esc("a\\b,c;d")))
        // Only what is ticked goes in.
        val onlyPhone = VCard.build(listOf(Detail(Kind.PHONE, "099 111 2222")))
        assertTrue(onlyPhone.contains("FN:0991112222")); assertFalse(onlyPhone.contains("EMAIL"))
        assertEquals(VCard.Problem.NO_NAME_OR_PHONE, VCard.problem(listOf(Detail(Kind.EMAIL, "x@y.z"))))
        assertEquals(VCard.Problem.TOO_LONG, VCard.problem(listOf(Detail(Kind.NAME, "X"), Detail(Kind.NOTE, "n".repeat(3000)))))
        assertNull(VCard.problem(all))
    }

    @Test fun treeNeverCoversTheCode() {
        assertFalse(ArtLayout.tree.overlaps(ArtLayout.panel)); assertFalse(ArtLayout.caption.overlaps(ArtLayout.panel))
        listOf(25, 57, 101, 185).forEach { modules ->                                          // version 1 … 40, with 4 modules of margin
            val (box, scale) = ArtLayout.code(modules)
            assertTrue("$modules", ArtLayout.panel.contains(box)); assertEquals(box.width, box.height); assertTrue("$modules: $scale px", scale >= 4)
            assertFalse(box.overlaps(ArtLayout.tree))
        }
    }

    /** The real cards, drawn and read back as another phone would; also a contact with every detail (a dense code). */
    @Test fun drawnCardsAreReadBack() {
        val dir = File("build/screenshots").apply { mkdirs() }
        listOf(listOf(Detail(Kind.NAME, "Luis"), Detail(Kind.PHONE, "0991234567")), ana.all).forEachIndexed { i, chosen ->
            val text = VCard.build(chosen)
            val tree = ArtCard.tree(text, chosen.first().value, "Escanéame para guardar el contacto")
            assertTrue("tree $i", tree.readable)
            val px = IntArray(tree.bitmap.width * tree.bitmap.height).also { tree.bitmap.getPixels(it, 0, tree.bitmap.width, 0, 0, tree.bitmap.width, tree.bitmap.height) }
            assertEquals(text, Codes.readPixels(px, tree.bitmap.width, tree.bitmap.height, withBarcodes = false)?.text)
            assertTrue("classic $i", ArtCard.classic(text).readable)
            File(dir, "qr-contact-tree-$i.png").writeBytes(tree.png)
        }
        // A picture of something else is not taken as this code.
        assertFalse(ArtCard.verify(ArtCard.classic("otra cosa").bitmap, VCard.build(ana.all)))
    }

    @Test fun unreadableTreeFallsBackToClassic() {
        val share = ContactShare().apply { load(ana); generate() }
        val img = ImageBitmap(4, 4)
        share.drawn(Picture(img, ByteArray(1), readable = false), Picture(img, ByteArray(2), readable = true))
        assertTrue(share.fellBack); assertEquals(2, share.picture!!.png.size)
        share.changeStyle(false); assertNull(share.picture)
        share.drawn(null, Picture(img, ByteArray(2), readable = true)); assertFalse(share.fellBack)
        share.toggle(0); share.toggle(1); share.toggle(2)                                         // no name nor phone left
        assertEquals(VCard.Problem.NO_NAME_OR_PHONE, share.problem)
    }

    @Test fun screensPreviewAndConfirm() {
        val state = QrState(MapValues())
        state.share.load(ana); state.page = Page.SHARE_CONTACT
        compose.setContent { QrTheme(state.themeMode, state.accent) { QrApp(state) } }
        compose.onNodeWithText("Vista previa: esto verá quien lo escanee").performScrollTo()
        compose.onNodeWithText("Diseñadora").performScrollTo().performClick()                  // tick the job title
        assertTrue(state.share.selected.any { it.kind == Kind.TITLE })
        compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/qr-contact-choose.png")
        compose.onNodeWithText("Crear código").performScrollTo().performClick()
        assertEquals(ContactShare.Step.CODE, state.share.step)
        val img = ImageBitmap(10, 10)
        state.share.drawn(Picture(img, ByteArray(1), true), Picture(img, ByteArray(1), true))
        compose.onNodeWithText("Compartir").performClick()
        compose.onNodeWithText("¿Compartir este contacto?").assertExists()                     // asks before anything leaves
        // A scanned vCard: the save button asks first, listing what will go to the agenda.
        state.share.clear(); state.page = Page.MAIN
        state.show(com.zirakusa.qr.data.Entry(0, VCard.build(ana.all)))
        compose.onNodeWithText("Añadir contacto").performScrollTo().performClick()
        compose.onNodeWithText("¿Guardar este contacto?").assertExists()
        compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/qr-contact-save.png")
    }
}
