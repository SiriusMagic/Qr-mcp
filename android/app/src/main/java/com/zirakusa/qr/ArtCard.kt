package com.zirakusa.qr

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.zirakusa.qr.data.ArtLayout
import com.zirakusa.qr.data.Box
import com.zirakusa.qr.data.Codes
import com.zirakusa.qr.data.Grid
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap

/** A finished code picture: its pixels, its PNG, and whether it was read back with exactly the text it carries. */
class CardPicture(val bitmap: Bitmap, val png: ByteArray, val readable: Boolean)

/**
 * The contact codes as pictures, drawn on the phone: the classic black‑on‑white QR, or the «Arbolito» card, an original
 * little 3D tree (cones lit from the left, ornaments, a star) above the QR on its own white panel. The tree never covers
 * the code ([ArtLayout]); and every picture is read back with ZXing before it is shown ([verify]).
 */
object ArtCard {
    /** The classic code: black on white, 4 modules of light margin, 12 px a module. */
    fun classic(text: String): CardPicture {
        val grid = Codes.encode(text, margin = 4)
        val scale = 12
        val bmp = createBitmap(grid.size * scale, grid.size * scale)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        drawCode(c, grid, Box(0, 0, grid.size * scale, grid.size * scale), scale)
        return CardPicture(bmp, png(bmp), verify(bmp, text))
    }

    /** The «Arbolito» card for [text] (the vCard), with [name] and [hint] («Escanéame para guardar el contacto») under it. */
    fun tree(text: String, name: String, hint: String): CardPicture {
        val grid = Codes.encode(text, margin = 4)
        val bmp = createBitmap(ArtLayout.WIDTH, ArtLayout.HEIGHT)
        val c = Canvas(bmp)
        background(c)
        drawTree(c, ArtLayout.tree)
        panel(c, ArtLayout.panel)
        val (box, scale) = ArtLayout.code(grid.size)
        drawCode(c, grid, box, scale)
        caption(c, name, hint, ArtLayout.caption)
        return CardPicture(bmp, png(bmp), verify(bmp, text))
    }

    /**
     * Read back as another phone would: the whole picture and a version at half size (a code seen from further away),
     * both must give exactly [text]. If not, the screen shows the classic code instead.
     */
    fun verify(bmp: Bitmap, text: String): Boolean {
        fun reads(b: Bitmap): Boolean {
            val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            return Codes.readPixels(px, b.width, b.height, withBarcodes = false)?.let { it.format == "QR_CODE" && it.text == text } == true
        }
        if (!reads(bmp)) return false
        val half = bmp.scale(bmp.width / 2, bmp.height / 2)
        return reads(half).also { if (half !== bmp) half.recycle() }
    }

