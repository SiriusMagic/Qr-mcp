package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Content
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.LinkCheck
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


/**
 * One code: what it holds, the real site and warnings for links, the code itself, the action that fits (open, call,
 * write, add the contact, join the Wi‑Fi…) and copy, share, save, favourite, name, edit and delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ResultScreen(state: QrState, e: Entry, actions: AppActions) {
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val c = e.content
    Sheet(stringResource(entryName(e)), onClose = state::closeResult) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(entryIcon(e), 52.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(e.title.ifBlank { "—" }, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text(if (e.isQr) stringResource(sourceName(e.source)) else "${e.format.replace('_', ' ')} · ${stringResource(sourceName(e.source))}",
                        color = QrColors.Muted, fontSize = 14.sp)
                }
            }
            if (c is Content.Link) LinkCard(c.url)
            Group { Fields(e) }
            ActionButton(c, e, actions)
            val bar = BarFormat.from(e.format)
            if (e.isQr) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(e.text, side = 220.dp) }
            else if (bar != null) BarImage(e.text, bar, Modifier.fillMaxWidth())
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Small(Icons.Outlined.ContentCopy, R.string.copy) { actions.copy(e.text) }
                Small(Icons.Outlined.Share, R.string.share) { actions.share(e) }
                if (e.isQr || bar != null) Small(Icons.Outlined.Download, R.string.save_image) { actions.saveImage(e) }
                if (e.id != 0L) {
                    Small(if (e.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, R.string.favorite, e.favorite) { state.toggleFavorite(e) }
                    Small(Icons.AutoMirrored.Outlined.Label, R.string.rename) { naming = true }
                }
                Small(Icons.Outlined.Edit, R.string.edit_new) { state.editAsNew(e) }
                if (e.id != 0L) Small(Icons.Outlined.Delete, R.string.delete) { deleting = true }
            }
        }
    }
    if (naming) TextDialog(stringResource(R.string.rename), e.name, Entry.NAME_MAX, { naming = false }) { state.rename(e, it); naming = false }
    if (deleting) AlertDialog({ deleting = false }, title = { Text(stringResource(R.string.delete_title)) },
        confirmButton = { TextButton({ deleting = false; state.delete(e) }) { Text(stringResource(R.string.delete), color = QrColors.Danger) } },
        dismissButton = { TextButton({ deleting = false }) { Text(stringResource(R.string.cancel), color = QrColors.Text) } })
}

@Composable private fun Fields(e: Entry) {
    val c = e.content
    val raw = e.text
    var reveal by remember { mutableStateOf(false) }
    val rows: List<Pair<Int, String>> = when (c) {
        is Content.Text -> listOf((if (e.isBarcode()) R.string.f_number else R.string.f_text) to c.text)
        is Content.Link -> listOf(R.string.f_address to c.url)
        is Content.Wifi -> listOfNotNull(R.string.f_ssid to c.ssid, R.string.f_security to when (c.security) {
            Security.WPA -> "WPA/WPA2"; Security.WEP -> "WEP"; Security.OPEN -> "—" },
            if (c.password.isNotEmpty()) R.string.f_password to (if (reveal) c.password else "••••••••") else null)
        is Content.Contact -> listOf(R.string.f_name to c.name, R.string.f_phone to c.phone, R.string.f_email to c.email, R.string.f_org to c.org,
            R.string.f_note to c.note).filter { it.second.isNotBlank() }
        is Content.Phone -> listOf(R.string.f_phone to c.number)
        is Content.Email -> listOf(R.string.f_email to c.to, R.string.f_subject to c.subject, R.string.f_body to c.body).filter { it.second.isNotBlank() }
        is Content.Sms -> listOf(R.string.f_phone to c.number, R.string.f_body to c.body).filter { it.second.isNotBlank() }
        is Content.Place -> listOf(R.string.f_place to raw.removePrefix("geo:"))
        is Content.Event -> {
            val l = locale()
            fun shown(t: java.time.LocalDateTime?) = t?.let {
                if (c.allDay) DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(l).format(it)
                else DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(l).format(it)
            }.orEmpty()
            listOf(R.string.f_title to c.title, R.string.f_start to shown(c.start), R.string.f_end to shown(c.end), R.string.f_location to c.place)
                .filter { it.second.isNotBlank() }
        }
    }
    rows.forEachIndexed { i, (label, value) ->
        if (i > 0) Divider()
        val secret = c is Content.Wifi && label == R.string.f_password
        Column(Modifier.fillMaxWidth().then(if (secret) Modifier.clickable(onClickLabel = stringResource(R.string.show_password)) { reveal = !reveal } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(stringResource(label), color = QrColors.Muted, fontSize = 13.sp)
            Text(value, fontSize = 17.sp, fontFamily = if (secret) FontFamily.Monospace else null)
        }
    }
}

/** The one action that fits what the code holds. */
@Composable private fun ActionButton(c: Content, e: Entry, actions: AppActions) {
    val label = when (c) {
        is Content.Link -> R.string.a_open; is Content.Wifi -> R.string.a_wifi; is Content.Contact -> R.string.a_contact
        is Content.Phone -> R.string.a_call; is Content.Email -> R.string.a_email; is Content.Sms -> R.string.a_sms
        is Content.Place -> R.string.a_map; is Content.Event -> R.string.a_calendar; is Content.Text -> return
    }
    var risky by remember { mutableStateOf(false) }
    val warned = c is Content.Link && LinkCheck.check(c.url)?.warnings.orEmpty().isNotEmpty()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MainButton(stringResource(label)) { if (warned) risky = true else actions.open(e) } }
    // A link with warnings asks once more; the safe choice is the highlighted one.
    if (risky) AlertDialog({ risky = false }, title = { Text(stringResource(R.string.risky_title)) }, text = { Text(stringResource(R.string.risky_text)) },
        confirmButton = { TextButton({ risky = false }) { Text(stringResource(R.string.dont_open), color = QrColors.AccentText) } },
        dismissButton = { TextButton({ risky = false; actions.open(e) }) { Text(stringResource(R.string.open_anyway), color = QrColors.Danger) } })
}

@Composable private fun Small(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, on: Boolean = false, onClick: () -> Unit) =
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(icon, stringResource(label), on = on, onClick = onClick)
        Text(stringResource(label), Modifier.padding(top = 4.dp), fontSize = 12.sp, color = QrColors.Muted)
    }
