package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Filter
import com.zirakusa.qr.data.Source
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * «Historial»: search, Todos · Leídos · Creados · Favoritos, each code (tap: open it) and the menu: Exportar,
 * Importar (CSV, through Android's file picker) and Borrar todo (asks first).
 */
@Composable fun HistoryScreen(state: QrState, actions: AppActions = AppActions(), zone: ZoneId = ZoneId.systemDefault()) =
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        var menu by remember { mutableStateOf(false) }
        var clearing by remember { mutableStateOf(false) }
        Row(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_history), Modifier.weight(1f).padding(start = 4.dp), fontSize = 26.sp)
            Box {
                RoundButton(Icons.Outlined.MoreVert, stringResource(R.string.more)) { menu = true }
                DropdownMenu(menu, { menu = false }, containerColor = QrColors.Card) {
                    MenuItem(Icons.Outlined.FileUpload, R.string.export_csv, enabled = state.items.isNotEmpty()) { menu = false; actions.exportHistory() }
                    MenuItem(Icons.Outlined.FileDownload, R.string.import_csv) { menu = false; actions.importHistory() }
                    MenuItem(Icons.Outlined.DeleteSweep, R.string.clear_all, enabled = state.items.isNotEmpty()) { menu = false; clearing = true }
                }
            }
        }
        val res = androidx.compose.ui.platform.LocalResources.current
        if (clearing) AlertDialog({ clearing = false }, title = { Text(stringResource(R.string.clear_all_title)) }, text = { Text(stringResource(R.string.clear_all_text)) },
            confirmButton = { TextButton({ clearing = false; val n = state.clearHistory(all = true); actions.toast(res.getQuantityString(R.plurals.cleared, n, n)) }) {
                Text(stringResource(R.string.clear_all), color = QrColors.Danger) } },
            dismissButton = { TextButton({ clearing = false }) { Text(stringResource(R.string.cancel), color = QrColors.Text) } })
        OutlinedTextField(state.query, { state.query = it.take(100) }, Modifier.fillMaxWidth().clip(CircleShape), singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.search)) }, shape = CircleShape)
        Segmented(listOf(R.string.f_all, R.string.f_scanned, R.string.f_created, R.string.f_favorites).map { stringResource(it) },
            state.filter.ordinal, Modifier.fillMaxWidth().padding(vertical = 12.dp)) { state.filter = Filter.entries[it] }
        val items = state.visible
        if (items.isEmpty()) Text(stringResource(if (state.items.isEmpty()) R.string.history_empty else R.string.nothing_found),
            Modifier.padding(24.dp).align(Alignment.CenterHorizontally), color = QrColors.Muted, fontSize = 16.sp)
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(items, key = { it.id }) { HistoryRow(it, zone) { state.show(it) } }
        }
    }

@Composable private fun HistoryRow(e: Entry, zone: ZoneId, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Badge(entryIcon(e))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(e.title.ifBlank { "—" }, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale()).format(Instant.ofEpochMilli(e.at).atZone(zone))
            Text(listOf(stringResource(entryName(e)), stringResource(sourceName(e.source)), time).joinToString(" · "),
                color = QrColors.Muted, fontSize = 13.sp, maxLines = 1)
        }
        if (e.favorite) Icon(Icons.Filled.Star, stringResource(R.string.favorite), Modifier.size(20.dp), tint = QrColors.AccentText)
    }

@Composable private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) =
    DropdownMenuItem({ Text(stringResource(label), fontSize = 16.sp) }, onClick, leadingIcon = { Icon(icon, null) }, enabled = enabled)

fun sourceName(s: Source) = when (s) { Source.CAMERA -> R.string.src_camera; Source.IMAGE -> R.string.src_image; Source.CREATED -> R.string.src_created }

@Composable fun locale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
