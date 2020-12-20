package net.folivo.android.smsGatewayServer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.text.isDigitsOnly
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException
import java.lang.IndexOutOfBoundsException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.IllegalArgumentException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class RestApiWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {


    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    private val messageManager = MessageManager(applicationContext)
    private val logTag = RestApiWorker::class.java.simpleName


    data class SmsMessageRequestContent(val recipientPhoneNumber: String, val message: String)


    @ExperimentalTime
    override suspend fun doWork(): Result = coroutineScope {


        setForegroundAsync(createForegroundInfo("Server started"))


        val password = inputData.getString("password")
            .orEmpty()
        val username = inputData.getString("username")
            .orEmpty()
        val keyStorePassword = inputData.getString("keyStorePassword")
            .orEmpty()
        val certificateUri = Uri.parse(
            inputData.getString("certificateUriStr")
                .orEmpty()
        )

        val certificateFileNameWithoutExtension: String
        val keyStore: KeyStore
        val appVersion: String = BuildConfig.VERSION_NAME

        try {
            certificateFileNameWithoutExtension =
                getKeyStoreFileName(applicationContext.contentResolver, certificateUri)
            keyStore =
                loadKeyStore(applicationContext.contentResolver, certificateUri, keyStorePassword)
        } catch (exception: java.lang.Exception) {
            Log.w(logTag, exception.toString())
            return@coroutineScope Result.failure()
        }


        try {
            val server = embeddedServer(
                Netty,
                applicationEngineEnvironment {

                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = certificateFileNameWithoutExtension,
                        keyStorePassword = {
                            keyStorePassword.toCharArray()
                        },
                        privateKeyPassword = { "".toCharArray() }) {
                        host = "0.0.0.0"
                        port = 9090
                    }

//                    connector {
//                        host = "0.0.0.0"
//                        port = 8080
//                    }

                    module {

                        install(CallLogging)
                        install(Authentication) {
                            basic {
                                realm = "Ktor Server"
                                validate { credentials ->
                                    if (credentials.password == password && credentials.name == username
                                    )
                                        UserIdPrincipal(credentials.name) else null
                                }
                            }
                        }
                        install(ContentNegotiation) {
                            gson {
//                                setPrettyPrinting()
                            }
                        }
                        routing {
                            authenticate {
                                post("/messages/out") {
                                    val result: Pair<HttpStatusCode, String>

                                    try {
                                        val smsMessage = call.receive<SmsMessageRequestContent>()

                                        result = messageManager.sendSms(
                                            smsMessage.recipientPhoneNumber,
                                            smsMessage.message
                                        )
                                    }
                                    catch (illegalArgumentException : java.lang.IllegalArgumentException){
                                        Log.i(logTag, illegalArgumentException.stackTraceToString())
                                        call.respondText(
                                                illegalArgumentException.toString(),
                                                status = HttpStatusCode.BadRequest
                                        )
                                        return@post
                                    }
                                    catch (exception: java.lang.Exception) {
                                        Log.i(logTag, exception.stackTraceToString())
                                        call.respondText(
                                            exception.toString(),
                                            status = HttpStatusCode.InternalServerError
                                        )
                                        return@post
                                    }
                                    call.respondText(result.second, status=result.first)
                                }
                                get("/") {
                                    call.respondText { "android sms gateway server ($appVersion) is running" }
                                }
                                route("/messages/in") {
                                    optionalParam("after") {
                                        get {

                                            val requestId = call.request.queryParameters["after"]
                                            val smsList: ArrayList<Message>
                                            val nextBatch: Long


                                            if (requestId.isNullOrEmpty()) {

                                                try {
                                                    smsList =
                                                        messageManager.getInboxMessages()
                                                    nextBatch = messageManager.getNextBatch()
                                                } catch (e: Exception) {
                                                    Log.w(logTag, e.stackTraceToString())
                                                    call.respondText(
                                                        e.toString(),
                                                        status = HttpStatusCode.InternalServerError
                                                    )
                                                    return@get
                                                }
                                                call.respond(
                                                    hashMapOf(
                                                        "messages" to smsList,
                                                        "nextBatch" to nextBatch
                                                    )
                                                )
                                                return@get
                                            }
                                            if (requestId.isDigitsOnly()) {

                                                try {
                                                    smsList =
                                                        messageManager.getInboxMessagesFromIdUp(

                                                            requestId
                                                        )
                                                    nextBatch = messageManager.getNextBatch()

                                                } catch (e: Exception) {
                                                    Log.w(logTag, e.stackTraceToString())
                                                    call.respondText(
                                                        e.toString(),
                                                        status = HttpStatusCode.InternalServerError
                                                    )
                                                    return@get
                                                }

                                                call.respond(
                                                    hashMapOf(
                                                        "messages" to smsList,
                                                        "nextBatch" to nextBatch
                                                    )
                                                )

                                            } else {
                                                call.respondText(
                                                    "ID must be of type long and positive!",
                                                    status = HttpStatusCode.BadRequest
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                })
            server.start()
            try {
                delay(Duration.INFINITE)
            } finally {
                server.stop(0, 10, TimeUnit.MILLISECONDS)
            }
        } catch (exception: java.lang.Exception) {
            if (exception !is CancellationException) {
                Log.w(logTag, exception.stackTraceToString())
                return@coroutineScope Result.failure()
            }
        }
        Result.success()
    }


    private fun createForegroundInfo(progress: String): ForegroundInfo {

        notificationManager.createNotificationChannel(
            NotificationChannel(
                "default",
                "Default",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val notification = NotificationCompat.Builder(applicationContext, "default")
            .setContentTitle("Server started")
            .setTicker("Running in background")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        return ForegroundInfo(1, notification)
    }


    companion object {

        @Throws(
            IndexOutOfBoundsException::class,
            IllegalArgumentException::class,
            KeyStoreException::class,
            IllegalStateException::class
        )
        fun getKeyStoreFileName(contentResolver: ContentResolver, uri: Uri): String {

            var fileName: String? = null
            if (uri.scheme == "content") {
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        fileName =
                            it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                }
            } else {
                val uriPath = uri.path
                fileName = uriPath?.substring(uriPath.lastIndexOf("/") + 1)
            }

            if (fileName.isNullOrBlank()) {
                throw KeyStoreException("The KeyStore file name could not be extracted.")
            }

            return fileName?.lastIndexOf('.')?.let { fileName?.substring(0, it) }
                ?: throw KeyStoreException("The KeyStore file name could not be extracted")
        }


        @Throws(
            KeyStoreException::class,
            FileNotFoundException::class,
            IOException::class,
            NoSuchAlgorithmException::class,
            CertificateException::class
        )
        fun loadKeyStore(
            contentResolver: ContentResolver,
            uri: Uri,
            keyStorePassword: String
        ): KeyStore {

            val keyStore = KeyStore.getInstance("pkcs12")
            if (uri.scheme == "content") {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    keyStore.load(inputStream, keyStorePassword.toCharArray())
                }
            } else {
                keyStore.load(
                    FileInputStream(uri.path),
                    keyStorePassword.toCharArray()
                )
            }
            return keyStore
        }
    }


}
