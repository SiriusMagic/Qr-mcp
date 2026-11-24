package com.zirakusa.qr.data

import androidx.core.content.edit

/** Where the settings live: the phone's preferences in the app, a map in the tests. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapValues(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValues {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Every setting of QR, typed. Unknown or broken values fall back to the defaults. */
class Settings(private val kv: KeyValues) {
    private fun flag(key: String, default: Boolean) = kv.get(key)?.let { it == "1" } ?: default
    private fun setFlag(key: String, on: Boolean) = kv.put(key, if (on) "1" else "0")

    /** A short buzz when a code is read. */
    var vibrate: Boolean get() = flag("vibrate", true); set(v) = setFlag("vibrate", v)
    /** Keep what is read and made in «Historial». */
    var keepHistory: Boolean get() = flag("keep_history", true); set(v) = setFlag("keep_history", v)
    /** Also read bar codes of products and books (EAN, UPC, Code 128…), not only QR. */
    var barcodes: Boolean get() = flag("barcodes", true); set(v) = setFlag("barcodes", v)
    /** A short beep when a code is read (off by default). */
    var sound: Boolean get() = flag("sound", false); set(v) = setFlag("sound", v)
    /** Copy the text of each code read to the clipboard (off by default). */
    var autoCopy: Boolean get() = flag("auto_copy", false); set(v) = setFlag("auto_copy", v)
    /** «Escaneo continuo»: keep reading; each code only goes to «Historial» with a short note (off by default). */
    var continuous: Boolean get() = flag("continuous", false); set(v) = setFlag("continuous", v)
    /** «Confirmar escaneos manualmente»: show what the camera read and wait for a tap (off by default). */
    var confirm: Boolean get() = flag("confirm", false); set(v) = setFlag("confirm", v)
    /** «Guardar duplicados»: the same code read again is a new line (off: the old one moves to the top). */
    var duplicates: Boolean get() = flag("duplicates", false); set(v) = setFlag("duplicates", v)
    /** The front camera instead of the back one. */
    var frontCamera: Boolean get() = flag("front_camera", false); set(v) = setFlag("front_camera", v)
    var theme: String? get() = kv.get("theme"); set(v) = kv.put("theme", v)
    var accent: String? get() = kv.get("accent"); set(v) = kv.put("accent", v)
}

/** The phone's preferences file «qr» (kept out of backups). The MCP tools and the family summary read the same. */
class SharedValues(context: android.content.Context) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences("qr", android.content.Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }
}
