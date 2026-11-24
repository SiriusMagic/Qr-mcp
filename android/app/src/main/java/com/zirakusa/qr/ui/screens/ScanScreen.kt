package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/**
 * «Escanear»: the camera full screen with a frame in the middle; light and «Desde imagen» below, Ajustes on top.
 * Without the camera permission, a card that asks for it (a picture can still be read). A code waiting for «Usar»
 * («Confirmar escaneos manualmente») replaces the tools; «Escaneo continuo» notes each code on top.
 */
@Composable fun ScanScreen(state: QrState, actions: AppActions) = Box(Modifier.fillMaxSize().background(Color.Black)) {
    if (state.cameraAllowed) {
        actions.camera(Modifier.fillMaxSize(), state.page == Page.MAIN && state.pending == null, state.torch)
        Frame(Modifier.align(Alignment.Center))
        Text(stringResource(R.string.scan_hint), Modifier.align(Alignment.Center).padding(top = 330.dp).clip(CircleShape)
            .background(Color(0x99000000)).padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 15.sp)
    } else Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(28.dp)).background(QrColors.Card).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(48.dp), tint = QrColors.AccentText)
        Text(stringResource(R.string.camera_title), fontSize = 20.sp, color = QrColors.Text)
        Text(stringResource(R.string.camera_text), color = QrColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        MainButton(stringResource(R.string.camera_allow), onClick = actions.askCamera)
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp), color = Color.White, fontSize = 24.sp)
        RoundButton(Icons.Outlined.Settings, stringResource(R.string.settings)) { state.page = Page.SETTINGS }
    }
    val pending = state.pending
    if (pending != null) PendingCard(state, pending, actions, Modifier.align(Alignment.BottomCenter))
    else Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        if (state.cameraAllowed) Tool(Icons.Outlined.Cameraswitch, stringResource(R.string.switch_camera), state.frontCamera) {
            state.changeFrontCamera(!state.frontCamera) }
        if (state.cameraAllowed && !state.frontCamera) Tool(if (state.torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
            stringResource(R.string.torch), state.torch) { state.torch = !state.torch }
        Tool(Icons.Outlined.Image, stringResource(R.string.from_image), false, actions.pickImage)
    }
    state.quick?.let { QuickNote(state, it, Modifier.align(Alignment.TopCenter)) }
}

@Composable fun Tool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(if (on) QrColors.Accent else Color(0x33FFFFFF)).clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center) { Icon(icon, label, tint = if (on) QrColors.OnAccent else Color.White) }
        Text(label, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 13.sp)
    }

/** Four accent corners around where the code should be. */
@Composable private fun Frame(modifier: Modifier) {
    val color = QrColors.Accent
    Canvas(modifier.size(260.dp)) {
        val l = size.width * 0.2f; val w = 6.dp.toPx(); val s = size.width
        listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(s, 0f) to Offset(-1f, 1f), Offset(0f, s) to Offset(1f, -1f), Offset(s, s) to Offset(-1f, -1f)).forEach { (o, d) ->
            drawLine(color, o, Offset(o.x + d.x * l, o.y), w, StrokeCap.Round)
            drawLine(color, o, Offset(o.x, o.y + d.y * l), w, StrokeCap.Round)
        }
    }
}
