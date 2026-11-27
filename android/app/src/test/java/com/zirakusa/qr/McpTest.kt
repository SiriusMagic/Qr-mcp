package com.zirakusa.qr

import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.MapValues
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.mcp.Access
import com.zirakusa.qr.mcp.HubLink
import com.zirakusa.qr.mcp.McpHandler
import com.zirakusa.qr.mcp.QrData
import com.zirakusa.qr.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

/** QR's MCP: tools list, good calls, pictures, bad arguments, not found, read-only tokens, wrong tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTest {
    private var t = 1_700_000_000_000L
    private val history = History(MapValues()) { t }
    private val data = object : QrData {
        var keeps = true
        override fun history() = history.list()
        override fun created(text: String) = if (keeps) history.add(text, Entry.QR, Source.CREATED) else null
        override fun delete(id: Long) = history.delete(id)
        override fun zone() = ZoneOffset.UTC
    }
    private val writer = Token("qrc_" + "w".repeat(40), Access.WRITE)
    private val reader = Token("qrc_" + "r".repeat(40), Access.READ)
    private val handler = McpHandler({ listOf(writer, reader) }, data, "0.1.0")

    init {
        history.add("WIFI:T:WPA;S:Casa;P:secreta123;;", "QR_CODE", Source.CAMERA)
        t += 60_000
        history.add("http://bit.ly/oferta", "QR_CODE", Source.IMAGE)
        t += 60_000
        history.add("9780306406157", "EAN_13", Source.CAMERA)
    }

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body).getJSONObject("result")
    }
    private fun tool(name: String, args: JSONObject, token: Token = writer) = rpc(token, "tools/call", JSONObject().put("name", name).put("arguments", args))
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject(), token: Token = writer) = tool(name, args, token).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")
    private fun names(token: Token) = rpc(token, "tools/list").getJSONArray("tools").let { t -> (0 until t.length()).map { t.getJSONObject(it).getString("name") } }

    @Test fun listsTheTools() {
        assertEquals(listOf("qr_history", "qr_item", "qr_parse", "qr_make", "qr_create", "qr_delete"), names(writer))
        assertEquals(listOf("qr_history", "qr_item", "qr_parse", "qr_make"), names(reader))
        val init = rpc(writer, "initialize")
        assertEquals("qr", init.getJSONObject("serverInfo").getString("name"))
        assertEquals("redirect", init.getJSONObject("_meta").getJSONObject(McpHandler.TOKEN_META).getString("kind"))
    }

    @Test fun readsHistoryItemsAndParses() {
        val all = ok("qr_history")
        assertEquals(3, all.getInt("total"))
        assertEquals("9780306406157", all.getJSONArray("items").getJSONObject(0).getString("title"))
        assertEquals("2023-11-14T22:15:20", all.getJSONArray("items").getJSONObject(0).getString("at"))
        assertEquals(1, ok("qr_history", JSONObject().put("query", "casa")).getInt("total"))
        assertEquals(0, ok("qr_history", JSONObject().put("filter", "created")).getInt("total"))
        assertEquals(1, ok("qr_history", JSONObject().put("limit", 1)).getJSONArray("items").length())
        val wifi = ok("qr_item", JSONObject().put("id", 1)).getJSONObject("content")
        assertEquals("Casa", wifi.getString("ssid")); assertTrue(wifi.getBoolean("has_password")); assertFalse(wifi.has("password"))
        val link = ok("qr_item", JSONObject().put("id", 2)).getJSONObject("content")
        assertEquals("bit.ly", link.getString("site")); assertEquals("[\"not_https\",\"shortener\"]", link.getJSONArray("warnings").toString())
        val parsed = ok("qr_parse", JSONObject().put("text", "https://banco.com@malo.ru/x"))
        assertEquals("malo.ru", parsed.getString("site")); assertEquals("user_info", parsed.getJSONArray("warnings").getString(0))
        assertEquals("contact", ok("qr_parse", JSONObject().put("text", "MECARD:N:Ana;;")).getString("type"))
    }

    @Test fun picturesMakeAndCreate() {
        val withImage = tool("qr_item", JSONObject().put("id", 1).put("image", true)).getJSONArray("content")
        assertEquals("image", withImage.getJSONObject(1).getString("type")); assertEquals("image/png", withImage.getJSONObject(1).getString("mimeType"))
        assertEquals(1, tool("qr_item", JSONObject().put("id", 3).put("image", true)).getJSONArray("content").length()) // bar codes: no QR picture
        val made = tool("qr_make", JSONObject().put("type", "wifi").put("ssid", "Oficina").put("password", "clave1234"))
        assertEquals("WIFI:T:WPA;S:Oficina;P:clave1234;;", made.getJSONObject("structuredContent").getString("text"))
        assertTrue(made.getJSONArray("content").getJSONObject(1).getString("data").startsWith("iVBOR"))           // a PNG
        assertEquals(3, history.list().size)                                                                       // qr_make keeps nothing
        val created = ok("qr_create", JSONObject().put("type", "link").put("url", "zirakusa.com"))
        assertEquals("https://zirakusa.com", created.getString("text")); assertTrue(created.getBoolean("saved"))
        assertEquals(4, history.list().size)
        assertEquals(created.getLong("id"), ok("qr_delete", JSONObject().put("id", created.getLong("id"))).getLong("deleted"))
        data.keeps = false
        assertFalse(ok("qr_create", JSONObject().put("type", "text").put("text", "Hola")).getBoolean("saved"))
        assertEquals(3, history.list().size)
    }

    /** Calendar events (0.2.0): made, kept, read back; bad dates refused. */
    @Test fun eventsMakeCreateAndParse() {
        val made = ok("qr_make", JSONObject().put("type", "event").put("title", "Cena").put("start", "2026-11-24T18:30")
            .put("end", "2026-11-24T21:00").put("location", "Quito"))
        assertEquals("BEGIN:VEVENT\nSUMMARY:Cena\nDTSTART:20261124T183000\nDTEND:20261124T210000\nLOCATION:Quito\nEND:VEVENT", made.getString("text"))
        val content = made.getJSONObject("content")
        assertEquals("event", content.getString("type")); assertEquals("2026-11-24T18:30", content.getString("start"))
        assertEquals("Quito", content.getString("location")); assertFalse(content.getBoolean("all_day"))
        val day = ok("qr_create", JSONObject().put("type", "event").put("title", "Feriado").put("start", "2026-12-25"))
        assertTrue(day.getBoolean("saved")); assertEquals(4, history.list().size)
        assertTrue(ok("qr_item", JSONObject().put("id", day.getLong("id"))).getJSONObject("content").getBoolean("all_day"))
        assertEquals("2026-12-25", ok("qr_parse", JSONObject().put("text", day.getString("text"))).getString("start"))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "event").put("title", "Cena").put("start", "mañana")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "event").put("title", "Cena")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "event").put("start", "2026-11-24")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "event").put("title", "Cena").put("start", "2026-11-24T18:00")
            .put("end", "2026-11-24T17:00")))
        assertEquals("BAD_ARGUMENT", code("qr_create", JSONObject().put("type", "event").put("title", 5).put("start", "2026-11-24")))
        assertEquals("READ_ONLY", code("qr_create", JSONObject().put("type", "event").put("title", "x").put("start", "2026-11-24"), reader))
        assertEquals(4, history.list().size)
    }

    @Test fun badArgumentsAreRefused() {
        assertEquals("BAD_ARGUMENT", code("qr_history", JSONObject().put("filter", "old")))
        assertEquals("BAD_ARGUMENT", code("qr_history", JSONObject().put("limit", 500)))
        assertEquals("BAD_ARGUMENT", code("qr_history", JSONObject().put("sql", "DROP")))
        assertEquals("BAD_ARGUMENT", code("qr_item", JSONObject().put("id", "1")))
        assertEquals("BAD_ARGUMENT", code("qr_item", JSONObject().put("id", 1.5)))
        assertEquals("BAD_ARGUMENT", code("qr_parse", JSONObject().put("text", " ")))
        assertEquals("BAD_ARGUMENT", code("qr_parse", JSONObject().put("text", "x".repeat(3000))))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "place")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "wifi").put("ssid", "Casa").put("password", "corta")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "link").put("url", "javascript:alert(1)")))
        assertEquals("BAD_ARGUMENT", code("qr_make", JSONObject().put("type", "email").put("email", "nada")))
        assertEquals("BAD_ARGUMENT", code("qr_create", JSONObject().put("type", "text")))
        assertEquals(3, history.list().size)
    }

    @Test fun notFoundUnknownAndReadOnly() {
        assertEquals("NOT_FOUND", code("qr_item", JSONObject().put("id", 99)))
        assertEquals("NOT_FOUND", code("qr_delete", JSONObject().put("id", 99)))
        assertEquals("UNKNOWN_TOOL", code("qr_open_link"))
        assertEquals("READ_ONLY", code("qr_delete", JSONObject().put("id", 1), reader))
        assertEquals("READ_ONLY", code("qr_create", JSONObject().put("type", "text").put("text", "x"), reader))
        assertEquals(3, history.list().size)
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer wrong_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer qrc_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertEquals(404, handler.handle("POST", "/other", emptyMap(), emptyMap(), body).status)
        assertTrue(Token.secret().startsWith("qrc_"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        // Only the same developer (Arka) may connect, disconnect, read the status or run a tool.
        listOf(HubLink.disconnect(false), HubLink.status(false)).forEach { assertEquals("NOT_SAME_DEVELOPER", it.getString("error")) }
    }

    /** Lucía runs a tool on the phone through the same handler, with or without the public link; Arka turns that on and off. */
    @Test fun luciaRunsToolsOnThePhone() {
        val relay = com.zirakusa.qr.mcp.McpMode.relayUrl
        com.zirakusa.qr.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.qr.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.qr.mcp.McpMode.turnRemote(false)
        val list = JSONObject(com.zirakusa.qr.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        com.zirakusa.qr.mcp.McpMode.allowLocal(false)
        assertEquals("LOCAL_DISABLED", HubLink.tool(true, "{}").getString("error"))
        com.zirakusa.qr.mcp.McpMode.allowLocal(true)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        assertEquals("BAD_REQUEST", HubLink.tool(true, "").getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertTrue(HubLink.status(true).containsKey("remote"))
        HubLink.connect(true); assertTrue(com.zirakusa.qr.mcp.McpMode.remote)                 // Redireccionar a Hub
        assertFalse(HubLink.disconnect(true).getBoolean("remote")); assertFalse(com.zirakusa.qr.mcp.McpMode.remote)
        com.zirakusa.qr.mcp.McpMode.allowLocal(false)
        com.zirakusa.qr.mcp.McpMode.relayUrl = relay                                          // as the other tests expect
    }

    /** The hub's token from before Arka took over is kept, so the hub's link keeps working. */
    @Test fun theOldHubTokenIsKept() {
        val old = """[{"id":"a","secret":"${"l".repeat(44)}","kind":"llm"},{"id":"b","secret":"${"h".repeat(44)}","kind":"redirect"}]"""
        assertEquals("h".repeat(44), com.zirakusa.qr.mcp.Legacy.redirectSecret(old))
        assertEquals(null, com.zirakusa.qr.mcp.Legacy.redirectSecret("[]"))
        assertEquals(null, com.zirakusa.qr.mcp.Legacy.redirectSecret(""))
    }
}
