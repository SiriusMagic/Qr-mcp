package com.zirakusa.qr

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Security
import java.io.File

/**
 * What a code's main button does, always after the user's tap: open the link, dial, write, add the contact, show
 * the place or join the Wi‑Fi (Android asks before saving it). Also copy, share and save the picture.
 */
class Openers(private val context: Context) {
    fun open(e: Entry) {
        val intent = when (val c = e.content) {
            is Content.Link -> Intent(Intent.ACTION_VIEW, c.url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
            is Content.Phone -> Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(c.number)}".toUri())
            is Content.Email -> Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).putExtra(Intent.EXTRA_EMAIL, arrayOf(c.to))
                .putExtra(Intent.EXTRA_SUBJECT, c.subject).putExtra(Intent.EXTRA_TEXT, c.body)
            is Content.Sms -> Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(c.number)}".toUri()).putExtra("sms_body", c.body)
            is Content.Place -> "geo:${c.lat},${c.lng}?q=${c.lat},${c.lng}".toUri().let { Intent(Intent.ACTION_VIEW, it) }
            is Content.Contact -> contact(c)
            is Content.Wifi -> wifi(c)
            is Content.Event -> event(c) ?: return
            is Content.Text -> return
        }
        start(intent)
    }

    /** Android 11+: the system's own «Guardar esta red» sheet. Before: the password is copied and Wi‑Fi settings open. */
    private fun wifi(c: Content.Wifi): Intent {
        if (Build.VERSION.SDK_INT >= 30 && c.security != Security.WEP) {
            val s = WifiNetworkSuggestion.Builder().setSsid(c.ssid).setIsHiddenSsid(c.hidden)
                .apply { if (c.security == Security.WPA) setWpa2Passphrase(c.password) }.build()
            return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(s))
        }
        if (c.password.isNotEmpty()) { copy(c.password, R.string.password_copied) }
        return Intent(Settings.ACTION_WIFI_SETTINGS)
    }

    /** «Añadir al calendario»: the calendar app's own new‑event page, filled in; the user saves it there. */
    private fun event(c: Content.Event): Intent? {
        val zone = java.time.ZoneId.systemDefault()
        val start = c.start ?: return null
        return Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, c.title).putExtra(CalendarContract.Events.EVENT_LOCATION, c.place)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, c.allDay)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
            .apply { c.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, (if (c.allDay) it.plusDays(1) else it).atZone(zone).toInstant().toEpochMilli()) } }
    }

    fun copy(text: String, message: Int = R.string.copied) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QR", text))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * «Añadir contacto» (after the person confirmed it in the app): Android's own «new contact» form, filled, where they
     * still choose to save. Nothing is written to the agenda by this app.
     */
    private fun contact(c: Content.Contact): Intent {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, c.name).putExtra(ContactsContract.Intents.Insert.PHONE, c.phone)
            .putExtra(ContactsContract.Intents.Insert.EMAIL, c.email).putExtra(ContactsContract.Intents.Insert.COMPANY, c.org)
            .putExtra(ContactsContract.Intents.Insert.NOTES, c.note).putExtra(ContactsContract.Intents.Insert.JOB_TITLE, c.title)
            .putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
        c.morePhones.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
        c.morePhones.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, it) }
        c.moreEmails.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
        c.moreEmails.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.TERTIARY_EMAIL, it) }
        if (c.url.isNotBlank()) i.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Website.URL, c.url)
        }))
        return i
    }

    /** QR codes and the bar codes «Crear» makes go as a picture (and their text); other bar codes as text. */
    fun share(e: Entry) = sharePng(Codes.picture(e.text, e.format), e.text)

    /**
     * Android's «Compartir» sheet with [png] (from the app's cache, read only by the app the person picks) and [text].
     * Nothing leaves until they choose an app there.
     */
    fun sharePng(png: ByteArray?, text: String?) {
        val send = Intent(Intent.ACTION_SEND)
        text?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
        if (png != null) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "qr.png").apply { writeBytes(png) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("QR", uri)
        } else send.setType("text/plain")
        start(Intent.createChooser(send, context.getString(R.string.share)))
    }

    /** «Guardar imagen»: into Pictures/QR (Android 10+; before, it is shared instead). */
    fun saveImage(e: Entry) = savePng(Codes.picture(e.text, e.format), e.title, e.text)

    /** [png] into Pictures/QR named after [title] (Android 10+; before, it is shared instead). */
    fun savePng(png: ByteArray?, title: String, text: String?) {
        if (Build.VERSION.SDK_INT < 29) return sharePng(png, text)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "QR-${title.filter { it.isLetterOrDigit() }.take(30).ifEmpty { "codigo" }}-${System.currentTimeMillis() / 1000}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QR")
        }
        val resolver = context.contentResolver
        val ok = runCatching {
            val bytes = png ?: error("no picture")
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        }.isSuccess
        Toast.makeText(context, if (ok) R.string.saved_image else R.string.not_saved, Toast.LENGTH_SHORT).show()
    }

    /** «Exportar»: the CSV into the file the user created in Android's file picker. */
    fun writeText(uri: android.net.Uri, text: String): Boolean =
        runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream") }.isSuccess

    /** «Importar»: the file the user picked, as text (at most [max] characters); null when it can't be read. */
    fun readText(uri: android.net.Uri, max: Int): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = CharArray(8192); val out = StringBuilder()
            input.bufferedReader(Charsets.UTF_8).use { r -> while (out.length < max) { val n = r.read(buf); if (n < 0) break; out.append(buf, 0, n) } }
            out.toString()
        }
    }.getOrNull()

    /** The text on the clipboard, or null. */
    fun paste(): String? = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

    private fun start(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(context, R.string.no_app, Toast.LENGTH_SHORT).show() }
    }

    private object Uri { fun encode(s: String): String = android.net.Uri.encode(s, "+") }
}
