package com.zirakusa.qr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Filter
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.KeyValues
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Settings
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.ui.theme.Accent
import com.zirakusa.qr.ui.theme.ThemeMode

/** The three tabs at the bottom. */
enum class Tab { SCAN, CREATE, HISTORY }

/** What covers the tabs: nothing, one code, Ajustes or Modo MCP. */
enum class Page { MAIN, RESULT, SETTINGS, MCP }

/** The fields of «Crear», kept while switching kinds so nothing typed is lost. */
data class Form(val text: String = "", val url: String = "", val ssid: String = "", val password: String = "", val security: Security = Security.WPA,
    val hidden: Boolean = false, val name: String = "", val phone: String = "", val email: String = "", val org: String = "", val subject: String = "",
    val body: String = "") {
    fun content(type: Type): Content = when (type) {
        Type.TEXT, Type.PLACE -> Content.Text(text)
        Type.LINK -> Content.Link(url)
        Type.WIFI -> Content.Wifi(ssid, password, security, hidden)
        Type.CONTACT -> Content.Contact(name, phone, email, org)
        Type.PHONE -> Content.Phone(phone)
        Type.EMAIL -> Content.Email(email, subject, body)
        Type.SMS -> Content.Sms(phone, body)
    }
}

/** The only owner of QR's state: tab, page, history, the code shown, the form of «Crear» and the settings. Screens only paint and call. */
class QrState(kv: KeyValues, now: () -> Long = System::currentTimeMillis) {
    private val settings = Settings(kv)
    private val history = History(kv, now)

    var tab by mutableStateOf(Tab.SCAN)
    var page by mutableStateOf(Page.MAIN)
    var items by mutableStateOf(history.list()); private set
    /** The code on «Resultado» (id 0 when the history is off). */
    var shown by mutableStateOf<Entry?>(null); private set
    var filter by mutableStateOf(Filter.ALL)
    var query by mutableStateOf("")
    val visible get() = History.filter(items, filter, query)

    var type by mutableStateOf(Type.TEXT)
    var form by mutableStateOf(Form())
    val draft get() = form.content(type)
    val problem get() = Payload.problem(draft)
    /** The text of the code being made, or null while the form is not ready. */
    val draftText get() = if (problem == null) Payload.build(draft) else null

    var torch by mutableStateOf(false)
    var cameraAllowed by mutableStateOf(false)

    var vibrate by mutableStateOf(settings.vibrate); private set
    var keepHistory by mutableStateOf(settings.keepHistory); private set
    var barcodes by mutableStateOf(settings.barcodes); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.AZUL); private set

    fun reload() { items = history.list(); shown = shown?.let { s -> if (s.id == 0L) s else history.get(s.id) } }

    /** A code was read (camera or picture): kept (if the history is on) and shown. The camera waits while it is. */
    fun scanned(read: Read, source: Source): Entry {
        val e = if (keepHistory) history.add(read.text, read.format, source) else Entry(0, read.text, read.format, source, System.currentTimeMillis())
        reload(); show(e)
        return e
    }

    /** «Guardar» in «Crear»: keeps the code made and shows it. */
    fun saveDraft(): Entry? {
        val text = draftText ?: return null
        val e = if (keepHistory) history.add(text, Entry.QR, Source.CREATED) else Entry(0, text, Entry.QR, Source.CREATED, System.currentTimeMillis())
        reload(); show(e)
        return e
    }

    fun show(e: Entry) { shown = e; page = Page.RESULT }
    fun closeResult() { shown = null; page = Page.MAIN }

    fun toggleFavorite(e: Entry) { if (e.id == 0L) return; history.update(e.copy(favorite = !e.favorite)); reload() }
    fun rename(e: Entry, name: String) { if (e.id == 0L) return; history.update(e.copy(name = name.trim().take(Entry.NAME_MAX))); reload() }
    fun delete(e: Entry) { history.delete(e.id); reload(); if (shown?.id == e.id) closeResult() }
    /** «Borrar historial» (favourites stay). */
    fun clearHistory(): Int = history.clear().also { reload() }

    /** «Editar» on a code: its fields go to «Crear». */
    fun editAsNew(e: Entry) {
        when (val c = e.content) {
            is Content.Text -> { type = Type.TEXT; form = form.copy(text = c.text) }
            is Content.Link -> { type = Type.LINK; form = form.copy(url = c.url) }
            is Content.Wifi -> { type = Type.WIFI; form = form.copy(ssid = c.ssid, password = c.password, security = c.security, hidden = c.hidden) }
            is Content.Contact -> { type = Type.CONTACT; form = form.copy(name = c.name, phone = c.phone, email = c.email, org = c.org) }
            is Content.Phone -> { type = Type.PHONE; form = form.copy(phone = c.number) }
            is Content.Email -> { type = Type.EMAIL; form = form.copy(email = c.to, subject = c.subject, body = c.body) }
            is Content.Sms -> { type = Type.SMS; form = form.copy(phone = c.number, body = c.body) }
            is Content.Place -> { type = Type.TEXT; form = form.copy(text = e.text) }
        }
        tab = Tab.CREATE; closeResult()
    }

    fun changeVibrate(on: Boolean) { vibrate = on; settings.vibrate = on }
    fun changeKeepHistory(on: Boolean) { keepHistory = on; settings.keepHistory = on }
    fun changeBarcodes(on: Boolean) { barcodes = on; settings.barcodes = on }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(next: Accent) { accent = next; settings.accent = next.name }
}
