package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.Detail
import com.zirakusa.qr.data.VCard
import com.zirakusa.qr.ui.ContactShare
import com.zirakusa.qr.ui.theme.QrColors

fun detailName(k: Detail.Kind) = when (k) {
    Detail.Kind.NAME -> R.string.f_name; Detail.Kind.PHONE -> R.string.f_phone; Detail.Kind.EMAIL -> R.string.f_email
    Detail.Kind.ORG -> R.string.f_org; Detail.Kind.TITLE -> R.string.f_job; Detail.Kind.ADDRESS -> R.string.f_postal
    Detail.Kind.URL -> R.string.f_web; Detail.Kind.NOTE -> R.string.f_note
}

fun problemName(p: VCard.Problem) = when (p) { VCard.Problem.NO_NAME_OR_PHONE -> R.string.vcard_needs_name; VCard.Problem.TOO_LONG -> R.string.vcard_too_long }

/** Each detail of the contact with a tick: what is ticked goes in the code, nothing else. */
@Composable fun DetailChecks(share: ContactShare) = Column {
    share.details.forEachIndexed { i, d ->
        Row(Modifier.fillMaxWidth().clickable { share.toggle(i) }.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(i in share.chosen, { share.toggle(i) }, colors = CheckboxDefaults.colors(checkedColor = QrColors.Accent, checkmarkColor = QrColors.OnAccent))
            Column(Modifier.weight(1f)) {
                Text(stringResource(detailName(d.kind)), color = QrColors.Muted, fontSize = 13.sp)
                Text(d.value, fontSize = 16.sp, maxLines = 2)
            }
        }
    }
}

/** Exactly what another phone will see when it reads the code (before anything is drawn). */
@Composable fun Preview(share: ContactShare) = Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val chosen = share.selected
    if (chosen.isEmpty()) Text(stringResource(R.string.nothing_chosen), color = QrColors.Muted)
    chosen.forEach { d ->
        Row {
            Text(stringResource(detailName(d.kind)), Modifier.padding(end = 10.dp), color = QrColors.Muted, fontSize = 14.sp)
            Text(d.value, fontSize = 15.sp)
        }
    }
    Text(stringResource(R.string.preview_size, share.vcard.toByteArray(Charsets.UTF_8).size), color = QrColors.Muted, fontSize = 12.sp)
}

/** «Compartir»: asks first, listing what goes, then Android's own sheet where the person picks the app. */
@Composable fun ShareConfirm(share: ContactShare, onCancel: () -> Unit, onShare: () -> Unit) = AlertDialog(onCancel,
    title = { Text(stringResource(R.string.share_confirm_title)) },
    text = { Text(stringResource(R.string.share_confirm_text, share.selected.joinToString("\n") { "• " + it.value })) },
    confirmButton = { TextButton(onShare) { Text(stringResource(R.string.share), color = QrColors.AccentText) } },
    dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel), color = QrColors.Text) } })

@Composable fun PrivacyNote() = Row(verticalAlignment = Alignment.Top) {
    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp).padding(top = 2.dp), tint = QrColors.Muted)
    Text(stringResource(R.string.share_privacy), Modifier.padding(start = 8.dp), color = QrColors.Muted, fontSize = 13.sp)
}

@Composable fun IconLabel(icon: ImageVector, label: Int, onClick: () -> Unit) =
    Column(Modifier.clickable(onClickLabel = stringResource(label), onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = QrColors.AccentText)
        Text(stringResource(label), fontSize = 13.sp)
    }
