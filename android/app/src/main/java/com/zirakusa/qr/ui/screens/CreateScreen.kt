package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.typeName
import com.zirakusa.qr.ui.AppActions
import com.zirakusa.qr.ui.Form
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/** «Crear»: the kind of code, its fields, the code drawn live and «Guardar y compartir». */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun CreateScreen(state: QrState, actions: AppActions) =
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.create_title), Modifier.padding(top = 20.dp, start = 4.dp), fontSize = 26.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Type.entries.filter { it != Type.PLACE }.forEach { t ->
                FilterChip(state.type == t, { state.type = t }, { Text(stringResource(typeName(t))) },
                    leadingIcon = { Icon(typeIcon(t), null, Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = QrColors.Accent, selectedLabelColor = QrColors.OnAccent,
                        selectedLeadingIconColor = QrColors.OnAccent))
            }
        }
        Group(Modifier) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Fields(state) } }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val text = state.draftText
            if (text != null) QrImage(text, side = 200.dp) else Text(stringResource(problemText(state.problem)), Modifier.padding(24.dp),
                color = QrColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        if (state.draftText != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MainButton(stringResource(R.string.save_share)) { state.saveDraft()?.let(actions.share) }
        }
    }

@Composable private fun Fields(state: QrState) {
    val f = state.form
    fun set(next: Form) { state.form = next }
    when (state.type) {
        Type.TEXT, Type.PLACE -> Field(R.string.f_text, f.text, lines = 4) { set(f.copy(text = it)) }
        Type.LINK -> Field(R.string.f_url, f.url, keyboard = KeyboardType.Uri) { set(f.copy(url = it)) }
        Type.WIFI -> {
            Field(R.string.f_ssid, f.ssid) { set(f.copy(ssid = it.take(32))) }
            Segmented(listOf(stringResource(R.string.wifi_wpa), stringResource(R.string.wifi_wep), stringResource(R.string.wifi_open)),
                f.security.ordinal, Modifier.fillMaxWidth()) { set(f.copy(security = Security.entries[it])) }
            if (f.security != Security.OPEN) Field(R.string.f_password, f.password, password = true) { set(f.copy(password = it.take(63))) }
            SwitchItem(stringResource(R.string.wifi_hidden), null, f.hidden) { set(f.copy(hidden = it)) }
        }
        Type.CONTACT -> {
            Field(R.string.f_name, f.name) { set(f.copy(name = it.take(100))) }
            Field(R.string.f_phone, f.phone, keyboard = KeyboardType.Phone) { set(f.copy(phone = it.take(40))) }
            Field(R.string.f_email, f.email, keyboard = KeyboardType.Email) { set(f.copy(email = it.take(100))) }
            Field(R.string.f_org, f.org) { set(f.copy(org = it.take(100))) }
        }
        Type.PHONE -> Field(R.string.f_phone, f.phone, keyboard = KeyboardType.Phone) { set(f.copy(phone = it.take(40))) }
        Type.EMAIL -> {
            Field(R.string.f_email, f.email, keyboard = KeyboardType.Email) { set(f.copy(email = it.take(100))) }
            Field(R.string.f_subject, f.subject) { set(f.copy(subject = it.take(200))) }
            Field(R.string.f_body, f.body, lines = 3) { set(f.copy(body = it.take(1000))) }
        }
        Type.SMS -> {
            Field(R.string.f_phone, f.phone, keyboard = KeyboardType.Phone) { set(f.copy(phone = it.take(40))) }
            Field(R.string.f_body, f.body, lines = 3) { set(f.copy(body = it.take(1000))) }
        }
    }
}

@Composable private fun Field(label: Int, value: String, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false,
    onChange: (String) -> Unit) =
    OutlinedTextField(value, { onChange(it.take(Payload.MAX)) }, Modifier.fillMaxWidth().background(QrColors.Card), label = { Text(stringResource(label)) },
        singleLine = lines == 1, minLines = lines, keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)

/** Why the code isn't drawn yet. */
fun problemText(p: Payload.Problem?) = when (p) {
    null, Payload.Problem.EMPTY -> R.string.p_empty
    Payload.Problem.TOO_LONG -> R.string.p_too_long
    Payload.Problem.BAD_LINK -> R.string.p_bad_link
    Payload.Problem.WIFI_PASSWORD -> R.string.p_wifi_password
    Payload.Problem.BAD_NUMBER -> R.string.p_bad_number
    Payload.Problem.BAD_EMAIL -> R.string.p_bad_email
}
