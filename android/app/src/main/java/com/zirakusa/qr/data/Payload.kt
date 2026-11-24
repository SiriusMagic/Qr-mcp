package com.zirakusa.qr.data

import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What a code holds, read from its text. Unknown shapes are plain [Text]. */
sealed class Content {
    abstract val kind: Type
    data class Text(val text: String) : Content() { override val kind get() = Type.TEXT }
    data class Link(val url: String) : Content() { override val kind get() = Type.LINK }
    data class Wifi(val ssid: String, val password: String, val security: Security, val hidden: Boolean = false) : Content() {
        override val kind get() = Type.WIFI
    }
    data class Contact(val name: String, val phone: String = "", val email: String = "", val org: String = "", val note: String = "") : Content() {
        override val kind get() = Type.CONTACT
    }
    data class Phone(val number: String) : Content() { override val kind get() = Type.PHONE }
    data class Email(val to: String, val subject: String = "", val body: String = "") : Content() { override val kind get() = Type.EMAIL }
    data class Sms(val number: String, val body: String = "") : Content() { override val kind get() = Type.SMS }
    data class Place(val lat: Double, val lng: Double) : Content() { override val kind get() = Type.PLACE }
    /** A calendar event (iCalendar VEVENT). Times are the phone's local time; [allDay] events have only dates. */
    data class Event(val title: String, val start: LocalDateTime?, val end: LocalDateTime? = null, val place: String = "",
        val allDay: Boolean = false) : Content() { override val kind get() = Type.EVENT }
}

/** The kinds of code, in the order «Crear» offers them (Place is only read). [key] is what MCP uses. */
enum class Type(val key: String) {
    TEXT("text"), LINK("link"), WIFI("wifi"), CONTACT("contact"), PHONE("phone"), EMAIL("email"), SMS("sms"), EVENT("event"), PLACE("place");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } }
}

/** Wi‑Fi security as written in a Wi‑Fi code (T:WPA, T:WEP or none). */
enum class Security(val key: String) {
    WPA("WPA"), WEP("WEP"), OPEN("nopass");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key.equals(key, true) } ?: if (key.isNullOrBlank()) OPEN else WPA }
}

/**
 * Reads and writes the common code formats: links, `WIFI:`, `MECARD:` and vCard, `tel:`, `mailto:` and `MATMSG:`,
 * `smsto:`, `geo:`. Pure and strict: nothing is opened or run, only split into fields.
 */
object Payload {
    const val MAX = 2000

    fun parse(raw: String): Content {
        val text = raw.trim()
        val upper = text.uppercase()
        return when {
            upper.startsWith("WIFI:") -> wifi(text)
            upper.startsWith("MECARD:") -> mecard(text)
            upper.startsWith("BEGIN:VCARD") -> vcard(text)
            upper.startsWith("TEL:") -> Content.Phone(text.substring(4).trim())
            upper.startsWith("MAILTO:") -> mailto(text)
            upper.startsWith("MATMSG:") -> fields(text.substring(7)).let { Content.Email(it["TO"].orEmpty(), it["SUB"].orEmpty(), it["BODY"].orEmpty()) }
            upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> sms(text)
            upper.startsWith("GEO:") -> geo(text) ?: Content.Text(text)
            upper.startsWith("BEGIN:VEVENT") || upper.startsWith("BEGIN:VCALENDAR") && upper.contains("BEGIN:VEVENT") -> event(text, ZoneId.systemDefault())
            isLink(text) -> Content.Link(text)
            else -> Content.Text(raw)
        }
    }

