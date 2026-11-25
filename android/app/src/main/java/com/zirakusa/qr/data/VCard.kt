package com.zirakusa.qr.data

/** One piece of a contact the person can include or leave out: its kind and its value («0991234567», «ana@correo.ec»…). */
data class Detail(val kind: Kind, val value: String) {
    enum class Kind { NAME, PHONE, EMAIL, ORG, TITLE, ADDRESS, URL, NOTE }
}

/**
 * A contact as read from the phone's agenda (only after the person picked it), and the vCard it becomes. Pure, with
 * tests: nothing here touches Android, the internet or the agenda itself.
 */
data class SharedContact(val name: String, val details: List<Detail>) {
    /** Everything that can go in the code; the name first and always on by default. */
    val all: List<Detail> get() = listOf(Detail(Detail.Kind.NAME, name)).filter { it.value.isNotBlank() } +
        details.filter { it.kind != Detail.Kind.NAME && it.value.isNotBlank() }.distinct()

    /** What is ticked when the screen opens: the name, phones and emails (the person adds the rest by hand). */
    fun defaults(): Set<Int> = all.indices.filter { all[it].kind in setOf(Detail.Kind.NAME, Detail.Kind.PHONE, Detail.Kind.EMAIL) }.toSet()
}

object VCard {
    /** Above this the code gets dense and harder to read from another phone: the screen suggests leaving something out. */
    const val COMFORTABLE_BYTES = 700
    /** What a QR code (medium correction) can hold at most; above it nothing is made. */
    const val MAX_BYTES = 2300

    enum class Problem { NO_NAME_OR_PHONE, TOO_LONG }

    /** Why [chosen] can't become a code yet: it needs a name or a phone, and must fit in one QR code. */
    fun problem(chosen: List<Detail>): Problem? = when {
        chosen.none { (it.kind == Detail.Kind.NAME || it.kind == Detail.Kind.PHONE) && it.value.isNotBlank() } -> Problem.NO_NAME_OR_PHONE
        build(chosen).toByteArray(Charsets.UTF_8).size > MAX_BYTES -> Problem.TOO_LONG
        else -> null
    }

    /**
     * vCard 3.0 (what phones' cameras and contact apps read): FN and N, then each chosen detail, with the escapes the
     * format needs (\\ , ; and line breaks) and CRLF line ends. A contact without a name is named by its first phone.
     */
    fun build(chosen: List<Detail>): String {
        fun pick(kind: Detail.Kind) = chosen.filter { it.kind == kind && it.value.isNotBlank() }.map { it.value.trim() }
        val name = pick(Detail.Kind.NAME).firstOrNull() ?: pick(Detail.Kind.PHONE).firstOrNull()?.let(Phones::clean).orEmpty()
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0", "N:${structured(name)}", "FN:${esc(name)}")
        pick(Detail.Kind.PHONE).forEachIndexed { i, p -> lines += "TEL;TYPE=${if (i == 0) "CELL" else "VOICE"}:${esc(Phones.clean(p))}" }
        pick(Detail.Kind.EMAIL).forEach { lines += "EMAIL;TYPE=INTERNET:${esc(it)}" }
        pick(Detail.Kind.ORG).firstOrNull()?.let { lines += "ORG:${esc(it)}" }
        pick(Detail.Kind.TITLE).firstOrNull()?.let { lines += "TITLE:${esc(it)}" }
        pick(Detail.Kind.ADDRESS).forEach { lines += "ADR;TYPE=HOME:;;${esc(it)};;;;" }
        pick(Detail.Kind.URL).forEach { lines += "URL:${esc(it)}" }
        pick(Detail.Kind.NOTE).firstOrNull()?.let { lines += "NOTE:${esc(it)}" }
        lines += "END:VCARD"
        return lines.joinToString("\r\n")
    }

    /** «Ana María Pérez» → `Pérez;Ana María;;;` (last word as family name), as contact apps file it. */
    private fun structured(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return "${esc(name.trim())};;;;"
        return "${esc(words.last())};${esc(words.dropLast(1).joinToString(" "))};;;"
    }

    fun esc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\r\n", "\\n").replace("\n", "\\n")

    /** The reverse of [esc], for reading a scanned vCard. */
    fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) { 'n', 'N' -> append('\n'); else -> append(n) }
                i += 2
            } else { append(c); i++ }
        }
    }
}

/** Phone numbers as a code should carry them: digits and a leading +, nothing else. */
object Phones {
    fun clean(number: String): String = number.trim().let { t -> (if (t.startsWith("+")) "+" else "") + t.filter { it.isDigit() || it in "*#" } }
}
