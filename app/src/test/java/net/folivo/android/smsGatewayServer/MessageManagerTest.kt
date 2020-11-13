package net.folivo.android.smsGatewayServer

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf


@RunWith(RobolectricTestRunner::class)
class MessageManagerTest {

//    @Test
//    fun `when here are multiple sms messages it should get last id from inbox messages`() {
//        val contentResolverMock = mockk<ContentResolver>()
//        val contextMock = mockk<Context> {
//            every { contentResolver } returns contentResolverMock
//        }
//        val cut = MessageManager(contextMock)
//
//        val matrixCursor = MatrixCursor(arrayOf(Telephony.Sms._ID))
//        matrixCursor.addRow(arrayOf(4))
//        matrixCursor.addRow(arrayOf(2))
//        every {
//            contentResolverMock.query(
//                Telephony.Sms.Inbox.CONTENT_URI,
//                null,
//                Telephony.Sms._ID,
//                null,
//                Telephony.Sms._ID + " DESC"
//            )
//        } returns matrixCursor
//        cut.getNextBatch().shouldBe(4)
//    }
//
//    @Test
//    fun `when here are no sms messages it should return 0`() {
//        val contentResolverMock = mockk<ContentResolver>()
//        val contextMock = mockk<Context> {
//            every { contentResolver } returns contentResolverMock
//        }
//        val cut = MessageManager(contextMock)
//
//        val matrixCursor = MatrixCursor(arrayOf(Telephony.Sms._ID))
//        every {
//            contentResolverMock.query(
//                Telephony.Sms.Inbox.CONTENT_URI,
//                null,
//                Telephony.Sms._ID,
//                null,
//                Telephony.Sms._ID + " DESC"
//            )
//        } returns matrixCursor
//        cut.getNextBatch().shouldBe(0)
//    }
//
//    @Test
//    fun `when there is one simple text message to sent it should send simple text message"`() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
//
//        val cut = MessageManager(context)
//        runBlocking {
//            launch {
//                println("sendSms")
//                cut.sendSms("012345678", "Dies ist eine einfache Textnachricht.")
//                    .shouldBe(Pair(HttpStatusCode.OK, "Transmission successful"))
//                println("send fertig")
//            }
//            launch {
//                var smsManager = SmsManager.getDefault()
//                var shadowSmsManager = shadowOf(smsManager)
//                println("loop")
//                while (true) {
//                    val lastSentTextMessageParams = shadowSmsManager.lastSentTextMessageParams
//                    if (lastSentTextMessageParams != null) {
//
//                        lastSentTextMessageParams.sentIntent.send(Activity.RESULT_OK)
//                        lastSentTextMessageParams.destinationAddress.shouldBe("012345678")
//                        lastSentTextMessageParams.text.shouldBe("Dies ist eine einfache Textnachricht.")
//
//                        break
//                    }
//                    delay(100)
//                }
//                println("fertig")
//            }
//        }
//        println("end")
//    }
//
//    @Test
//    fun `where there is one multiple text message to sent it should send multiple text message`() {
//        val context = ApplicationProvider.getApplicationContext<Context>()
//        val cut = MessageManager(context)
//        var smsManager = SmsManager.getDefault()
//        var shadowSmsManager = shadowOf(smsManager)
//        GlobalScope.launch {
//            cut.sendSms(
//                "012345678",
//                "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht."
//            )
//        }
//        val lastSentTextMessageParams =
//            shadowSmsManager.lastSentMultipartTextMessageParams
//        lastSentTextMessageParams.destinationAddress.shouldBe("012345678")
//        lastSentTextMessageParams.parts.shouldBe(listOf(
//            "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies",
//            " ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist",
//            " eine Multipart Textnachricht."
//        ))
//    }

}

//private fun testBody(): DescribeSpec.() -> Unit {
//    return {
//
//        describe(MessageManager::sendSms.name) {
//
//            val context = ApplicationProvider.getApplicationContext<Context>()
//            val cut = MessageManager(context)
//            describe("there is one simple text message to sent") {
//                var smsManager = SmsManager.getDefault()
//                smsManager.sendTextMessage("1234", null, "jklösldkjfösd", null, null)
//                var shadowSmsManager = shadowOf(smsManager)
//
//                it("should send simple text message") {
//                    GlobalScope.launch {
//                        cut.sendSms("012345678", "Dies ist eine einfache Textnachricht.")
//                    }
//                    delay(500)
//                    val lastSentTextMessageParams = shadowSmsManager.lastSentTextMessageParams
//                    lastSentTextMessageParams.sentIntent.send(context,
//                        Activity.RESULT_OK,
//                        Intent("sent"))
//                    lastSentTextMessageParams.destinationAddress.shouldBe("012345678")
//                    lastSentTextMessageParams.text.shouldBe("Dies ist eine einfache Textnachricht.")
//                }
//
//            }
//            describe("there are is multiple text message to sent") {
//                it("should send multiple text message") {
//                    cut.sendSms(
//                        "012345678",
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                                "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                                "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                                "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. " +
//                                "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht."
//                    )
//                    val shadowSmsManager = ShadowSmsManager()
//                    val lastSentTextMessageParams =
//                        shadowSmsManager.lastSentMultipartTextMessageParams
//                    lastSentTextMessageParams.destinationAddress.shouldBe("012345678")
//                    lastSentTextMessageParams.parts.shouldBe(listOf(
//                        "Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies",
//                        " ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist eine Multipart Textnachricht. Dies ist",
//                        " eine Multipart Textnachricht."
//                    ))
//                }
//
//            }
//
//        }
//
//    }
//}