package com.zirakusa.qr

import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Read
import java.util.concurrent.Executors

/**
 * The back camera with its preview; each frame's brightness goes to [Codes.readFrame] on one worker thread (the
 * newest frame only). Frames are read only while [active] and never kept: nothing is saved or sent.
 */
@Composable fun CameraView(modifier: Modifier, active: Boolean, torch: Boolean, barcodes: Boolean, onRead: (Read) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val reading by rememberUpdatedState(active)
    val withBarcodes by rememberUpdatedState(barcodes)
    val found by rememberUpdatedState(onRead)
    var camera by remember { mutableStateOf<Camera?>(null) }
    DisposableEffect(owner) {
        val worker = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val show = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build()).build()
            analysis.setAnalyzer(worker) { image ->
                try {
                    if (reading) {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        Codes.readFrame(bytes, image.width, image.height, plane.rowStride, withBarcodes)?.let { read ->
                            main.post { if (reading) found(read) }
                        }
                    }
                } catch (_: RuntimeException) { // a broken frame: wait for the next one
                } finally { image.close() }
            }
            runCatching { p.unbindAll(); camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, show, analysis) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll(); camera = null; worker.shutdown() }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    AndroidView({ preview }, modifier)
}
