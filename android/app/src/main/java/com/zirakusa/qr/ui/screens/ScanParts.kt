package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.Pending
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors
import kotlinx.coroutines.delay

/** «Confirmar escaneos manualmente»: what the camera read, and nothing happens until «Usar». */
@Composable fun PendingCard(state: QrState, pending: Pending, actions: AppActions, modifier: Modifier) {
    val e = Entry(0, pending.read.text, pending.read.format, pending.source)
    Column(modifier.padding(horizontal = 16.dp).padding(bottom = 110.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(QrColors.Card)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(entryIcon(e))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(entryName(e)), color = QrColors.Muted, fontSize = 13.sp)
                Text(e.title.ifBlank { "—" }, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(state::dropPending, Modifier.weight(1f)) { Text(stringResource(R.string.discard), color = QrColors.Text) }
            MainButton(stringResource(R.string.use_code), Modifier.weight(1f)) { state.confirmPending()?.let(actions.handled) }
        }
    }
}

/** «Escaneo continuo»: the last code, for a few seconds. A tap shows it. */
@Composable fun QuickNote(state: QrState, e: Entry, modifier: Modifier) {
    LaunchedEffect(e) { delay(NOTE_MS); if (state.quick == e) state.quick = null }
    Text(stringResource(if (e.id != 0L) R.string.quick_saved else R.string.quick_read, e.title.ifBlank { "—" }),
        modifier.statusBarsPadding().padding(top = 80.dp, start = 24.dp, end = 24.dp).clip(CircleShape).background(QrColors.Card)
            .clickable { state.quick = null; state.show(e) }.padding(horizontal = 18.dp, vertical = 10.dp),
        color = QrColors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val NOTE_MS = 4_000L
