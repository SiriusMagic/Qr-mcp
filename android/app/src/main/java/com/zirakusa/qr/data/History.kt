package com.zirakusa.qr.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Where a code came from. */
enum class Source(val key: String) {
    CAMERA("camera"), IMAGE("image"), CREATED("created");
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CAMERA }
}

/** One code in «Historial»: its text, the barcode format (QR_CODE, EAN_13…), where it came from and when. */
data class Entry(val id: Long, val text: String, val format: String = QR, val source: Source = Source.CAMERA, val at: Long = 0,
    val favorite: Boolean = false, val name: String = "") {
    val content get() = Payload.parse(text)
    /** The user's name for it, or what it holds. */
    val title get() = name.ifBlank { Payload.title(content) }
    val isQr get() = format == QR

    fun json(): JSONObject = JSONObject().put("id", id).put("text", text).put("format", format).put("source", source.key).put("at", at)
        .put("favorite", favorite).put("name", name)

    companion object {
        const val QR = "QR_CODE"
        const val NAME_MAX = 60
        fun from(o: JSONObject) = Entry(o.getLong("id"), o.getString("text"), o.optString("format", QR), Source.from(o.optString("source")),
            o.optLong("at"), o.optBoolean("favorite"), o.optString("name"))
    }
}

/** Filters of «Historial». */
enum class Filter { ALL, SCANNED, CREATED, FAVORITES }

/**
 * «Historial»: newest first, at most [MAX] (the oldest non‑favourite goes first). The same code read again within
 * [REPEAT_MS] is not added twice (the camera sees it in every frame). JSON in the app's preferences.
 */
class History(private val kv: KeyValues, private val now: () -> Long = System::currentTimeMillis) {
    fun list(): List<Entry> = runCatching {
        val a = JSONArray(kv.get(KEY) ?: "[]")
        (0 until a.length()).map { Entry.from(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun get(id: Long) = list().firstOrNull { it.id == id }

    /** Adds a code (or brings the same recent one to the top); returns it. */
    fun add(text: String, format: String, source: Source): Entry {
        val items = list()
        val at = now()
        items.firstOrNull { it.text == text && it.source == source && at - it.at in 0..REPEAT_MS }?.let { return it }
        val entry = Entry((items.maxOfOrNull { it.id } ?: 0) + 1, text.take(Payload.MAX * 2), format, source, at)
        save(trim(listOf(entry) + items))
        return entry
    }

    fun update(entry: Entry) = save(list().map { if (it.id == entry.id) entry else it })
    fun delete(id: Long): Boolean { val items = list(); return (items.any { it.id == id }).also { if (it) save(items.filterNot { e -> e.id == id }) } }
    /** «Borrar historial»: favourites stay unless [all]. Returns how many went. */
    fun clear(all: Boolean = false): Int { val items = list(); val keep = if (all) emptyList() else items.filter { it.favorite }; save(keep); return items.size - keep.size }

    private fun trim(items: List<Entry>): List<Entry> {
        if (items.size <= MAX) return items
        val drop = items.size - MAX
        val oldest = items.filterNot { it.favorite }.takeLast(drop).map { it.id }.toSet()
        return items.filterNot { it.id in oldest }.take(MAX)
    }

    private fun save(items: List<Entry>) = kv.put(KEY, JSONArray(items.map { it.json() }).toString())

    companion object {
        const val MAX = 500
        const val REPEAT_MS = 5_000L
        private const val KEY = "history"

        fun filter(items: List<Entry>, filter: Filter, query: String = ""): List<Entry> {
            val q = plain(query.trim())
            return items.filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.SCANNED -> it.source != Source.CREATED
                    Filter.CREATED -> it.source == Source.CREATED
                    Filter.FAVORITES -> it.favorite
                }
            }.filter { q.isEmpty() || plain(it.text).contains(q) || plain(it.name).contains(q) }
        }

        /** Lower case without accents, for search. */
        fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}
