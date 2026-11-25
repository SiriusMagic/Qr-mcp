package com.zirakusa.qr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Barcodes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Filter
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.KeyValues
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.ScanGate
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Settings
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.ui.theme.Accent
import com.zirakusa.qr.ui.theme.ThemeMode

/** The tabs at the bottom (Ajustes, the fourth button, opens its page). */
enum class Tab { SCAN, CREATE, HISTORY }

/** What covers the tabs: nothing, one code, Ajustes or Modo MCP. */
enum class Page { MAIN, RESULT, SETTINGS, SHARE_CONTACT }

/** The fields of «Crear», kept while switching kinds so nothing typed is lost. */
data class Form(val text: String = "", val url: String = "", val ssid: String = "", val password: String = "", val security: Security = Security.WPA,
    val hidden: Boolean = false, val name: String = "", val phone: String = "", val email: String = "", val org: String = "", val subject: String = "",
    val body: String = "", val title: String = "", val start: String = "", val end: String = "", val place: String = "",
    val code: String = "") {
    fun content(type: Type): Content = when (type) {
        Type.TEXT, Type.PLACE -> Content.Text(text)
        Type.LINK -> Content.Link(url)
        Type.WIFI -> Content.Wifi(ssid, password, security, hidden)
        Type.CONTACT -> Content.Contact(name, phone, email, org)
        Type.PHONE -> Content.Phone(phone)
        Type.EMAIL -> Content.Email(email, subject, body)
        Type.SMS -> Content.Sms(phone, body)
        Type.EVENT -> {
            val from = Payload.moment(start)
            // An end that isn't a date reads as «before the start», so the form says to look at it.
            val to = if (end.isBlank()) null else Payload.moment(end)?.first ?: java.time.LocalDateTime.MIN
            Content.Event(title, from?.first, to, place, allDay = from != null && !from.second)
        }
    }
}

/** A code the camera read, waiting for «Usar» («Confirmar escaneos manualmente»). */
data class Pending(val read: Read, val source: Source)

/** What happened with a code the camera or a picture gave. */
sealed interface Step {
    /** The same code still in view («Escaneo continuo»). */
    data object Ignored : Step
    /** Shown, waiting for the user's tap. */
    data object Waiting : Step
    /** Kept (if the history is on) and shown, or only noted in «Escaneo continuo». */
    data class Done(val entry: Entry) : Step
}

/** The only owner of QR's state: tab, page, history, the code shown, the form of «Crear» and the settings. Screens only paint and call. */
class QrState(kv: KeyValues, private val now: () -> Long = System::currentTimeMillis) {
    private val settings = Settings(kv)
    private val history = History(kv, now)

    var tab by mutableStateOf(Tab.SCAN)
    var page by mutableStateOf(Page.MAIN)
    /** «Compartir un contacto» (from «Crear»): only in memory, never in the history. */
    val share = ContactShare()
    var items by mutableStateOf(history.list()); private set
    /** The code on «Resultado» (id 0 when the history is off). */
    var shown by mutableStateOf<Entry?>(null); private set
    var filter by mutableStateOf(Filter.ALL)
    var query by mutableStateOf("")
    val visible get() = History.filter(items, filter, query)

    var type by mutableStateOf(Type.TEXT)
    var form by mutableStateOf(Form())
    /** «Código de barras» in «Crear»: the format being made, or null for QR. */
    var barFormat by mutableStateOf<BarFormat?>(null)
    val draft get() = form.content(type)
    val problem get() = Payload.problem(draft)
    val barProblem get() = barFormat?.let { Barcodes.problem(it, form.code) }
    /** The text of the code being made, or null while the form is not ready. */
    val draftText get() = barFormat?.let { f -> if (barProblem == null) Barcodes.normalize(f, form.code) else null }
        ?: if (barFormat == null && problem == null) Payload.build(draft) else null

    var torch by mutableStateOf(false)
    var cameraAllowed by mutableStateOf(false)
    var pending by mutableStateOf<Pending?>(null); private set
    /** The last code noted by «Escaneo continuo» (a short note over the camera). */
    var quick by mutableStateOf<Entry?>(null)
    private val gate = ScanGate()

