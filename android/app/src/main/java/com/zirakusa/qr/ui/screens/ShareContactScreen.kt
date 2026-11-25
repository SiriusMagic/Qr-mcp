package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.ContactShare
import com.zirakusa.qr.ui.Page
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/**
 * «Compartir un contacto»: first the details to include (ticked, with a preview of exactly what the code will carry),
 * then the code, «Arbolito» or classic, with «Guardar imagen» and «Compartir». Every picture was read back first.
 */
@Composable fun ShareContactScreen(state: QrState, actions: AppActions) {
    val share = state.share
    fun close() { share.clear(); state.page = Page.MAIN }
    Sheet(stringResource(R.string.share_contact), onClose = ::close, onBack = if (share.step == ContactShare.Step.CODE) share::back else null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (share.contact == null) {
                Text(stringResource(R.string.share_contact_intro), color = QrColors.Muted, fontSize = 15.sp)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MainButton(stringResource(R.string.pick_contact), onClick = actions.pickContact) }
                PrivacyNote()
            } else when (share.step) {
                ContactShare.Step.CHOOSE -> Choose(share, actions)
                ContactShare.Step.CODE -> Code(share, actions)
            }
        }
    }
}

@Composable private fun Choose(share: ContactShare, actions: AppActions) {
    Text(stringResource(R.string.choose_details), fontSize = 18.sp)
    Group { DetailChecks(share) }
    Text(stringResource(R.string.preview_title), fontSize = 18.sp)
    Group { Preview(share) }
    share.problem?.let { Text(stringResource(problemName(it)), color = QrColors.Danger, fontSize = 15.sp) }
    if (share.problem == null && share.dense) Text(stringResource(R.string.vcard_dense), color = QrColors.Muted, fontSize = 14.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.TextButton(actions.pickContact) { Text(stringResource(R.string.other_contact), color = QrColors.AccentText) }
        if (share.problem == null) MainButton(stringResource(R.string.make_code), onClick = share::generate)
    }
    PrivacyNote()
}

@Composable private fun Code(share: ContactShare, actions: AppActions) {
    var confirming by remember { mutableStateOf(false) }
    Segmented(listOf(stringResource(R.string.style_tree), stringResource(R.string.style_classic)), if (share.artistic) 0 else 1, Modifier.fillMaxWidth()) {
        share.changeStyle(it == 0)
    }
    val text = share.vcard; val title = share.title; val art = share.artistic
    LaunchedEffect(text, art) {
        if (share.picture == null) { val (tree, classic) = actions.drawContact(text, title, art); classic?.let { share.drawn(tree, it) } }
    }
    val picture = share.picture
    Box(Modifier.fillMaxWidth().heightIn(min = 280.dp), contentAlignment = Alignment.Center) {
        if (picture == null) CircularProgressIndicator(color = QrColors.Accent)
        else Image(picture.image, stringResource(R.string.code_for, title),
            Modifier.fillMaxWidth(if (share.fellBack || !art) 0.8f else 1f).aspectRatio(picture.image.width.toFloat() / picture.image.height), contentScale = ContentScale.Fit)
    }
    if (share.fellBack) Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = QrColors.AccentText)
        Text(stringResource(R.string.tree_fallback), Modifier.padding(start = 8.dp), color = QrColors.Muted, fontSize = 14.sp)
    }
    if (picture != null && !picture.readable) Text(stringResource(R.string.code_unreadable), color = QrColors.Danger, fontSize = 14.sp)
    if (picture != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconLabel(Icons.Outlined.Download, R.string.save_image) { actions.savePng(picture.png, title) }
        IconLabel(Icons.Outlined.Share, R.string.share) { confirming = true }
    }
    if (confirming && picture != null) ShareConfirm(share, { confirming = false }) { confirming = false; actions.sharePng(picture.png, title) }
    Text(stringResource(R.string.share_confirm_note), color = QrColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
