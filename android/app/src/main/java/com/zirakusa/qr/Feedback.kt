package com.zirakusa.qr

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** «Vibrar al leer» and «Reproducir sonido»: a short buzz and a short beep (no permission, no sound file). */
class Feedback(private val context: Context) {
    private var tone: ToneGenerator? = null

    fun buzz() {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        else v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun beep() {
        val t = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()?.also { tone = it } ?: return
        t.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    fun release() { tone?.release(); tone = null }
}
