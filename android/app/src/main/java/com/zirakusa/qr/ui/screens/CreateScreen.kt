package com.zirakusa.qr.ui.screens

import com.zirakusa.qr.ui.Page
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.typeName
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/**
 * «Crear»: QR or bar code, the kind (or format), its fields, the code drawn live and «Guardar y compartir».
 * «Desde el portapapeles» fills the fields with the clipboard's text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun CreateScreen(state: QrState, actions: AppActions) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val emptyClipboard = stringResource(R.string.clipboard_empty)
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.create_title), Modifier.weight(1f).padding(start = 4.dp), fontSize = 26.sp)
            RoundButton(Icons.Outlined.ContentPaste, stringResource(R.string.from_clipboard)) {
                actions.paste()?.let(state::prefill) ?: actions.toast(emptyClipboard)
            }
        }
        // «Compartir un contacto»: a contact from the agenda as a vCard QR, classic or «Arbolito».
        Group(Modifier.clickable(onClickLabel = stringResource(R.string.share_contact)) { state.share.clear(); state.page = Page.SHARE_CONTACT }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(Icons.Outlined.ContactPage, 44.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(stringResource(R.string.share_contact), fontSize = 17.sp)
                    Text(stringResource(R.string.share_contact_hint), color = QrColors.Muted, fontSize = 14.sp)
                }
            }
        }
        val bar = state.barFormat
        Segmented(listOf(stringResource(R.string.qr_picture), stringResource(R.string.barcode)), if (bar == null) 0 else 1, Modifier.fillMaxWidth()) {
            state.barFormat = if (it == 0) null else bar ?: BarFormat.EAN_13
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bar == null) Type.entries.filter { it != Type.PLACE }.forEach { t ->
                Chip(stringResource(typeName(t)), state.type == t, { Icon(typeIcon(t), null, Modifier.size(18.dp)) }) { state.type = t }
            } else BarFormat.entries.forEach { f -> Chip(f.label, bar == f, null) { state.barFormat = f } }
        }
        Group(Modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (bar == null) Fields(state) else BarField(state, bar) } }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val text = state.draftText
            when {
                text != null && bar == null -> QrImage(text, side = 200.dp)
                text != null && bar != null -> BarImage(text, bar, Modifier.fillMaxWidth())
                else -> Text(stringResource(if (bar == null) problemText(state.problem) else barProblemText(state.barProblem, bar)), Modifier.padding(24.dp),
                    color = QrColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        if (state.draftText != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MainButton(stringResource(R.string.save_share)) { state.saveDraft()?.let(actions.share) }
        }
    }

@Composable private fun Chip(label: String, on: Boolean, icon: (@Composable () -> Unit)?, onClick: () -> Unit) =
    FilterChip(on, onClick, { Text(label) }, leadingIcon = icon,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = QrColors.Accent, selectedLabelColor = QrColors.OnAccent,
            selectedLeadingIconColor = QrColors.OnAccent))
