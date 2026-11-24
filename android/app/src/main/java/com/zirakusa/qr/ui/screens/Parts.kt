package com.zirakusa.qr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.qr.R
import com.zirakusa.qr.data.BarFormat
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Entry
import com.zirakusa.qr.data.Type
import com.zirakusa.qr.typeName
import androidx.compose.material.icons.outlined.ViewWeek
import com.zirakusa.qr.ui.theme.QrColors

@Composable fun PageTop(title: String, onBack: () -> Unit) = Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
    RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), size = 52.dp, onClick = onBack)
    Text(title, Modifier.padding(start = 16.dp), fontSize = 22.sp)
}

@Composable fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = QrColors.Chip)

/** A QR code drawn black on white (always, so every reader sees it), with rounded paper around. */
@Composable fun QrImage(text: String, modifier: Modifier = Modifier, side: Dp = 240.dp) {
    val grid = remember(text) { runCatching { Codes.encode(text) }.getOrNull() } ?: return
    val label = stringResource(R.string.qr_picture)
    Box(modifier.size(side).clip(RoundedCornerShape(20.dp)).background(Color.White).semantics { contentDescription = label }) {
        Canvas(Modifier.size(side)) {
            val cell = size.width / grid.size
            for (y in 0 until grid.size) for (x in 0 until grid.size) if (grid[x, y])
                drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** A bar code drawn black on white, with its text under it. Nothing when ZXing can't draw it. */
@Composable fun BarImage(text: String, format: BarFormat, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val bars = remember(text, format) { Codes.bars(text, format) } ?: return
    val label = stringResource(R.string.barcode)
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(vertical = 12.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val cell = size.width / bars.size
            for (x in 0 until bars.size) if (bars[x]) drawRect(Color.Black, Offset(x * cell, 0f), Size(cell + 0.5f, size.height))
        }
        Text(text, Modifier.padding(top = 6.dp), color = Color.Black, fontSize = 16.sp, letterSpacing = 2.sp)
    }
}

/** The icon of each kind of code. */
fun typeIcon(t: Type): ImageVector = when (t) {
    Type.TEXT -> Icons.AutoMirrored.Outlined.Notes; Type.LINK -> Icons.Outlined.Link; Type.WIFI -> Icons.Outlined.Wifi
    Type.CONTACT -> Icons.Outlined.Person; Type.PHONE -> Icons.Outlined.Call; Type.EMAIL -> Icons.Outlined.Email
    Type.SMS -> Icons.Outlined.Sms; Type.EVENT -> Icons.Outlined.Event; Type.PLACE -> Icons.Outlined.Place
}

/** A bar code (product, book…) holding plain text: named and drawn as one. */
fun Entry.isBarcode() = !isQr && content.kind == Type.TEXT
fun entryName(e: Entry) = if (e.isBarcode()) R.string.barcode else typeName(e.content.kind)
fun entryIcon(e: Entry): ImageVector = if (e.isBarcode()) Icons.Outlined.ViewWeek else typeIcon(e.content.kind)

/** A round tinted icon for a kind of code. */
@Composable fun TypeBadge(t: Type, size: Dp = 44.dp) = Badge(typeIcon(t), size)

@Composable fun Badge(icon: ImageVector, size: Dp = 44.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(QrColors.Accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = QrColors.AccentText)
    }
