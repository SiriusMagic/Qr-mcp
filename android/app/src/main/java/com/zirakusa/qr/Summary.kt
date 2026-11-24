package com.zirakusa.qr

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.SharedValues
import com.zirakusa.qr.data.Type

/** The lines of «Hoy» for the family summary: the codes of the last [DAYS] days, newest first («Wi‑Fi · Casa»). Pure. */
object SummaryLines {
    const val MAX = 5
    const val DAYS = 7
    fun lines(items: List<Entry>, now: Long, kind: (Entry) -> String): List<String> =
        items.filter { now - it.at in 0..DAYS * 86_400_000L }.sortedByDescending { it.at }
            .map { "${kind(it)} · ${it.title.take(60)}" }.take(MAX)
}

/**
 * The family summary (see Inicio's FAMILIA.md): what Inicio shows in its «Hoy» card for QR. Only apps signed by the
 * same developer may ask (Android's signature permission and the caller's signature checked here). Wi‑Fi passwords
 * never leave: only the network's name.
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "summary") return null
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(context.packageName, caller) != PackageManager.SIGNATURE_MATCH) return null
        val lines = SummaryLines.lines(History(SharedValues(context)).list(), System.currentTimeMillis()) {
            context.getString(if (!it.isQr && it.content.kind == Type.TEXT) R.string.barcode else typeName(it.content.kind)) }
        return Bundle().apply {
            putString("title", context.getString(R.string.summary_title))
            putStringArrayList("lines", ArrayList(lines))
            putString("empty", context.getString(R.string.summary_empty))
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** The name of each kind of code. */
fun typeName(t: Type) = when (t) {
    Type.TEXT -> R.string.type_text; Type.LINK -> R.string.type_link; Type.WIFI -> R.string.type_wifi; Type.CONTACT -> R.string.type_contact
    Type.PHONE -> R.string.type_phone; Type.EMAIL -> R.string.type_email; Type.SMS -> R.string.type_sms; Type.EVENT -> R.string.type_event; Type.PLACE -> R.string.type_place
}
