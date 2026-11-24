package com.zirakusa.qr

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.MapValues
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrApp
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.Tab
import com.zirakusa.qr.ui.theme.QrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen, painted: Escanear (without camera, waiting for «Usar», a continuous note), Crear (Wi‑Fi, event, bar code),
 * Historial and its menu, a link with warnings, a Wi‑Fi, an event, Ajustes and Modo MCP.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val values = MapValues()
    private val state by lazy { QrState(values) }

    private fun shot(name: String) { compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/qr-$name.png") }
    private fun show() = compose.setContent { QrTheme(state.themeMode, state.accent) { QrApp(state) } }
    private fun seed() {
        var t = 1_763_700_000_000L
        val h = History(values) { t.also { t += 3_600_000 } }
        h.add("WIFI:T:WPA;S:Casa Sarango;P:clave-segura-2026;;", "QR_CODE", Source.CAMERA)
        h.add("7861234500018", "EAN_13", Source.CAMERA)
        h.add("MECARD:N:Pérez,Ana;TEL:+593991234567;EMAIL:ana@correo.ec;;", "QR_CODE", Source.IMAGE)
        h.add("https://zirakusa.com/menu", "QR_CODE", Source.CREATED)
        h.add("http://bit.ly/premio-gratis", "QR_CODE", Source.CAMERA)
        state.reload()
    }

    @Test fun scanCreateHistory() {
        seed(); show()
        compose.onNodeWithText("Permite la cámara").assertExists()
        shot("scan")
        compose.onNodeWithContentDescription("Crear").performClick()
        state.type = Type.WIFI
        state.form = state.form.copy(ssid = "Casa Sarango", password = "clave-segura-2026", security = Security.WPA)
        compose.onNodeWithText("Guardar y compartir").assertExists()
        shot("create")
        state.form = state.form.copy(password = "corta")
        compose.onNodeWithText("La contraseña WPA tiene de 8 a 63 caracteres").assertExists()
        compose.onNodeWithContentDescription("Historial").performClick()
        compose.onNodeWithText("Casa Sarango").assertExists()
        compose.onNodeWithText("Código de barras · Cámara", substring = true).assertExists()
        shot("history")
        compose.onNodeWithText("Creados").performClick()
        compose.onNodeWithText("zirakusa.com/menu").assertExists()
        compose.onNodeWithText("Casa Sarango").assertDoesNotExist()
    }

    @Test fun resultsSettingsAndMcp() {
        seed(); state.tab = Tab.HISTORY; show()
        compose.onNodeWithText("bit.ly/premio-gratis").performClick()
        compose.onNodeWithText("Sitio real").assertExists()
        compose.onNodeWithText("Es un acortador: no se ve a dónde lleva de verdad.").assertExists()
        shot("result-link")
        compose.onNodeWithText("Abrir enlace").performClick()
        compose.onNodeWithText("¿Abrir este enlace?").assertExists()
        compose.onNodeWithText("No abrir").performClick()
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.onNodeWithText("Casa Sarango").performClick()
        compose.onNodeWithText("Conectar a la red").assertExists()
        compose.onNodeWithText("••••••••").assertExists()
        shot("result-wifi")
        compose.onNodeWithText("Favorito").performClick()
        assertEquals(true, state.shown!!.favorite)
        state.closeResult(); state.page = Page.SETTINGS
        compose.onNodeWithText("Códigos de barras").assertExists()
        shot("settings")
        state.page = Page.MCP
        compose.onNodeWithText("Activar MCP").assertExists()
        shot("mcp")
    }

    @Test fun eventsBarcodesAndMenu() {
        seed(); state.tab = Tab.CREATE; show()
        compose.onNodeWithText("Evento").performClick()
        state.form = state.form.copy(title = "Cena de fin de año", start = "31/12/2026 20:00", end = "01/01/2027 01:00", place = "Casa Sarango")
        compose.onNodeWithText("Guardar y compartir").assertExists()
        shot("create-event")
        compose.onNodeWithText("Código de barras").performClick()
        compose.onNodeWithText("EAN-13").assertExists()
        state.form = state.form.copy(code = "786123450001")
        compose.onNodeWithText("7861234500017").assertExists()                                  // the check digit added
        shot("create-barcode")
        state.form = state.form.copy(code = "7861234500018")
        compose.onNodeWithText("El último número (de control) no cuadra. Escribe los demás y se calcula solo.").assertExists()
        state.form = state.form.copy(code = "7861234500017")
        compose.onNodeWithText("Guardar y compartir").performClick()
        compose.onNodeWithText("Guardar").assertExists()                                        // the bar code can be saved as a picture
        shot("result-barcode")
        state.closeResult(); state.prefill("BEGIN:VEVENT\nSUMMARY:Cita médica\nDTSTART:20261203T093000\nLOCATION:Centro de salud\nEND:VEVENT")
        state.saveDraft()
        compose.onNodeWithText("Añadir al calendario").assertExists()
        shot("result-event")
        state.closeResult(); state.tab = Tab.HISTORY
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Exportar (CSV)").assertExists()
        shot("history-menu")
        compose.onNodeWithText("Borrar todo").performClick()
        compose.onNodeWithText("¿Borrar todo el historial?").assertExists()
        shot("history-clear")
    }

    @Test fun scanConfirmContinuousAndSettings() {
        state.cameraAllowed = true; state.changeConfirm(true); show()
        state.seen(Read("https://zirakusa.com/menu", "QR_CODE"), com.zirakusa.qr.data.Source.CAMERA)
        compose.onNodeWithText("Usar código").assertExists()
        shot("scan-confirm")
        compose.onNodeWithText("Usar código").performClick()
        assertEquals(Page.RESULT, state.page)
        state.closeResult(); state.changeConfirm(false); state.changeContinuous(true)
        state.seen(Read("7861234500018", "EAN_13"), com.zirakusa.qr.data.Source.CAMERA)
        compose.onNodeWithText("Guardado: 7861234500018").assertExists()
        shot("scan-continuous")
        state.page = Page.SETTINGS
        compose.onNodeWithText("Frontal").performScrollTo()
        compose.onNodeWithText("Confirmar escaneos manualmente").assertExists()
        shot("settings-reading")
        compose.onNodeWithText("Guardar duplicados").performScrollTo()
        shot("settings-history")
        assertEquals(BarFormat.EAN_13, BarFormat.from("EAN_13"))
    }
}
