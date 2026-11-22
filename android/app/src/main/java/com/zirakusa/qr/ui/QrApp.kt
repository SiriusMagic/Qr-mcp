package com.zirakusa.qr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.ui.screens.CreateScreen
import com.zirakusa.qr.ui.screens.HistoryScreen
import com.zirakusa.qr.ui.screens.McpScreen
import com.zirakusa.qr.ui.screens.ResultScreen
import com.zirakusa.qr.ui.screens.ScanScreen
import com.zirakusa.qr.ui.screens.SettingsScreen
import com.zirakusa.qr.ui.theme.QrColors

/**
 * What the screens ask of the phone (the activity gives the real ones; tests leave the defaults). Every one of them
 * runs only after the user's tap: nothing is opened by itself or by MCP.
 */
class AppActions(
    /** The live camera, reading codes while [active]. Tests paint nothing. */
    val camera: @Composable (modifier: Modifier, active: Boolean, torch: Boolean) -> Unit = { _, _, _ -> },
    val askCamera: () -> Unit = {},
    val pickImage: () -> Unit = {},
    /** Opens what the code holds: the link, the dialer, a new email or SMS, «Añadir contacto», the map, Wi‑Fi. */
    val open: (Entry) -> Unit = {},
    val copy: (String) -> Unit = {},
    val share: (Entry) -> Unit = {},
    val saveImage: (Entry) -> Unit = {},
    val toast: (String) -> Unit = {},
    val copyLink: (String) -> Unit = {},
    val goToHub: () -> Unit = {},
)

/** The whole app: the chosen tab with the bar at the bottom, or a page over it. */
@Composable fun QrApp(state: QrState, actions: AppActions = AppActions()) {
    when (state.page) {
        Page.RESULT -> state.shown?.let { return ResultScreen(state, it, actions) }
        Page.SETTINGS -> return SettingsScreen(state, actions.toast)
        Page.MCP -> return McpScreen(actions.copyLink, actions.goToHub) { state.page = Page.SETTINGS }
        Page.MAIN -> Unit
    }
    Box(Modifier.fillMaxSize().background(QrColors.Background)) {
        when (state.tab) {
            Tab.SCAN -> ScanScreen(state, actions)
            Tab.CREATE -> CreateScreen(state, actions)
            Tab.HISTORY -> HistoryScreen(state)
        }
        TabBar(state, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun TabBar(state: QrState, modifier: Modifier) =
    Row(modifier.navigationBarsPadding().padding(bottom = 12.dp).clip(RoundedCornerShape(32.dp)).background(QrColors.Card.copy(alpha = 0.94f))
        .padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TabItem(Icons.Outlined.QrCodeScanner, R.string.tab_scan, state.tab == Tab.SCAN) { state.tab = Tab.SCAN }
        TabItem(Icons.Outlined.AddBox, R.string.tab_create, state.tab == Tab.CREATE) { state.tab = Tab.CREATE }
        TabItem(Icons.Outlined.History, R.string.tab_history, state.tab == Tab.HISTORY) { state.tab = Tab.HISTORY }
    }

@Composable private fun TabItem(icon: ImageVector, label: Int, on: Boolean, onClick: () -> Unit) =
    Row(Modifier.clip(CircleShape).background(if (on) QrColors.Accent else Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // The chosen tab shows its name; the others only their icon (named for TalkBack).
        Icon(icon, if (on) null else stringResource(label), tint = if (on) QrColors.OnAccent else QrColors.Text)
        if (on) Text(stringResource(label), Modifier.padding(start = 8.dp), color = QrColors.OnAccent, fontSize = 15.sp)
    }