    /** http(s) only, with a host and no spaces: what «Abrir enlace» may offer. */
    fun isLink(text: String): Boolean = !text.any { it.isWhitespace() } && runCatching {
        val uri = URI(text)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    /** The text to put in a code. */
    fun build(content: Content): String = when (content) {
        is Content.Text -> content.text
        is Content.Link -> content.url.trim().let { if (it.contains("://")) it else "https://$it" }
        is Content.Wifi -> buildString {
            append("WIFI:T:").append(content.security.key).append(";S:").append(esc(content.ssid)).append(';')
            if (content.security != Security.OPEN) append("P:").append(esc(content.password)).append(';')
            if (content.hidden) append("H:true;")
            append(';')
        }
        is Content.Contact -> buildString {
            append("MECARD:N:").append(esc(content.name)).append(';')
            if (content.phone.isNotBlank()) append("TEL:").append(esc(content.phone)).append(';')
            if (content.email.isNotBlank()) append("EMAIL:").append(esc(content.email)).append(';')
            if (content.org.isNotBlank()) append("ORG:").append(esc(content.org)).append(';')
            if (content.note.isNotBlank()) append("NOTE:").append(esc(content.note)).append(';')
            append(';')
        }
        is Content.Phone -> "tel:" + content.number.filter { it.isDigit() || it in "+*#" }
        is Content.Email -> "MATMSG:TO:${esc(content.to)};SUB:${esc(content.subject)};BODY:${esc(content.body)};;"
        is Content.Sms -> "SMSTO:" + content.number.filter { it.isDigit() || it == '+' } + ":" + content.body
        is Content.Place -> "geo:${content.lat},${content.lng}"
        is Content.Event -> buildString {
            append("BEGIN:VEVENT\n")
            if (content.title.isNotBlank()) append("SUMMARY:").append(icsEsc(content.title)).append('\n')
            val start = content.start
            if (start != null && content.allDay) {
                append("DTSTART;VALUE=DATE:").append(start.format(ICS_DATE)).append('\n')
                content.end?.let { append("DTEND;VALUE=DATE:").append(it.toLocalDate().plusDays(1).format(ICS_DATE)).append('\n') }
            } else if (start != null) {
                append("DTSTART:").append(start.format(ICS_TIME)).append('\n')
                content.end?.let { append("DTEND:").append(it.format(ICS_TIME)).append('\n') }
            }
            if (content.place.isNotBlank()) append("LOCATION:").append(icsEsc(content.place)).append('\n')
            append("END:VEVENT")
        }
    }

    /** Why a form can't be made into a code yet, or null when it can. */
    fun problem(content: Content): Problem? = when {
        content is Content.Event && content.title.isBlank() -> Problem.EMPTY
        content is Content.Event && content.start == null -> Problem.BAD_DATE
        content is Content.Event && content.end != null && content.start.let { it != null && content.end < it } -> Problem.BAD_END
        build(content).length > MAX -> Problem.TOO_LONG
        content is Content.Text && content.text.isBlank() -> Problem.EMPTY
        content is Content.Link && !isLink(build(content)) -> Problem.BAD_LINK
        content is Content.Wifi && content.ssid.isBlank() -> Problem.EMPTY
        content is Content.Wifi && content.security == Security.WPA && content.password.length !in 8..63 -> Problem.WIFI_PASSWORD
        content is Content.Contact && content.name.isBlank() -> Problem.EMPTY
        content is Content.Phone && content.number.count { it.isDigit() } < 3 -> Problem.BAD_NUMBER
        content is Content.Sms && content.number.count { it.isDigit() } < 3 -> Problem.BAD_NUMBER
        content is Content.Email && !content.to.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) -> Problem.BAD_EMAIL
        else -> null
    }

    enum class Problem { EMPTY, TOO_LONG, BAD_LINK, WIFI_PASSWORD, BAD_NUMBER, BAD_EMAIL, BAD_DATE, BAD_END }

    /** A short line for lists: the network, the name, the link… */
    fun title(content: Content): String = when (content) {
        is Content.Text -> content.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().take(80)
        is Content.Link -> content.url.removePrefix("https://").removePrefix("http://").take(80)
        is Content.Wifi -> content.ssid
        is Content.Contact -> content.name.ifBlank { content.phone }
        is Content.Phone -> content.number
        is Content.Email -> content.to
        is Content.Sms -> content.number
        is Content.Place -> "%.5f, %.5f".format(java.util.Locale.ROOT, content.lat, content.lng)
        is Content.Event -> content.title.ifBlank { content.start?.let { momentText(it, content.allDay) }.orEmpty() }.take(80)
    }

    // ---- dates of events ----

