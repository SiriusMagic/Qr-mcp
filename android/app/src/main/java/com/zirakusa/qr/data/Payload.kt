package com.zirakusa.qr.data

import java.net.URI

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
}

/** The kinds of code, in the order «Crear» offers them (Place is only read). [key] is what MCP uses. */
enum class Type(val key: String) {
    TEXT("text"), LINK("link"), WIFI("wifi"), CONTACT("contact"), PHONE("phone"), EMAIL("email"), SMS("sms"), PLACE("place");
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
    }

    /** Why a form can't be made into a code yet, or null when it can. */
    fun problem(content: Content): Problem? = when {
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

    enum class Problem { EMPTY, TOO_LONG, BAD_LINK, WIFI_PASSWORD, BAD_NUMBER, BAD_EMAIL }

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
    }

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
