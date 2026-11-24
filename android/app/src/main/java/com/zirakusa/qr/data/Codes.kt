package com.zirakusa.qr.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** A square grid of dark (true) and light modules, with its quiet margin. */
class Grid(val size: Int, private val cells: BooleanArray) {
    operator fun get(x: Int, y: Int) = cells[y * size + x]
}

/** A bar code as one row of dark (true) and light modules, with its quiet margin. */
class Bars(private val modules: BooleanArray) {
    val size get() = modules.size
    operator fun get(x: Int) = modules[x]
}

/** What was read: the text and its format («QR_CODE», «EAN_13»…). */
data class Read(val text: String, val format: String)

/**
 * Making and reading codes with ZXing, on the phone (no internet, no Google services). Pure Java underneath, so the
 * tests make a code and read it back.
 */
object Codes {
    private val barcodes = listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.CODABAR)
    private val twoD = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417)

    /** A QR code for [text] (UTF‑8, medium error correction, [margin] light modules around). */
    fun encode(text: String, margin: Int = 2): Grid {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to margin,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        return Grid(m.width, BooleanArray(m.width * m.height) { m.get(it % m.width, it / m.width) })
    }

    /** A bar code for [text], already checked with [Barcodes.problem]; null when ZXing still refuses it. */
    fun bars(text: String, format: BarFormat): Bars? = runCatching {
        val m = MultiFormatWriter().encode(text, BarcodeFormat.valueOf(format.format), 0, 1, mapOf(EncodeHintType.MARGIN to 10))
        Bars(BooleanArray(m.width) { m.get(it, 0) })
    }.getOrNull()

    /** The picture to share or save: the QR code, or the bar code for the formats «Crear» makes; null otherwise. */
    fun picture(text: String, format: String): ByteArray? {
        if (format == Entry.QR) return runCatching { png(encode(text, margin = 4)) }.getOrNull()
        val f = BarFormat.from(format) ?: return null
        return bars(text, f)?.let { png(it) }
    }

    /** A camera frame (its brightness plane): the code in it, also turned a quarter for bar codes held sideways. */
    fun readFrame(y: ByteArray, width: Int, height: Int, rowStride: Int = width, withBarcodes: Boolean = true): Read? {
        val plane = if (rowStride == width) y else ByteArray(width * height).also { out ->
            for (r in 0 until height) System.arraycopy(y, r * rowStride, out, r * width, width)
        }
        return read(PlanarYUVLuminanceSource(plane, width, height, 0, 0, width, height, false), withBarcodes, hard = false)
            ?: if (withBarcodes) read(PlanarYUVLuminanceSource(rotate(plane, width, height), height, width, 0, 0, height, width, false),
                withBarcodes = true, hard = false) else null
    }

    /** A picture (ARGB pixels), trying harder: it is read only once. */
    fun readPixels(argb: IntArray, width: Int, height: Int, withBarcodes: Boolean = true): Read? =
        read(RGBLuminanceSource(width, height, argb), withBarcodes, hard = true)

    private fun read(source: LuminanceSource, withBarcodes: Boolean, hard: Boolean): Read? {
        val hints = buildMap<DecodeHintType, Any> {
            put(DecodeHintType.POSSIBLE_FORMATS, if (withBarcodes) twoD + barcodes else twoD)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            if (hard) put(DecodeHintType.TRY_HARDER, true)
        }
        val reader = MultiFormatReader().apply { setHints(hints) }
        for (s in listOf(source, source.invert())) {
            try { return reader.decodeWithState(BinaryBitmap(HybridBinarizer(s))).let { Read(it.text, it.barcodeFormat.name) } }
            catch (_: NotFoundException) { } catch (_: ReaderException) { } finally { reader.reset() }
        }
        return null
    }

    /** The plane turned a quarter clockwise. */
    internal fun rotate(plane: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(plane.size)
        for (y in 0 until height) for (x in 0 until width) out[x * height + (height - 1 - y)] = plane[y * width + x]
        return out
    }

    /** The grid as a black‑on‑white PNG, [scale] pixels a module: for «Compartir» and for the AI. */
    fun png(grid: Grid, scale: Int = 10): ByteArray = png(grid.size * scale, grid.size * scale) { x, y -> grid[x / scale, y / scale] }

    /** The bars as a black‑on‑white PNG, [scale] pixels a module and [height] pixels high. */
    fun png(bars: Bars, scale: Int = 4, height: Int = 240): ByteArray = png(bars.size * scale, height) { x, _ -> bars[x / scale] }

    private fun png(width: Int, height: Int, dark: (Int, Int) -> Boolean): ByteArray {
        val raw = ByteArray((width + 1) * height)
        for (y in 0 until height) {
            val row = y * (width + 1)
            raw[row] = 0 // no filter
            for (x in 0 until width) raw[row + 1 + x] = if (dark(x, y)) 0 else 0xFF.toByte()
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        chunk(out, "IHDR", ByteArrayOutputStream().apply { int(width); int(height); write(8); write(0); write(0); write(0); write(0) }.toByteArray())
        chunk(out, "IDAT", deflate(raw))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(data); finish() }
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val t = type.toByteArray(Charsets.US_ASCII)
        out.int(data.size); out.write(t); out.write(data)
        out.int(CRC32().apply { update(t); update(data) }.value.toInt())
    }

    private fun ByteArrayOutputStream.int(v: Int) { write(v ushr 24); write(v ushr 16 and 0xFF); write(v ushr 8 and 0xFF); write(v and 0xFF) }
}
