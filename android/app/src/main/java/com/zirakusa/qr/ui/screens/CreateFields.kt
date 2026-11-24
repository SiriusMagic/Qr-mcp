package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.zirakusa.qr.R
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Barcodes
import com.zirakusa.qr.data.Payload
import com.zirakusa.qr.data.Security
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.ui.Form
import com.zirakusa.qr.ui.QrState
import com.zirakusa.qr.ui.theme.QrColors

/** The fields of the kind of QR code being made. */
@Composable fun Fields(state: QrState) {
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
        Type.EVENT -> {
            Field(R.string.f_title, f.title) { set(f.copy(title = it.take(200))) }
            Field(R.string.f_start, f.start, hint = R.string.f_when_hint) { set(f.copy(start = it.take(40))) }
            Field(R.string.f_end_optional, f.end, hint = R.string.f_when_hint) { set(f.copy(end = it.take(40))) }
            Field(R.string.f_location, f.place) { set(f.copy(place = it.take(200))) }
        }
    }
}

/** The field of the bar code being made: digits for EAN and UPC. */
@Composable fun BarField(state: QrState, format: BarFormat) {
    val digits = format == BarFormat.EAN_13 || format == BarFormat.EAN_8 || format == BarFormat.UPC_A
    Field(if (digits) R.string.f_number else R.string.f_text, state.form.code, keyboard = if (digits) KeyboardType.Number else KeyboardType.Ascii,
        hint = when (format) { BarFormat.EAN_13 -> R.string.bar_ean13_hint; BarFormat.EAN_8 -> R.string.bar_ean8_hint; BarFormat.UPC_A -> R.string.bar_upc_hint
            BarFormat.CODE_128 -> R.string.bar_128_hint; BarFormat.CODE_39 -> R.string.bar_39_hint }) {
        state.form = state.form.copy(code = it.take(Barcodes.MAX_128))
    }
}

@Composable private fun Field(label: Int, value: String, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false,
    hint: Int? = null, onChange: (String) -> Unit) =
    OutlinedTextField(value, { onChange(it.take(Payload.MAX)) }, Modifier.fillMaxWidth().background(QrColors.Card), label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
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
    Payload.Problem.BAD_DATE -> R.string.p_bad_date
    Payload.Problem.BAD_END -> R.string.p_bad_end
}

/** Why the bar code isn't drawn yet. */
fun barProblemText(p: Barcodes.Problem?, format: BarFormat) = when (p) {
    null, Barcodes.Problem.EMPTY -> R.string.p_empty
    Barcodes.Problem.DIGITS_ONLY -> R.string.p_digits
    Barcodes.Problem.LENGTH -> when (format) { BarFormat.EAN_13 -> R.string.p_len_13; BarFormat.EAN_8 -> R.string.p_len_8; else -> R.string.p_len_12 }
    Barcodes.Problem.CHECKSUM -> R.string.p_checksum
    Barcodes.Problem.CHARACTERS -> if (format == BarFormat.CODE_39) R.string.p_chars_39 else R.string.p_chars_128
    Barcodes.Problem.TOO_LONG -> R.string.p_too_long
}
