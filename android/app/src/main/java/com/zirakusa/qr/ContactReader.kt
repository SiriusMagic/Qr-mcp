package com.zirakusa.qr

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as K
import com.zirakusa.qr.data.Detail
import com.zirakusa.qr.data.SharedContact

/**
 * Reads the one contact the person picked in Android's contact picker: its name, phones, emails, company, job title,
 * address, web pages and note. Only that contact, only on the phone, only to fill «Compartir un contacto»; nothing is
 * kept or sent anywhere. Throws [SecurityException] when Android asks for the contacts permission first.
 */
object ContactReader {
    fun read(context: Context, picked: Uri): SharedContact? {
        val entity = Uri.withAppendedPath(picked, ContactsContract.Contacts.Entity.CONTENT_DIRECTORY)
        val cols = arrayOf(ContactsContract.Contacts.Entity.MIMETYPE, ContactsContract.Contacts.Entity.DATA1, ContactsContract.Contacts.DISPLAY_NAME)
        var name = ""
        val details = ArrayList<Detail>()
        context.contentResolver.query(entity, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (name.isBlank()) name = c.getString(2).orEmpty()
                val value = c.getString(1)?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: continue
                val kind = when (c.getString(0)) {
                    K.Phone.CONTENT_ITEM_TYPE -> Detail.Kind.PHONE
                    K.Email.CONTENT_ITEM_TYPE -> Detail.Kind.EMAIL
                    K.Organization.CONTENT_ITEM_TYPE -> Detail.Kind.ORG
                    K.StructuredPostal.CONTENT_ITEM_TYPE -> Detail.Kind.ADDRESS
                    K.Website.CONTENT_ITEM_TYPE -> Detail.Kind.URL
                    K.Note.CONTENT_ITEM_TYPE -> Detail.Kind.NOTE
                    else -> null
                } ?: continue
                details += Detail(kind, if (kind == Detail.Kind.ADDRESS) value.replace('\n', ' ') else value)
            }
        } ?: return null
        // The job title lives in the organization row's DATA4.
        context.contentResolver.query(entity, arrayOf(ContactsContract.Contacts.Entity.MIMETYPE, K.Organization.TITLE), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(0) == K.Organization.CONTENT_ITEM_TYPE) c.getString(1)?.takeIf { it.isNotBlank() }?.let { details += Detail(Detail.Kind.TITLE, it.trim()) }
        }
        return SharedContact(name, details.distinct()).takeIf { it.all.isNotEmpty() }
    }
}
