package com.zirakusa.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.HistoryCsv
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.SharedValues
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.mcp.McpMode
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrApp
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.Step
import com.zirakusa.qr.ui.Tab
import com.zirakusa.qr.ui.theme.QrColors
import com.zirakusa.qr.ui.theme.QrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Creates the state, asks for the camera, reads pictures, takes shared text, exports and imports, and does what the screens ask. */
class MainActivity : ComponentActivity() {
    private lateinit var state: QrState
    private lateinit var openers: Openers
    private lateinit var feedback: Feedback
    private val camera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { state.cameraAllowed = it }
    private val picture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::readPicture) }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(::writeCsv) }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::readCsv) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = QrState(SharedValues(this))
        openers = Openers(this)
        feedback = Feedback(this)
        McpMode.onChanged = { state.reload() }
        McpMode.load(this)
        val actions = AppActions(
            camera = { modifier, active, torch -> CameraView(modifier, active, torch, state.barcodes, state.frontCamera) { found(it, Source.CAMERA) } },
            askCamera = { camera.launch(Manifest.permission.CAMERA) },
            pickImage = { picture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            open = openers::open, copy = { openers.copy(it) }, share = openers::share, saveImage = openers::saveImage,
            toast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }, copyLink = ::copyLink, goToHub = ::goToHub,
            handled = ::handled, paste = openers::paste,
            exportHistory = { exportFile.launch("QR-${getString(R.string.tab_history).lowercase()}-${java.time.LocalDate.now()}.csv") },
            importHistory = { importFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
        )
        if (savedInstanceState == null) shared(intent)
        setContent {
            QrTheme(state.themeMode, state.accent) {
                val light = QrColors.isLight
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light } }
                BackHandler(state.page != Page.MAIN || state.tab != Tab.SCAN || state.pending != null, ::back)
                QrApp(state, actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shared(intent)
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    /** Text shared from another app («Compartir» › QR): «Crear» opens with it. Nothing is read or opened by itself. */
    private fun shared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(state::prefill)
    }

    override fun onResume() {
        super.onResume()
        state.cameraAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        state.reload()
    }

    private fun back() = when (state.page) {
        Page.MCP -> state.page = Page.SETTINGS
        Page.RESULT -> state.closeResult()
        Page.SETTINGS -> state.page = Page.MAIN
        Page.MAIN -> if (state.pending != null) state.dropPending() else state.tab = Tab.SCAN
    }

    private fun found(read: Read, source: Source) {
        val step = state.seen(read, source)
        if (step == Step.Ignored) return
        if (state.vibrate) feedback.buzz()
        if (state.sound) feedback.beep()
        if (step is Step.Done) handled(step.entry)
    }

    /** After a code is kept and shown (or noted): «Copiar al portapapeles» and the light off. */
    private fun handled(e: Entry) {
        if (state.autoCopy) openers.copy(e.text)
        if (state.page == Page.RESULT) state.torch = false
    }

    private fun writeCsv(uri: Uri) = lifecycleScope.launch {
        val csv = withContext(Dispatchers.Default) { HistoryCsv.build(state.items) }
        val ok = withContext(Dispatchers.IO) { openers.writeText(uri, csv) }
        Toast.makeText(this@MainActivity, if (ok) R.string.exported else R.string.not_saved, Toast.LENGTH_SHORT).show()
    }

    private fun readCsv(uri: Uri) = lifecycleScope.launch {
        val rows = withContext(Dispatchers.IO) { openers.readText(uri, HistoryCsv.MAX_CHARS) }
            ?.let { withContext(Dispatchers.Default) { HistoryCsv.parse(it, System.currentTimeMillis()) } }
        if (rows == null) { Toast.makeText(this@MainActivity, R.string.not_read, Toast.LENGTH_LONG).show(); return@launch }
        val n = state.import(rows)
        Toast.makeText(this@MainActivity, resources.getQuantityString(R.plurals.imported, n, n), Toast.LENGTH_LONG).show()
    }

    /** A picture from the phone (Android's photo picker: no permission), made smaller and read once. */
    private fun readPicture(uri: Uri) = lifecycleScope.launch {
        val read = withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
                val bitmap: Bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                Codes.readPixels(pixels, bitmap.width, bitmap.height, state.barcodes).also { bitmap.recycle() }
            }.getOrNull()
        }
        if (read != null) found(read, Source.IMAGE) else Toast.makeText(this@MainActivity, R.string.no_code, Toast.LENGTH_LONG).show()
    }

    private fun copyLink(link: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QR MCP", link))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** «Ir al Hub»: copies the hub's link (in case it must be pasted) and opens the Gateway Hub app. */
    private fun goToHub() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QR › Gateway Hub", McpMode.hubLink()))
        val open = packageManager.getLaunchIntentForPackage("com.zirakusa.gatewayhub")
        if (open != null) startActivity(open) else Toast.makeText(this, R.string.mcp_paste_in_hub, Toast.LENGTH_LONG).show()
    }

    companion object { const val MAX_SIDE = 1600 }
}