    var vibrate by mutableStateOf(settings.vibrate); private set
    var keepHistory by mutableStateOf(settings.keepHistory); private set
    var barcodes by mutableStateOf(settings.barcodes); private set
    var sound by mutableStateOf(settings.sound); private set
    var autoCopy by mutableStateOf(settings.autoCopy); private set
    var continuous by mutableStateOf(settings.continuous); private set
    var confirm by mutableStateOf(settings.confirm); private set
    var duplicates by mutableStateOf(settings.duplicates); private set
    var frontCamera by mutableStateOf(settings.frontCamera); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.AZUL); private set

    fun reload() { items = history.list(); shown = shown?.let { s -> if (s.id == 0L) s else history.get(s.id) } }

    /**
     * The camera or a picture gave a code. With «Escaneo continuo» the same code in view counts once; with «Confirmar
     * escaneos manualmente» a camera code waits for [confirmPending]. Pictures are a choice already: never asked.
     */
    fun seen(read: Read, source: Source): Step {
        if (source == Source.CAMERA && continuous && !gate.accept(read.text, now())) return Step.Ignored
        if (source == Source.CAMERA && confirm) { pending = Pending(read, source); return Step.Waiting }
        return Step.Done(scanned(read, source))
    }

    /** «Usar» on the code waiting. */
    fun confirmPending(): Entry? = pending?.let { p -> pending = null; scanned(p.read, p.source) }
    /** «Descartar»: the camera reads again (the same code too). */
    fun dropPending() { pending = null; gate.reset() }

    /** A code was read: kept (if the history is on) and shown; in «Escaneo continuo» only noted. The camera waits while shown. */
    fun scanned(read: Read, source: Source): Entry {
        val e = keep(read.text, read.format, source)
        reload()
        if (continuous && source == Source.CAMERA) quick = e else show(e)
        return e
    }

    private fun keep(text: String, format: String, source: Source) =
        if (keepHistory) history.add(text, format, source, duplicates) else Entry(0, text, format, source, now())

    /** «Guardar» in «Crear»: keeps the code made (QR or bar code) and shows it. */
    fun saveDraft(): Entry? {
        val text = draftText ?: return null
        val e = keep(text, barFormat?.format ?: Entry.QR, Source.CREATED)
        reload(); show(e)
        return e
    }

    /** «Importar»: returns how many codes were added. */
    fun import(rows: List<Entry>): Int = history.import(rows).also { reload() }

    fun show(e: Entry) { shown = e; page = Page.RESULT }
    fun closeResult() { shown = null; page = Page.MAIN }

    fun toggleFavorite(e: Entry) { if (e.id == 0L) return; history.update(e.copy(favorite = !e.favorite)); reload() }
    fun rename(e: Entry, name: String) { if (e.id == 0L) return; history.update(e.copy(name = name.trim().take(Entry.NAME_MAX))); reload() }
    fun delete(e: Entry) { history.delete(e.id); reload(); if (shown?.id == e.id) closeResult() }
    /** «Borrar historial» (favourites stay), or «Borrar todo» with [all]. */
    fun clearHistory(all: Boolean = false): Int = history.clear(all).also { reload() }

    /** «Editar» on a code: its fields go to «Crear». */
    fun editAsNew(e: Entry) {
        val bar = BarFormat.from(e.format)
        if (bar != null) { barFormat = bar; form = form.copy(code = e.text); tab = Tab.CREATE; closeResult(); return }
        prefill(e.text)
    }

    /** Text from another app («Compartir» › QR) or «Desde el portapapeles»: «Crear» with the fields it fits. */
    fun prefill(raw: String) {
        val text = raw.take(Payload.MAX)
        barFormat = null
        when (val c = Payload.parse(text)) {
            is Content.Text -> { type = Type.TEXT; form = form.copy(text = c.text) }
            is Content.Link -> { type = Type.LINK; form = form.copy(url = c.url) }
            is Content.Wifi -> { type = Type.WIFI; form = form.copy(ssid = c.ssid, password = c.password, security = c.security, hidden = c.hidden) }
            is Content.Contact -> { type = Type.CONTACT; form = form.copy(name = c.name, phone = c.phone, email = c.email, org = c.org) }
            is Content.Phone -> { type = Type.PHONE; form = form.copy(phone = c.number) }
            is Content.Email -> { type = Type.EMAIL; form = form.copy(email = c.to, subject = c.subject, body = c.body) }
            is Content.Sms -> { type = Type.SMS; form = form.copy(phone = c.number, body = c.body) }
            is Content.Place -> { type = Type.TEXT; form = form.copy(text = text) }
            is Content.Event -> { type = Type.EVENT; form = form.copy(title = c.title, place = c.place,
                start = c.start?.let { Payload.momentText(it, c.allDay) }.orEmpty(), end = c.end?.let { Payload.momentText(it, c.allDay) }.orEmpty()) }
        }
        tab = Tab.CREATE; closeResult()
    }

    fun changeVibrate(on: Boolean) { vibrate = on; settings.vibrate = on }
    fun changeKeepHistory(on: Boolean) { keepHistory = on; settings.keepHistory = on }
    fun changeBarcodes(on: Boolean) { barcodes = on; settings.barcodes = on }
    fun changeSound(on: Boolean) { sound = on; settings.sound = on }
    fun changeAutoCopy(on: Boolean) { autoCopy = on; settings.autoCopy = on }
    fun changeContinuous(on: Boolean) { continuous = on; settings.continuous = on; gate.reset(); if (!on) quick = null }
    fun changeConfirm(on: Boolean) { confirm = on; settings.confirm = on; if (!on) pending = null }
    fun changeDuplicates(on: Boolean) { duplicates = on; settings.duplicates = on }
    fun changeFrontCamera(on: Boolean) { frontCamera = on; settings.frontCamera = on; torch = false }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(next: Accent) { accent = next; settings.accent = next.name }
}
