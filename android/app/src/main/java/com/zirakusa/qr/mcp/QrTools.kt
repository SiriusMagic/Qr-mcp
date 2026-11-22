package com.zirakusa.qr.mcp

import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Filter
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.LinkCheck
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Type
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** What the tools read and change; the app gives the real history, tests a fake. */
interface QrData {
    fun history(): List<Entry>
    /** Saves a code made by the AI (only when «Guardar historial» is on); null when the history is off. */
    fun created(text: String): Entry?
    fun delete(id: Long): Boolean
    fun zone(): ZoneId = ZoneId.systemDefault()
}

/** An expected failure the AI can read (structuredContent.error.code), never a crash. */
class ToolFailure(val code: String, message: String) : Exception(message)

/** A tool's answer: the JSON and, when it made or shows a code, its picture (PNG). */
class ToolOutput(val json: JSONObject, val png: ByteArray? = null)

/**
 * QR's MCP tools: read the history, look inside a code's text (with link warnings), make QR codes and, with a
 * read-and-write token, keep or delete them. Nothing is opened, fetched or run: a link is only described.
 */
object QrTools {
    class Tool(val name: String, val description: String, val write: Boolean, val schema: JSONObject)

    private fun schema(required: List<String>, vararg props: Pair<String, JSONObject>) = JSONObject()
        .put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required)).put("additionalProperties", false)
    private fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
    private fun choice(values: List<String>, description: String) = JSONObject().put("type", "string").put("enum", JSONArray(values)).put("description", description)

    private val createProps = arrayOf(
        "type" to choice(Type.entries.filter { it != Type.PLACE }.map { it.key }, "What the code holds."),
        "text" to prop("string", "type text: the text."), "url" to prop("string", "type link: the address (https:// is added if missing)."),
        "ssid" to prop("string", "type wifi: network name."), "password" to prop("string", "type wifi: password (WPA: 8–63 characters)."),
        "security" to choice(Security.entries.map { it.key }, "type wifi: default WPA."), "hidden" to prop("boolean", "type wifi: hidden network."),
        "name" to prop("string", "type contact: full name."), "phone" to prop("string", "type contact or phone: number."),
        "email" to prop("string", "type contact or email: address."), "org" to prop("string", "type contact: company."),
        "note" to prop("string", "type contact: note."), "subject" to prop("string", "type email: subject."),
        "body" to prop("string", "type email or sms: message."),
    )

    val tools = listOf(
        Tool("qr_history", "Codes read and made on the phone, newest first.", false,
            schema(emptyList(), "filter" to choice(listOf("all", "scanned", "created", "favorites"), "Default all."),
                "query" to prop("string", "Words to look for (accents ignored)."), "limit" to prop("integer", "1–100, default 20."))),
        Tool("qr_item", "One code of the history: its full text, what it holds (fields) and, for links, the real site and warnings. " +
            "«image» adds the QR picture.", false,
            schema(listOf("id"), "id" to prop("integer", "From qr_history."), "image" to prop("boolean", "Add the picture (QR codes only)."))),
        Tool("qr_parse", "Looks inside the text of a code without saving it: kind, fields and link warnings. Nothing is opened.", false,
            schema(listOf("text"), "text" to prop("string", "Up to ${Payload.MAX} characters."))),
        Tool("qr_make", "Makes a QR code picture (PNG) for a text, link, Wi‑Fi, contact, phone, email or SMS, without saving it.", false,
            schema(listOf("type"), *createProps)),
        Tool("qr_create", "Like qr_make, and keeps the code in the phone's history (when the user keeps a history).", true,
            schema(listOf("type"), *createProps)),
        Tool("qr_delete", "Deletes one code from the history.", true, schema(listOf("id"), "id" to prop("integer", "From qr_history."))),
    )

    fun available(access: Access) = tools.filter { !it.write || access == Access.WRITE }

    fun call(name: String, args: JSONObject, access: Access, data: QrData): ToolOutput {
        val tool = tools.firstOrNull { it.name == name } ?: throw ToolFailure("UNKNOWN_TOOL", "No tool named $name.")
        if (tool.write && access != Access.WRITE) throw ToolFailure("READ_ONLY", "This token can only read. Ask for a read-and-write token.")
        val known = tool.schema.getJSONObject("properties").keys().asSequence().toSet()
        args.keys().forEach { if (it !in known) throw ToolFailure("BAD_ARGUMENT", "Unknown argument «$it».") }
        return when (name) {
            "qr_history" -> history(args, data)
            "qr_item" -> item(args, data)
            "qr_parse" -> ToolOutput(describe(Payload.parse(text(args, "text", Payload.MAX, required = true))))
            "qr_make" -> make(args, null)
            "qr_create" -> make(args, data)
            "qr_delete" -> id(args).let { if (!data.delete(it)) throw notFound(it); ToolOutput(JSONObject().put("deleted", it)) }
            else -> throw ToolFailure("UNKNOWN_TOOL", "No tool named $name.")
        }
    }

    private fun history(args: JSONObject, data: QrData): ToolOutput {
        val filter = when (args.optString("filter", "all")) {
            "all" -> Filter.ALL; "scanned" -> Filter.SCANNED; "created" -> Filter.CREATED; "favorites" -> Filter.FAVORITES
            else -> throw ToolFailure("BAD_ARGUMENT", "«filter» is all, scanned, created or favorites.")
        }
        val limit = if (args.has("limit")) int(args, "limit").takeIf { it in 1..100 } ?: throw ToolFailure("BAD_ARGUMENT", "«limit» is 1–100.") else 20
        val found = History.filter(data.history(), filter, text(args, "query", 200))
        return ToolOutput(JSONObject().put("total", found.size).put("items", JSONArray(found.take(limit).map { summary(it, data.zone()) })))
    }

    private fun item(args: JSONObject, data: QrData): ToolOutput {
        val id = id(args)
        val e = data.history().firstOrNull { it.id == id } ?: throw notFound(id)
        val image = args.has("image") && (args.opt("image") as? Boolean ?: throw ToolFailure("BAD_ARGUMENT", "«image» is true or false."))
        val json = summary(e, data.zone()).put("text", e.text).put("content", describe(e.content))
        return ToolOutput(json, if (image && e.isQr) Codes.png(Codes.encode(e.text)) else null)
    }

    private fun make(args: JSONObject, data: QrData?): ToolOutput {
        val type = Type.from(args.optString("type")).takeIf { it != null && it != Type.PLACE }
            ?: throw ToolFailure("BAD_ARGUMENT", "«type» is text, link, wifi, contact, phone, email or sms.")
        fun s(key: String, max: Int = 500) = text(args, key, max)
        val content = when (type) {
            Type.TEXT -> Content.Text(s("text", Payload.MAX))
            Type.LINK -> Content.Link(s("url", Payload.MAX))
            Type.WIFI -> Content.Wifi(s("ssid", 32), s("password", 63),
                if (args.has("security")) Security.entries.firstOrNull { it.key == args.optString("security") } ?: throw ToolFailure("BAD_ARGUMENT", "«security» is WPA, WEP or nopass.") else Security.WPA,
                args.has("hidden") && (args.opt("hidden") as? Boolean ?: throw ToolFailure("BAD_ARGUMENT", "«hidden» is true or false.")))
            Type.CONTACT -> Content.Contact(s("name", 100), s("phone", 40), s("email", 100), s("org", 100), s("note"))
            Type.PHONE -> Content.Phone(s("phone", 40))
            Type.EMAIL -> Content.Email(s("email", 100), s("subject", 200), s("body", 1000))
            Type.SMS -> Content.Sms(s("phone", 40), s("body", 1000))
            Type.PLACE -> throw ToolFailure("BAD_ARGUMENT", "«type» can't be place.")
        }
        Payload.problem(content)?.let { throw ToolFailure("BAD_ARGUMENT", problemText(it)) }
        val text = Payload.build(content)
        val json = JSONObject().put("text", text).put("content", describe(Payload.parse(text)))
        if (data != null) {
            val saved = data.created(text)
            json.put("saved", saved != null)
            if (saved != null) json.put("id", saved.id) else json.put("note", "The user doesn't keep a history; the code was not saved.")
        }
        return ToolOutput(json, Codes.png(Codes.encode(text)))
    }

    private fun problemText(p: Payload.Problem) = when (p) {
        Payload.Problem.EMPTY -> "Nothing to put in the code: fill the main field (text, url, ssid or name)."
        Payload.Problem.TOO_LONG -> "Too long for a code that phones read well (${Payload.MAX} characters at most)."
        Payload.Problem.BAD_LINK -> "«url» is not an http(s) address."
        Payload.Problem.WIFI_PASSWORD -> "A WPA password has 8–63 characters."
        Payload.Problem.BAD_NUMBER -> "«phone» needs at least 3 digits."
        Payload.Problem.BAD_EMAIL -> "«email» is not an address."
    }

    /** What a code holds, as fields; links also carry their real site and warnings. */
    fun describe(c: Content): JSONObject {
        val o = JSONObject().put("type", c.kind.key)
        when (c) {
            is Content.Text -> o.put("text", c.text)
            is Content.Link -> { o.put("url", c.url); LinkCheck.check(c.url)?.let { r -> o.put("site", r.host).put("warnings", JSONArray(r.warnings.map { it.key })) } }
            is Content.Wifi -> o.put("ssid", c.ssid).put("security", c.security.key).put("hidden", c.hidden).put("has_password", c.password.isNotEmpty())
            is Content.Contact -> o.put("name", c.name).put("phone", c.phone).put("email", c.email).put("org", c.org).put("note", c.note)
            is Content.Phone -> o.put("phone", c.number)
            is Content.Email -> o.put("email", c.to).put("subject", c.subject).put("body", c.body)
            is Content.Sms -> o.put("phone", c.number).put("body", c.body)
            is Content.Place -> o.put("lat", c.lat).put("lng", c.lng)
        }
        return o
    }

    private fun summary(e: Entry, zone: ZoneId) = JSONObject().put("id", e.id).put("title", e.title).put("type", e.content.kind.key)
        .put("format", e.format).put("source", e.source.key).put("favorite", e.favorite)
        .put("at", Instant.ofEpochMilli(e.at).atZone(zone).toLocalDateTime().withNano(0).toString())

    private fun notFound(id: Long) = ToolFailure("NOT_FOUND", "No code with id $id in the history.")
    private fun id(args: JSONObject) = (args.opt("id") as? Number)?.toLong()?.takeIf { it > 0 && args.opt("id") !is Double }
        ?: throw ToolFailure("BAD_ARGUMENT", "«id» is a whole number from qr_history.")
    private fun int(args: JSONObject, key: String) = (args.opt(key) as? Int) ?: (args.opt(key) as? Long)?.toInt()
        ?: throw ToolFailure("BAD_ARGUMENT", "«$key» is a whole number.")
    private fun text(args: JSONObject, key: String, max: Int, required: Boolean = false): String {
        if (!args.has(key)) { if (required) throw ToolFailure("BAD_ARGUMENT", "«$key» is missing."); return "" }
        val v = args.opt(key) as? String ?: throw ToolFailure("BAD_ARGUMENT", "«$key» is text.")
        if (v.length > max) throw ToolFailure("BAD_ARGUMENT", "«$key» is longer than $max characters.")
        if (required && v.isBlank()) throw ToolFailure("BAD_ARGUMENT", "«$key» is empty.")
        return v
    }

}
