package net.folivo.android.smsGatewayServer

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import io.ktor.http.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


class MessageManager(private val applicationContext: Context) {

    private val logTag = MessageManager::class.java.simpleName


    private val inboxURI: Uri = Telephony.Sms.Inbox.CONTENT_URI
//    private val outboxURI: Uri = Telephony.Sms.Outbox.CONTENT_URI
//    private val sentURI: Uri = Telephony.Sms.Sent.CONTENT_URI
    private val allURI: Uri = Telephony.Sms.CONTENT_URI
    private val messageProjection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE)
    private val contentResolver = applicationContext.contentResolver

    private val sentAction = "sent"

    @Throws(IllegalArgumentException::class)
    suspend fun sendSms(
        recipientPhoneNumber: String,
        message: String,
    ): Pair<HttpStatusCode, String> {

        val sendStatusChannel = Channel<Int>(Channel.UNLIMITED)

        applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                sendStatusChannel.sendBlocking(resultCode)
            }
        }, IntentFilter(sentAction))

        val smsManager: SmsManager = SmsManager.getDefault()
        val multipartMessageArray = smsManager.divideMessage(message)
        val resultPair: Pair<HttpStatusCode, String>


        // Send simple text message
        if (multipartMessageArray.count() <= 1) {
            val sentIntent = PendingIntent.getBroadcast(applicationContext,
                Random().nextInt(),
                Intent(sentAction),
                PendingIntent.FLAG_UPDATE_CURRENT)

            smsManager.sendTextMessage(
                recipientPhoneNumber,
                null,
                message,
                sentIntent,
                null
            )
            Log.i(
                logTag,
                "Request to send simple text message: [message: $message , phone number: $recipientPhoneNumber]"
            )

            resultPair = convertSmsResultCode(sendStatusChannel.receive())


            // Send multipart text message
        } else {

            val sentIntents = ArrayList<PendingIntent>()

            for (i in 0 until multipartMessageArray.count()) {
                sentIntents.add(PendingIntent.getBroadcast(applicationContext, i, Intent(sentAction), 0))
            }
            smsManager.sendMultipartTextMessage(
                recipientPhoneNumber,
                null,
                multipartMessageArray,
                sentIntents,
                null
            )
            Log.i(
                logTag,
                "Request to send multipart text message: [message: $multipartMessageArray , phone number: $recipientPhoneNumber]"
            )

            val resultCodes = mutableListOf<Int>()
            for (i in 0 until multipartMessageArray.count()) {
                resultCodes.add(sendStatusChannel.receive())
            }

            val mergedResultCode = resultCodes.find{it != Activity.RESULT_OK} ?: Activity.RESULT_OK
            resultPair = Pair(convertSmsResultCode(mergedResultCode).first, resultCodes.map{convertSmsResultCode(it)}.joinToString(prefix = "[", postfix = "]"))

        }
        if (resultPair.first.isSuccess()) {
            Log.i(logTag, "Message has been successfully sent")
        } else {
            Log.i(logTag, "Message has not been successfully sent")
        }
        return resultPair
    }

    @Throws(SQLiteCantOpenDatabaseException::class, IllegalArgumentException::class, SQLiteException::class)
    fun getInboxMessages(): ArrayList<Message> {
        val cursor = contentResolver.query(
            inboxURI,
            messageProjection,
            null,
            null,
            Telephony.Sms._ID + " ASC"
        )
            ?: throw (SQLiteCantOpenDatabaseException("Provider's table not found"))
        return getArrayListFromTable(cursor)
    }

    @Throws(SQLiteCantOpenDatabaseException::class, IllegalArgumentException::class, SQLiteException::class)
    fun getInboxMessagesFromIdUp(requestId: String): ArrayList<Message> {
        val cursor = contentResolver.query(
            inboxURI,
            messageProjection,
            Telephony.Sms._ID + " > " + requestId,
            null,
            Telephony.Sms._ID + " ASC"
        )
            ?: throw (SQLiteCantOpenDatabaseException("Provider's table not found"))

        return getArrayListFromTable(cursor)
    }

    @Throws(IllegalArgumentException::class)
    private fun getArrayListFromTable(cursor: Cursor?): ArrayList<Message> {
        val smsList = ArrayList<Message>()
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                val number =
                    it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                val dateReceived =
                    Instant.ofEpochMilli(it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE)))
                        .toString()
                val dateSent =
                    Instant.ofEpochMilli(it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)))
                        .toString()
                val type = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                smsList.add(Message(id, number, body, dateReceived, dateSent, type))
            }
        }
        return smsList
    }

    private fun convertSmsResultCode(resultCode: Int): Pair<HttpStatusCode, String> {
        return when (resultCode) {
            Activity.RESULT_OK -> Pair(HttpStatusCode.OK, "Transmission successful")
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Pair(
                HttpStatusCode.InternalServerError,
                "Transmission failed"
            )
            SmsManager.RESULT_ERROR_RADIO_OFF -> Pair(
                HttpStatusCode.InternalServerError,
                "Radio off"
            )
            SmsManager.RESULT_ERROR_NULL_PDU -> Pair(
                HttpStatusCode.InternalServerError,
                "No PDU defined"
            )
            SmsManager.RESULT_ERROR_NO_SERVICE -> Pair(
                HttpStatusCode.InternalServerError,
                "No service"
            )
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> Pair(
                HttpStatusCode.InternalServerError,
                "Failed because we reached the sending queue limit"
            )
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> Pair(
                HttpStatusCode.InternalServerError,
                "Failed because user denied the sending of this short code"
            )
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> Pair(
                HttpStatusCode.InternalServerError,
                "Failed because the user has denied this app ever send premium short codes"
            )
            else -> Pair(
                HttpStatusCode.InternalServerError,
                "Error"
            )
        }
    }

    @Throws(SQLiteCantOpenDatabaseException::class, IllegalArgumentException::class, SQLiteException::class)
    fun getNextBatch(): Long {
        val cursor = contentResolver.query(
            inboxURI,
            messageProjection,
            Telephony.Sms._ID,
            null,
            Telephony.Sms._ID + " DESC"
        )
            ?: throw (SQLiteCantOpenDatabaseException("Provider's table not found"))
        if (cursor.moveToFirst()){
            cursor.use {
                return it.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
            }
        }
        return 0
    }

    @Throws(SQLiteException::class)
    private fun putSentMessageToDatabase(
        recipientPhoneNumber: String,
        message: String,
    ) {
        val contentValues = ContentValues()
        contentValues.put(Telephony.Sms.BODY, message)
        contentValues.put(Telephony.Sms.ADDRESS, recipientPhoneNumber)
        contentValues.put(Telephony.Sms.DATE_SENT, Calendar.getInstance().timeInMillis.toString())
        contentValues.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        if (contentResolver.insert(allURI, contentValues) == null) {
                throw SQLiteException("The message could not be saved")
            }
        Log.i(logTag, "Message has been stored successfully")
    }


    fun deleteInboxMessagesOlderThan(time: Long){
        applicationContext.contentResolver.delete(allURI, Telephony.Sms.DATE +" < "+ time, null)
    }
}