package com.zirakusa.qr.data

import java.net.URI

/** Something about a link worth a second look before opening it. [key] is what MCP uses. */
enum class Warning(val key: String) {
    /** Plain http: anyone on the network can read or change the page. */
    NOT_HTTPS("not_https"),
    /** A bare address (192.168…) instead of a name. */
    IP_ADDRESS("ip_address"),
    /** A name in another alphabet that can look like a known one (xn--…). */
    LOOKALIKE("lookalike"),
    /** A shortener hides where it really goes. */
    SHORTENER("shortener"),
    /** «usuario@» before the host: the real site is the part after the @. */
    USER_INFO("user_info"),
    /** Not a usual port (:8080…). */
    ODD_PORT("odd_port"),
    /** It downloads an app (.apk). */
    APP_DOWNLOAD("app_download"),
}

/**
 * Looks at a link without opening it: the real site (host) to show big, and the [Warning]s. Pure; nothing is fetched,
 * so a code can't learn that it was read.
 */
object LinkCheck {
    data class Result(val host: String, val warnings: List<Warning>)

    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "cutt.ly", "rb.gy", "tiny.cc",
        "shorturl.at", "s.id", "rebrand.ly", "buff.ly", "t.ly", "v.gd")

    fun check(url: String): Result? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https")) return null
        val host = (uri.host ?: return null).lowercase().trimEnd('.')
        val warnings = buildList {
            if (scheme == "http") add(Warning.NOT_HTTPS)
            if (host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) || host.startsWith("[")) add(Warning.IP_ADDRESS)
            if (host.split('.').any { it.startsWith("xn--") }) add(Warning.LOOKALIKE)
            if (host.removePrefix("www.") in shorteners) add(Warning.SHORTENER)
            if (!uri.rawUserInfo.isNullOrEmpty()) add(Warning.USER_INFO)
            if (uri.port != -1 && uri.port != 80 && uri.port != 443) add(Warning.ODD_PORT)
            if (uri.path.orEmpty().lowercase().endsWith(".apk")) add(Warning.APP_DOWNLOAD)
        }
        return Result(host, warnings)
    }
}
