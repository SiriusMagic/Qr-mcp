package com.zirakusa.qr.ui.screens

import com.zirakusa.qr.IconCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.BuildConfig
import com.zirakusa.qr.R
import com.zirakusa.qr.mcp.McpMode
import com.zirakusa.qr.mcp.RelayLink
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/** «Ajustes»: reading (barcodes, vibrate, sound, copy, continuous, confirm, camera), history, Modo MCP, Temas, privacy and version. */
@Composable fun SettingsScreen(state: QrState, toast: (String) -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(QrColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTop(stringResource(R.string.settings)) { state.page = Page.MAIN }
        Caption(stringResource(R.string.reading))
        Group {
            SwitchItem(stringResource(R.string.barcodes), stringResource(R.string.barcodes_hint), state.barcodes, onChange = state::changeBarcodes)
            Divider()
            SwitchItem(stringResource(R.string.vibrate), null, state.vibrate, onChange = state::changeVibrate)
            Divider()
            SwitchItem(stringResource(R.string.sound), null, state.sound, onChange = state::changeSound)
            Divider()
            SwitchItem(stringResource(R.string.auto_copy), stringResource(R.string.auto_copy_hint), state.autoCopy, onChange = state::changeAutoCopy)
            Divider()
            SwitchItem(stringResource(R.string.continuous), stringResource(R.string.continuous_hint), state.continuous, onChange = state::changeContinuous)
            Divider()
            SwitchItem(stringResource(R.string.confirm), stringResource(R.string.confirm_hint), state.confirm, onChange = state::changeConfirm)
            Divider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.camera), fontSize = 18.sp)
                Segmented(listOf(stringResource(R.string.camera_back), stringResource(R.string.camera_front)), if (state.frontCamera) 1 else 0,
                    Modifier.fillMaxWidth()) { state.changeFrontCamera(it == 1) }
            }
        }
        Caption(stringResource(R.string.tab_history))
        Group {
            SwitchItem(stringResource(R.string.keep_history), stringResource(R.string.keep_history_hint), state.keepHistory, onChange = state::changeKeepHistory)
            Divider()
            SwitchItem(stringResource(R.string.duplicates), stringResource(R.string.duplicates_hint), state.duplicates, enabled = state.keepHistory,
                onChange = state::changeDuplicates)
            Divider()
            Item(stringResource(R.string.clear_history), stringResource(R.string.clear_history_hint), onClick = { clearing = true })
        }
        Group {
            val status = when {
                !McpMode.active -> stringResource(R.string.mcp_status_off)
                McpMode.hubRedirect -> stringResource(R.string.mcp_redirect)
                McpMode.state == RelayLink.State.ONLINE -> stringResource(R.string.mcp_online)
                McpMode.state == RelayLink.State.CONNECTING -> stringResource(R.string.mcp_connecting)
                else -> stringResource(R.string.mcp_offline)
            }
            Item(stringResource(R.string.mcp_title), "$status · ${stringResource(R.string.mcp_settings_hint)}") { state.page = Page.MCP }
        }
        IconCard(QrColors.Card, QrColors.Text, QrColors.Muted, QrColors.Accent)
        ThemeCard(state.themeMode, state.accent, state::changeTheme, state::changeAccent)
        Caption(stringResource(R.string.about))
        Group {
            Item(stringResource(R.string.privacy), null) { privacy = true }
            Divider()
            Item(stringResource(R.string.version_title), BuildConfig.VERSION_NAME, onClick = null)
        }
        Text("", Modifier.padding(bottom = 16.dp))
    }
    val res = androidx.compose.ui.platform.LocalResources.current
    if (clearing) AlertDialog({ clearing = false }, title = { Text(stringResource(R.string.clear_history)) }, text = { Text(stringResource(R.string.clear_history_text)) },
        confirmButton = { TextButton({ clearing = false; val n = state.clearHistory(); toast(res.getQuantityString(R.plurals.cleared, n, n)) }) {
            Text(stringResource(R.string.delete), color = QrColors.Danger) } },
        dismissButton = { TextButton({ clearing = false }) { Text(stringResource(R.string.cancel), color = QrColors.Text) } })
    if (privacy) AlertDialog({ privacy = false }, confirmButton = { TextButton({ privacy = false }) { Text(stringResource(R.string.done), color = QrColors.AccentText) } },
        title = { Text(stringResource(R.string.privacy)) }, text = { Text(stringResource(R.string.privacy_text)) })
}
