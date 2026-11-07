package com.rpginsurance.desk

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rpginsurance.brief.Phones
import com.rpginsurance.brief.Schema

/**
 * Everything here only *opens* another app. The user places the call or sends the WhatsApp
 * message themselves; nothing is ever sent automatically.
 */
object ContactActions {
    fun dial(context: Context, mobile: String) {
        val number = Phones.dialable(mobile) ?: return toast(context, "That mobile number looks invalid")
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")), "No phone app found")
    }

    fun whatsapp(context: Context, mobile: String) {
        val number = Phones.whatsapp(mobile) ?: return toast(context, "That mobile number looks invalid")
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")), "WhatsApp isn't installed")
    }

    fun openInAirtable(context: Context, tableId: String, recordId: String) {
        val uri = Uri.parse("https://airtable.com/${Schema.BASE_ID}/$tableId/$recordId")
        start(context, Intent(Intent.ACTION_VIEW, uri), "No browser found")
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(send, "Share brief"), "No app to share with")
    }

    private fun start(context: Context, intent: Intent, notFound: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(context, notFound)
        }
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
