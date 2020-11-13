package net.folivo.android.smsGatewayServer

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import java.util.*

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // Get SMS map from Intent
        if (context == null || intent == null || intent.action == null) {
            return
        }

        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = smsMessages.joinToString(separator = "") { it.displayMessageBody }

        putSmsToDatabase(context, body, smsMessages.first().displayOriginatingAddress)
    }

    private fun putSmsToDatabase(context: Context, body: String, address: String): Uri? {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues()
        contentValues.put(Telephony.Sms.BODY, body)
        contentValues.put(Telephony.Sms.DATE, Calendar.getInstance().timeInMillis.toString())
        contentValues.put(Telephony.Sms.ADDRESS, address)
        contentValues.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        return contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
    }

}