package com.zirakusa.qr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.zirakusa.qr.data.Detail
import com.zirakusa.qr.data.SharedContact
import com.zirakusa.qr.data.VCard

/** A code picture ready to show, save or share: [readable] says it was read back with exactly its text. */
class Picture(val image: ImageBitmap, val png: ByteArray, val readable: Boolean)

/**
 * «Compartir un contacto»: the contact the person picked, the details they tick (with a preview of exactly what goes in
 * the code), then the code itself, «Arbolito» or classic. Nothing is saved in the history and nothing leaves the phone
 * here; only «Compartir» (the person's tap and their choice of app) or «Guardar imagen» do.
 */
class ContactShare {
    enum class Step { CHOOSE, CODE }

    var contact by mutableStateOf<SharedContact?>(null); private set
    var chosen by mutableStateOf(emptySet<Int>()); private set
    var step by mutableStateOf(Step.CHOOSE)
    /** «Arbolito» (true) or «Clásico». */
    var artistic by mutableStateOf(true); private set
    /** The picture on screen; null while it is being drawn and checked. */
    var picture by mutableStateOf<Picture?>(null)
    /** The «Arbolito» card couldn't be read back, so the classic code is shown instead (and the screen says so). */
    var fellBack by mutableStateOf(false)

    fun load(c: SharedContact) { contact = c; chosen = c.defaults(); step = Step.CHOOSE; artistic = true; picture = null; fellBack = false }
    fun clear() { contact = null; chosen = emptySet(); picture = null; fellBack = false; step = Step.CHOOSE }

    fun toggle(i: Int) { chosen = if (i in chosen) chosen - i else chosen + i; picture = null; fellBack = false }

    val details: List<Detail> get() = contact?.all.orEmpty()
    val selected: List<Detail> get() = details.filterIndexed { i, _ -> i in chosen }
    val vcard: String get() = VCard.build(selected)
    val problem: VCard.Problem? get() = VCard.problem(selected)
    /** Many details make a dense code that other phones read less easily: the screen suggests leaving something out. */
    val dense: Boolean get() = vcard.toByteArray(Charsets.UTF_8).size > VCard.COMFORTABLE_BYTES
    /** The name shown under the «Arbolito» code. */
    val title: String get() = selected.firstOrNull { it.kind == Detail.Kind.NAME }?.value ?: selected.firstOrNull()?.value.orEmpty()

    fun generate() { if (problem == null) { step = Step.CODE; picture = null; fellBack = false } }
    fun changeStyle(art: Boolean) { if (art != artistic) { artistic = art; picture = null; fellBack = false } }
    fun back() { step = Step.CHOOSE; picture = null; fellBack = false }

    /**
     * The drawn pictures arrive here. An «Arbolito» card that wasn't read back exactly is replaced by the [classic] one,
     * so what is shown, saved or shared always works.
     */
    fun drawn(art: Picture?, classic: Picture) {
        if (artistic && art != null && art.readable) { picture = art; fellBack = false }
        else { picture = classic; fellBack = artistic }
    }
}
