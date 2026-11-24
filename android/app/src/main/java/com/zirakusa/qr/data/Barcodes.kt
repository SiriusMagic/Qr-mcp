package com.zirakusa.qr.data

/** The bar codes «Crear» makes. [format] is ZXing's name, as «Historial» keeps it. */
enum class BarFormat(val format: String, val label: String) {
    EAN_13("EAN_13", "EAN-13"), EAN_8("EAN_8", "EAN-8"), UPC_A("UPC_A", "UPC-A"), CODE_128("CODE_128", "Code 128"), CODE_39("CODE_39", "Code 39");
    companion object { fun from(format: String?) = entries.firstOrNull { it.format == format } }
}

/**
 * Checking what goes into a bar code, before ZXing draws it. Pure. EAN and UPC take digits only: without the last
 * (check) digit it is added; with it, it must be right. Code 128 takes printable ASCII; Code 39 its 43 characters.
 */
object Barcodes {
    enum class Problem { EMPTY, DIGITS_ONLY, LENGTH, CHECKSUM, CHARACTERS, TOO_LONG }

    const val MAX_128 = 80
    const val MAX_39 = 43
    private const val CODE_39_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ -.$/+%"

    /** How many digits the code has, check digit included. */
    fun length(f: BarFormat) = when (f) { BarFormat.EAN_13 -> 13; BarFormat.EAN_8 -> 8; BarFormat.UPC_A -> 12; else -> 0 }

    /** The text as it goes into the code: spaces out of numbers, check digit added, Code 39 in capitals. */
    fun normalize(f: BarFormat, input: String): String = when (f) {
        BarFormat.EAN_13, BarFormat.EAN_8, BarFormat.UPC_A -> {
            val d = input.filterNot { it == ' ' || it == '-' }
            if (d.length == length(f) - 1 && d.all { it in '0'..'9' }) d + checkDigit(d) else d
        }
        BarFormat.CODE_128 -> input
        BarFormat.CODE_39 -> input.uppercase()
    }

    /** Why [input] can't be a [f] code yet, or null when it can. */
    fun problem(f: BarFormat, input: String): Problem? {
        if (input.isBlank()) return Problem.EMPTY
        val s = normalize(f, input)
        return when (f) {
            BarFormat.EAN_13, BarFormat.EAN_8, BarFormat.UPC_A -> when {
                !s.all { it in '0'..'9' } -> Problem.DIGITS_ONLY
                s.length != length(f) -> Problem.LENGTH
                checkDigit(s.dropLast(1)) != s.last() - '0' -> Problem.CHECKSUM
                else -> null
            }
            BarFormat.CODE_128 -> when {
                s.length > MAX_128 -> Problem.TOO_LONG
                !s.all { it.code in 32..126 } -> Problem.CHARACTERS
                else -> null
            }
            BarFormat.CODE_39 -> when {
                s.length > MAX_39 -> Problem.TOO_LONG
                !s.all { it in CODE_39_CHARS } -> Problem.CHARACTERS
                else -> null
            }
        }
    }

    /** The GS1 check digit (EAN, UPC): from the right, digits weigh 3, 1, 3, 1… */
    fun checkDigit(digits: String): Int {
        val sum = digits.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10
    }
}
