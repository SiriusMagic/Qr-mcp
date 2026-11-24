package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.LinkCheck
import com.zirakusa.qr.data.Warning
import com.zirakusa.qr.ui.theme.QrColors

/** The colour of warnings (links). */
val Amber = Color(0xFFF5B83D)

/** The real site, big, and what to look at before opening it. */
@Composable fun LinkCard(url: String) {
    val check = LinkCheck.check(url) ?: return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (check.warnings.isEmpty()) QrColors.Card else Amber.copy(alpha = 0.16f))
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.real_site), color = QrColors.Muted, fontSize = 13.sp)
        Text(check.host, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (check.warnings.isEmpty()) Text(stringResource(R.string.no_warnings), color = QrColors.Muted, fontSize = 14.sp)
        check.warnings.forEach { w ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Amber)
                Text(stringResource(warningText(w)), Modifier.padding(start = 8.dp), fontSize = 15.sp)
            }
        }
    }
}

fun warningText(w: Warning) = when (w) {
    Warning.NOT_HTTPS -> R.string.w_not_https; Warning.IP_ADDRESS -> R.string.w_ip; Warning.LOOKALIKE -> R.string.w_lookalike
    Warning.SHORTENER -> R.string.w_shortener; Warning.USER_INFO -> R.string.w_user_info; Warning.ODD_PORT -> R.string.w_port
    Warning.APP_DOWNLOAD -> R.string.w_apk
}