    private fun png(bmp: Bitmap) = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** Sharp squares: each module a whole number of pixels, no smoothing, pure black on white. */
    private fun drawCode(c: Canvas, grid: Grid, box: Box, scale: Int) {
        val white = Paint().apply { color = Color.WHITE; isAntiAlias = false }
        val black = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        c.drawRect(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), white)
        for (y in 0 until grid.size) for (x in 0 until grid.size) if (grid[x, y]) {
            val l = (box.left + x * scale).toFloat(); val t = (box.top + y * scale).toFloat()
            c.drawRect(l, t, l + scale, t + scale, black)
        }
    }

    // ---- The card around the code ----

    private fun background(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, ArtLayout.HEIGHT.toFloat(), intArrayOf(0xFF14213D.toInt(), 0xFF1F4E5F.toInt(), 0xFF2A6F6A.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, ArtLayout.WIDTH.toFloat(), ArtLayout.HEIGHT.toFloat(), p)
        // Soft snow: the same dots every time (no randomness), kept off the code's panel.
        val snow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        var seed = 7L
        repeat(90) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val x = (seed % ArtLayout.WIDTH).toFloat()
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            val y = (seed % ArtLayout.HEIGHT).toFloat()
            val r = 2f + (seed % 5)
            val near = x > ArtLayout.panel.left - 12 && x < ArtLayout.panel.right + 12 && y > ArtLayout.panel.top - 12 && y < ArtLayout.panel.bottom + 12
            if (!near) { snow.alpha = 60 + (seed % 120).toInt(); c.drawCircle(x, y, r, snow) }
        }
    }

    /** The white panel, with a soft shadow outside it (never on the code). */
    private fun panel(c: Canvas, b: Box) {
        val r = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(RectF(r.left + 6, r.top + 16, r.right + 6, r.bottom + 16), 44f, 44f, shadow)
        c.drawRoundRect(r, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun caption(c: Canvas, name: String, hint: String, b: Box) {
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 50f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 32f; textAlign = Paint.Align.CENTER }
        val cx = (b.left + b.right) / 2f
        c.drawText(TextUtils.ellipsize(name, title, b.width.toFloat(), TextUtils.TruncateAt.END).toString(), cx, b.top + 52f, title)
        c.drawText(TextUtils.ellipsize(hint, small, b.width.toFloat(), TextUtils.TruncateAt.END).toString(), cx, b.top + 104f, small)
    }

    // ---- The little tree: three cones, each with a lit left side and a shaded right side ----

    private fun drawTree(c: Canvas, b: Box) {
        val cx = (b.left + b.right) / 2f
        val ground = b.bottom - 50f
        // The snowy ground and the tree's shadow on it.
        c.drawOval(RectF(cx - 300, ground - 34, cx + 300, ground + 34), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, ground, 300f, intArrayOf(0xFFF4FAFC.toInt(), 0xFFCFE3EA.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawOval(RectF(cx - 150, ground - 14, cx + 190, ground + 18), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40203040; maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        })
        // Trunk: a short cylinder, lighter on the left.
        val trunk = RectF(cx - 30, ground - 70, cx + 30, ground + 4)
        c.drawRoundRect(trunk, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(trunk.left, 0f, trunk.right, 0f, intArrayOf(0xFFB07A4E.toInt(), 0xFF7A4A2A.toInt(), 0xFF4E2E1A.toInt()), null, Shader.TileMode.CLAMP)
        })
        // Three tiers, bottom to top: base y, half width, height.
        val tiers = listOf(Triple(ground - 60, 230f, 190f), Triple(ground - 185, 180f, 170f), Triple(ground - 295, 128f, 150f))
        tiers.forEach { (base, half, height) -> cone(c, cx, base, half, height) }
        // Garlands: golden arcs across each tier.
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFF2C94C.toInt(); strokeCap = Paint.Cap.ROUND }
        tiers.forEach { (base, half, height) ->
            val y = base - height * 0.32f; val w = half * 0.72f
            c.drawPath(Path().apply { moveTo(cx - w, y - 18); quadTo(cx, y + 26, cx + w, y - 30) }, gold)
        }
        // Ornaments: round baubles with a highlight (lit from the upper left, like the cones).
        listOf(Triple(-140f, ground - 92, 0xFFE63946), Triple(95f, ground - 110, 0xFF4EA8DE), Triple(-20f, ground - 160, 0xFFF4A261),
            Triple(150f, ground - 75, 0xFFF2C94C), Triple(-95f, ground - 225, 0xFF9B5DE5), Triple(70f, ground - 245, 0xFFE63946),
            Triple(-40f, ground - 330, 0xFF4EA8DE), Triple(35f, ground - 380, 0xFFF2C94C)).forEach { (dx, y, color) -> bauble(c, cx + dx, y, 17f, color.toInt()) }
        // The star on top, with a soft glow.
        val top = tiers.last().let { it.first - it.third }
        c.drawCircle(cx, top - 6, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, top - 6, 46f, intArrayOf(0x88FFE08A.toInt(), 0x00FFE08A), null, Shader.TileMode.CLAMP)
        })
        c.drawPath(star(cx, top - 6, 30f, 13f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - 30, top - 36, cx + 30, top + 24, intArrayOf(0xFFFFF3B0.toInt(), 0xFFF2C94C.toInt(), 0xFFD99A1E.toInt()), null, Shader.TileMode.CLAMP)
        })
    }

    /** One cone seen from the front: lit left half, shaded right half, a rounded (3D) bottom edge and a rim of light. */
    private fun cone(c: Canvas, cx: Float, base: Float, half: Float, height: Float) {
        val tip = base - height
        val left = Path().apply { moveTo(cx, tip); lineTo(cx - half, base); quadTo(cx - half * 0.5f, base + 26, cx, base + 30); close() }
        val right = Path().apply { moveTo(cx, tip); lineTo(cx + half, base); quadTo(cx + half * 0.5f, base + 26, cx, base + 30); close() }
        c.drawPath(left, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - half, tip, cx, base, intArrayOf(0xFF6FD08C.toInt(), 0xFF3FA66B.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawPath(right, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, tip, cx + half, base, intArrayOf(0xFF2F8A57.toInt(), 0xFF1E5E3C.toInt()), null, Shader.TileMode.CLAMP)
        })
        // Snow on the bottom edge, thicker where the light falls.
        c.drawPath(Path().apply { moveTo(cx - half, base); quadTo(cx - half * 0.5f, base + 26, cx, base + 30); quadTo(cx + half * 0.5f, base + 26, cx + half, base) },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(cx - half, 0f, cx + half, 0f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFDCEBF1.toInt()), null, Shader.TileMode.CLAMP) })
    }

    private fun bauble(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        c.drawCircle(x + 2, y + 4, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x40000000 })
        c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x - r * 0.4f, y - r * 0.4f, r * 1.4f, intArrayOf(Color.WHITE, color, darker(color)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun darker(color: Int) = Color.rgb(Color.red(color) * 6 / 10, Color.green(color) * 6 / 10, Color.blue(color) * 6 / 10)

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
