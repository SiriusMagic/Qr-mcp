package com.zirakusa.qr.data

/**
 * «Escaneo continuo»: the camera sees the same code in every frame. A code counts once while it stays in view; it
 * counts again only after it was out of sight for [gapMs] (or another code was read in between).
 */
class ScanGate(private val gapMs: Long = GAP_MS) {
    private var last: String? = null
    private var seen = Long.MIN_VALUE

    /** True when [text], seen at [now], is a new read. */
    fun accept(text: String, now: Long): Boolean {
        val again = text == last && now - seen in 0..gapMs
        last = text
        seen = now
        return !again
    }

    fun reset() { last = null; seen = Long.MIN_VALUE }

    companion object { const val GAP_MS = 2_500L }
}
