package com.zirakusa.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.zirakusa.qr.data.Read
import com.zirakusa.qr.data.SharedValues
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.mcp.McpMode
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrApp
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.Tab
import com.zirakusa.qr.ui.theme.QrColors
import com.zirakusa.qr.ui.theme.QrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Creates the state, asks for the camera, reads pictures and does what the screens ask. */
class MainActivity : ComponentActivity() {
    private lateinit var state: QrState
    private lateinit var openers: Openers
    private val camera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { state.cameraAllowed = it }
    private val picture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::readPicture) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = QrState(SharedValues(this))
        openers = Openers(this)
        McpMode.onChanged = { state.reload() }
        McpMode.load(this)
        val actions = AppActions(
            camera = { modifier, active, torch -> CameraView(modifier, active, torch, state.barcodes) { found(it, Source.CAMERA) } },
            askCamera = { camera.launch(Manifest.permission.CAMERA) },
            pickImage = { picture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            open = openers::open, copy = { openers.copy(it) }, share = openers::share, saveImage = openers::saveImage,
            toast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }, copyLink = ::copyLink, goToHub = ::goToHub,
        )
        setContent {
            QrTheme(state.themeMode, state.accent) {
                val light = QrColors.isLight
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light } }
                BackHandler(state.page != Page.MAIN || state.tab != Tab.SCAN, ::back)
                QrApp(state, actions)
            }
        }
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
        Page.MAIN -> state.tab = Tab.SCAN
    }

    private fun found(read: Read, source: Source) {
        if (state.vibrate) buzz()
        state.torch = false
        state.scanned(read, source)
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

    private fun buzz() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
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