    private val ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val SHOWN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val SHOWN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val MOMENT = Regex("""(?:(\d{4})-(\d{1,2})-(\d{1,2})|(\d{1,2})/(\d{1,2})/(\d{4}))(?:[ T]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")

    /**
     * A date and time typed in «Crear» or sent by MCP: «24/11/2026 18:30», «2026-11-24 18:30», «2026-11-24T18:30» or a
     * date alone. Returns the moment and whether it had a time, or null when it isn't a real date.
     */
    fun moment(text: String): Pair<LocalDateTime, Boolean>? {
        val m = MOMENT.matchEntire(text.trim()) ?: return null
        val g = m.groupValues
        return runCatching {
            val date = if (g[1].isNotEmpty()) LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()) else LocalDate.of(g[6].toInt(), g[5].toInt(), g[4].toInt())
            val time = if (g[7].isNotEmpty()) LocalTime.of(g[7].toInt(), g[8].toInt(), g[9].ifEmpty { "0" }.toInt()) else null
            date.atTime(time ?: LocalTime.MIDNIGHT) to (time != null)
        }.getOrNull()
    }

    /** How «Crear» shows a moment: «24/11/2026 18:30», or only the date. */
    fun momentText(at: LocalDateTime, dateOnly: Boolean = false): String = at.format(if (dateOnly) SHOWN_DATE else SHOWN)

    // ---- reading ----

    private fun wifi(text: String): Content {
        val f = fields(text.substring(5))
        return Content.Wifi(f["S"].orEmpty(), f["P"].orEmpty(), Security.from(f["T"]), f["H"].equals("true", true))
    }

    private fun mecard(text: String): Content {
        val f = fields(text.substring(7))
        val name = f["N"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.reversed().joinToString(" ")
        return Content.Contact(name, f["TEL"].orEmpty(), f["EMAIL"].orEmpty(), f["ORG"].orEmpty(), f["NOTE"].orEmpty())
    }

    private fun vcard(text: String): Content {
        val lines = text.lines().map { it.trim() }
        fun value(key: String) = lines.firstOrNull { it.substringBefore(':').substringBefore(';').equals(key, true) }?.substringAfter(':')?.trim().orEmpty()
        val name = value("FN").ifBlank { value("N").split(';').filter { it.isNotBlank() }.reversed().joinToString(" ") }
        return Content.Contact(name, value("TEL"), value("EMAIL"), value("ORG"), value("NOTE"))
    }

    private fun mailto(text: String): Content {
        val rest = text.substring(7)
        val to = java.net.URLDecoder.decode(rest.substringBefore('?'), "UTF-8")
        val query = rest.substringAfter('?', "").split('&').mapNotNull { p ->
            p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to runCatching { java.net.URLDecoder.decode(it[1], "UTF-8") }.getOrDefault(it[1]) }
        }.toMap()
        return Content.Email(to, query["subject"].orEmpty(), query["body"].orEmpty())
    }

    private fun sms(text: String): Content {
        val rest = text.substringAfter(':')
        return Content.Sms(rest.substringBefore(':').trim(), rest.substringAfter(':', ""))
    }

    /** A VEVENT, alone or inside a VCALENDAR. Folded lines are joined; UTC times («Z») become [zone]'s time. */
    internal fun event(text: String, zone: ZoneId): Content.Event {
        val lines = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").lines()
        val body = lines.dropWhile { !it.trim().equals("BEGIN:VEVENT", true) }.drop(1).takeWhile { !it.trim().equals("END:VEVENT", true) }
        fun line(key: String) = body.firstOrNull { it.substringBefore(':').substringBefore(';').trim().equals(key, true) }
        fun value(key: String) = line(key)?.substringAfter(':').orEmpty().trim()
        fun time(key: String): Pair<LocalDateTime, Boolean>? {
            val v = value(key).ifEmpty { return null }
            return runCatching {
                when {
                    v.length == 8 -> LocalDate.parse(v, ICS_DATE).atStartOfDay() to true
                    v.endsWith("Z", true) -> LocalDateTime.parse(v.dropLast(1), ICS_TIME).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime() to false
                    else -> LocalDateTime.parse(v.take(15), ICS_TIME) to false
                }
            }.getOrNull()
        }
        val start = time("DTSTART")
        val allDay = start?.second == true
        // An all‑day DTEND is the day after the last one; an end that isn't after the start is dropped.
        val end = time("DTEND")?.first?.let { if (allDay) it.minusDays(1) else it }?.takeIf { start == null || it > start.first }
        return Content.Event(icsUnesc(value("SUMMARY")), start?.first, end, icsUnesc(value("LOCATION")), allDay)
    }

    private fun icsEsc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    private fun icsUnesc(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) { append(if (s[i + 1] == 'n' || s[i + 1] == 'N') '\n' else s[i + 1]); i += 2 } else { append(s[i]); i++ }
        }
    }

    private fun geo(text: String): Content? {
        val parts = text.substring(4).substringBefore('?').substringBefore(';').split(',')
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) Content.Place(lat, lng) else null
    }

    /** `K:value;K2:value;` with `\` escapes, as Wi‑Fi codes and MECARD write them. */
    internal fun fields(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val part = StringBuilder()
        var escaped = false
        fun flush() {
            val p = part.toString(); part.clear()
            val i = indexOfUnescapedColon(p)
            if (i > 0) out.putIfAbsent(p.substring(0, i).uppercase(), unescape(p.substring(i + 1)))
        }
        for (c in body) when {
            escaped -> { part.append('\\').append(c); escaped = false }
            c == '\\' -> escaped = true
            c == ';' -> flush()
            else -> part.append(c)
        }
        flush()
        return out
    }

    private fun indexOfUnescapedColon(s: String): Int {
        var i = 0
        while (i < s.length) { if (s[i] == '\\') i += 2 else if (s[i] == ':') return i else i++ }
        return -1
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) { if (s[i] == '\\' && i + 1 < s.length) { append(s[i + 1]); i += 2 } else { append(s[i]); i++ } }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
}
