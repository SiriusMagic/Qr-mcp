package com.zirakusa.qr.mcp

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zirakusa.qr.BuildConfig
import com.zirakusa.qr.data.History
import com.zirakusa.qr.data.Settings
import com.zirakusa.qr.data.SharedValues
import com.zirakusa.qr.data.Source

/**
 * The public relay may run for MCP or for redirection. Only redirection serves the current Arka token.
 * The phone grants local access separately through the signature-protected provider.
 */
object McpMode {
    var remote by mutableStateOf(false); private set
    var enabled by mutableStateOf(false); private set
    var localMcp by mutableStateOf(false); private set
    val active get() = enabled || remote
    var state by mutableStateOf(RelayLink.State.OFFLINE); private set
    /** Called on the main thread after a tool changed the history, so the screen reloads it. */
    var onChanged: () -> Unit = {}

    private var prefs: android.content.SharedPreferences? = null
    private var link: RelayLink? = null
    private var device = ""
    private var relaySecret = ""
    private var token = Token("")
    internal var relayUrl: String = BuildConfig.RELAY_URL

    fun load(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("mcp", Context.MODE_PRIVATE)
        prefs = p
        remote = p.getBoolean("redirect", true)
        enabled = p.getBoolean("enabled", false)
        localMcp = p.getBoolean("localMcp", false)
        token = Token(SecretBox.open(p.getString("arka", "") ?: "").ifBlank {
            (Legacy.redirectSecret(SecretBox.open(p.getString("tokens", "") ?: "")) ?: Token.secret())
                .also { p.edit { putString("arka", SecretBox.seal(it)); remove("tokens") } }
        })
        device = p.getString("device", null) ?: Token.id().repeat(2).also { p.edit { putString("device", it) } }
        relaySecret = SecretBox.open(p.getString("relay", "") ?: "").ifBlank {
            Token.secret().also { p.edit { putString("relay", SecretBox.seal(it)) } }
        }
        data = LiveData(app)
        apply()
    }

    fun enable(on: Boolean) { enabled = on; prefs?.edit { putBoolean("enabled", on) }; apply() }
    fun allowLocal(on: Boolean) { localMcp = on; prefs?.edit { putBoolean("localMcp", on) } }

    fun turnRemote(on: Boolean) { remote = on; prefs?.edit { putBoolean("redirect", on) }; apply() }

    fun hubLink() = "${relayUrl.trimEnd('/')}/d/$device/mcp?token=${token.secret}"

    private var data: QrData? = null

    /** One JSON-RPC request from Lucía on this phone, through the same handler as the relay. Null before [load]. */
    fun local(body: String): String? {
        val d = data ?: return null
        return kotlinx.coroutines.runBlocking {
            McpHandler({ listOf(token) }, d, BuildConfig.VERSION_NAME).handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(), body).body
        }
    }

    private fun apply() {
        val d = data ?: return
        if (active && relayUrl.isNotBlank()) {
            if (link == null) link = RelayLink(relayUrl, device, relaySecret,
                McpHandler({ if (remote) listOf(token) else emptyList() }, d, BuildConfig.VERSION_NAME)) { state = it }.also { it.start() }
        } else { link?.stop(); link = null; state = RelayLink.State.OFFLINE }
    }
}

/** The real history for the tools: the same preferences as the screens; every change is shown. */
class LiveData(context: Context) : QrData {
    private val values = SharedValues(context)
    private val history = History(values)
    private val main = Handler(Looper.getMainLooper())
    override fun history() = history.list()
    override fun created(text: String) = if (!Settings(values).keepHistory) null
        else history.add(text, com.zirakusa.qr.data.Entry.QR, Source.CREATED).also { changed() }
    override fun delete(id: Long) = history.delete(id).also { if (it) changed() }
    private fun changed() = main.post { McpMode.onChanged() }
}
