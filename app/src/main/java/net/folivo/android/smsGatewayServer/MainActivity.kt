package net.folivo.android.smsGatewayServer

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.work.*
import java.lang.Exception
import java.security.KeyStore
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.time.ExperimentalTime


class MainActivity : AppCompatActivity() {

    private val logTag = MainActivity::class.java.simpleName

    private val workManager = WorkManager.getInstance(this)
    private val uniqueWorkName = "restApiWorker"

    private lateinit var sharedPref: SharedPreferences


    private val smsDefaultAppResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            Log.i(logTag, "Default SMS app permission is granted.")
        } else {
            Log.i(logTag, "Default SMS app permission is not granted.")
        }
    }

    private val certificateResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val intent = it.data
            intent?.data?.also { uri ->
                val sharedPref: SharedPreferences = this.getPreferences(MODE_PRIVATE)
                val editor = sharedPref.edit()
                editor.putString(getString(R.string.saved_certificate_uri_str_key), uri.toString())
                editor.commit()
                findViewById<EditText>(R.id.editTextPathKeyStore).setText(uri.toString())
                Log.i(logTag, "certificate uri: $uri")

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        }
    }

    private val storagePermissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(logTag, "Storage permission is granted.")
        } else {
            Log.i(logTag, "Storage permission is not granted.")
        }
    }

    private val onSharedPreferenceChangeListener =
        OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                getString(R.string.saved_username_key) -> findViewById<EditText>(R.id.editTextUserName).hint =
                    sharedPreferences.getString(key, "admin")
                getString(R.string.saved_password_key) -> findViewById<EditText>(R.id.editTextPassword).hint =
                    sharedPreferences.getString(key, "123")
                getString(R.string.saved_keyStore_password_key) -> findViewById<EditText>(R.id.editTextKeyStorePassword).hint =
                    sharedPreferences.getString(key, "")
                getString(R.string.saved_certificate_uri_str_key) -> findViewById<EditText>(R.id.editTextPathKeyStore).hint =
                    sharedPreferences.getString(key, "")
            }
        }


    @ExperimentalTime
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editTextUserName = findViewById<EditText>(R.id.editTextUserName)
        val editTextPassword = findViewById<EditText>(R.id.editTextPassword)
        val editTextKeyStorePassword = findViewById<EditText>(R.id.editTextKeyStorePassword)
        val editTextPathKeyStore = findViewById<EditText>(R.id.editTextPathKeyStore)


        sharedPref = this.getPreferences(MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)

        editTextUserName.hint =
            sharedPref.getString(getString(R.string.saved_username_key), "admin")
        editTextPassword.hint =
            sharedPref.getString(getString(R.string.saved_password_key), "123")
        editTextKeyStorePassword.hint =
            sharedPref.getString(getString(R.string.saved_keyStore_password_key), "")
        editTextPathKeyStore.hint =
            sharedPref.getString(getString(R.string.saved_certificate_uri_str_key), "")

        findViewById<SwitchCompat>(R.id.switchStartServer).isEnabled = isCertificateAvailable()

        findViewById<Button>(R.id.buttonCheckCertificateAvailability).setOnClickListener {

            val keyStorePassword =
                editTextKeyStorePassword.text.toString()
            val keyStorePath =
                editTextPathKeyStore.text.toString()
            val editor = sharedPref.edit()


            if (!keyStorePassword.isBlank()) {
                editor.putString(
                    getString(R.string.saved_keyStore_password_key),
                    keyStorePassword
                )
                editor.commit()
            }

            if (!keyStorePath.isBlank()) {
                editor.putString(getString(R.string.saved_certificate_uri_str_key), keyStorePath)
                editor.commit()
            }

            if (!isCertificateAvailable()) {
                Toast.makeText(this, "Error loading certificate: ", Toast.LENGTH_SHORT).show()
                findViewById<SwitchCompat>(R.id.switchStartServer).isEnabled = false
            } else {
                Toast.makeText(this, "Certificate loaded successfully", Toast.LENGTH_SHORT).show()
                findViewById<SwitchCompat>(R.id.switchStartServer).isEnabled = true

            }

        }


        findViewById<AppCompatImageButton>(R.id.buttonCertificate).setOnClickListener {


            if (storagePermissionGranted()) {
                // Choose a directory using the system's file picker.
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/x-pkcs12"
                }
                certificateResultLauncher.launch(intent)

            } else {
                storagePermissionResultLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)

            }


        }

        findViewById<SwitchCompat>(R.id.switchStartServer).setOnCheckedChangeListener { viewButton, isChecked ->

            if (isDefaultSmsApp()) {
                if (isChecked && !isUniqueWorkScheduled(uniqueWorkName)) {

                    val txtUserName = editTextUserName.text.toString()
                    val txtPassword = editTextPassword.text.toString()

                    val editor = sharedPref.edit()

                    if (!txtPassword.isBlank()) {
                        editor.putString(getString(R.string.saved_password_key), txtPassword)
                        editor.commit()
                    }

                    if (!txtUserName.isBlank()) {
                        editor.putString(getString(R.string.saved_username_key), txtUserName)
                        editor.commit()
                    }


                    if (!isCertificateAvailable()) {
                        Toast.makeText(this, "Error loading the certificate", Toast.LENGTH_SHORT)
                            .show()
                        findViewById<SwitchCompat>(R.id.switchStartServer).isEnabled = false
                    }


                    val data =
                        Data.Builder()
                            .putString(
                                "username",
                                sharedPref.getString(
                                    getString(R.string.saved_username_key),
                                    "admin"
                                )
                            )
                            .putString(
                                "password",
                                sharedPref.getString(getString(R.string.saved_password_key), "123")
                            )
                            .putString(
                                "certificateUriStr",
                                sharedPref.getString(
                                    getString(R.string.saved_certificate_uri_str_key),
                                    null
                                )
                            )
                            .putString(
                                "keyStorePassword",
                                sharedPref.getString(
                                    getString(R.string.saved_keyStore_password_key),
                                    ""
                                )
                            )
                            .build()

                    val restApiWorkRequest =
                        OneTimeWorkRequestBuilder<RestApiWorker>().setInputData(data)
                            .addTag(uniqueWorkName)
                            .build()

                    workManager.enqueueUniqueWork(
                        uniqueWorkName,
                        ExistingWorkPolicy.KEEP,
                        restApiWorkRequest
                    )
                }
                if (!isChecked && isUniqueWorkScheduled(uniqueWorkName)) {
                    workManager.cancelUniqueWork(uniqueWorkName)

                }
            } else {
                requestSmsDefaultAppState()
                viewButton.toggle()
            }
        }

        findViewById<Button>(R.id.buttonDeleteMessages).setOnClickListener{

            var toastText = "Messages deleted"
            val messageManager = MessageManager(applicationContext)
            val durationInMillis = Duration.ofDays(14).toMillis()
            try {
                messageManager.deleteInboxMessagesOlderThan(Calendar.getInstance().timeInMillis.minus(durationInMillis))
            }catch (exception : Exception){
                Log.w(logTag, exception.toString())
                toastText = "Messages could not be deleted"
            }
            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()

        }

        workManager.getWorkInfosForUniqueWorkLiveData(uniqueWorkName).observe(this) {

            when (it.singleOrNull()?.state) {
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_server_terminated_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i(
                        logTag,
                        getString(R.string.toast_server_terminated_successfully) + ": " + it.single().state
                    )
                }
                WorkInfo.State.FAILED -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_server_terminated_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.i(
                        logTag,
                        getString(R.string.toast_server_terminated_failed) + ": " + it.single().state
                    )
                    findViewById<SwitchCompat>(R.id.switchStartServer).toggle()
                }

                else -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val switch = findViewById<SwitchCompat>(R.id.switchStartServer)
        if ((isUniqueWorkScheduled(uniqueWorkName) && !switch.isChecked) || (!isUniqueWorkScheduled(
                uniqueWorkName
            ) && switch.isChecked)
        ) {
            switch.toggle()
        }


    }


    override fun onDestroy() {
        super.onDestroy()
        if (isUniqueWorkScheduled(uniqueWorkName)) {
            workManager.cancelUniqueWork(uniqueWorkName)
        }
    }

    private fun isUniqueWorkScheduled(tag: String): Boolean {
        return try {
            val workerState = workManager.getWorkInfosForUniqueWork(tag).get().singleOrNull()?.state
            workerState == WorkInfo.State.RUNNING || workerState == WorkInfo.State.ENQUEUED
        } catch (e: ExecutionException) {
            Log.w(logTag, e.stackTraceToString())
            false
        } catch (e: InterruptedException) {
            Log.w(logTag, e.stackTraceToString())
            false
        }
    }

    private fun storagePermissionGranted(): Boolean {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDefaultSmsApp(): Boolean {
        return this.packageName == Telephony.Sms.getDefaultSmsPackage(this)
    }


    private fun requestSmsDefaultAppState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager: RoleManager = this.getSystemService(RoleManager::class.java)
            // check if the app is having permission to be as default SMS app
            val isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
            if (isRoleAvailable) {
                // check whether your app is already holding the default SMS app role.
                val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                if (!isRoleHeld) {
                    val roleRequestIntent =
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)

                    smsDefaultAppResultLauncher.launch(roleRequestIntent)

                }
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            smsDefaultAppResultLauncher.launch(intent)
        }
    }

    private fun isCertificateAvailable(): Boolean {

        val certificateUriStr =
            sharedPref.getString(getString(R.string.saved_certificate_uri_str_key), "").orEmpty()
        val keyStorePassword =
            sharedPref.getString(getString(R.string.saved_keyStore_password_key), "").orEmpty()
        val keyStore: KeyStore
        val certificateFileNameWithoutExtension: String


        try {
            certificateFileNameWithoutExtension =
                RestApiWorker.getKeyStoreFileName(
                    applicationContext.contentResolver,
                    Uri.parse(certificateUriStr)
                )
            keyStore = RestApiWorker.loadKeyStore(
                applicationContext.contentResolver,
                Uri.parse(certificateUriStr),
                keyStorePassword
            )
        } catch (exception: Exception) {
            Log.i(logTag, "isCertificateAvailable: false -> $exception")
            return false
        }
        if (keyStore.getCertificate(certificateFileNameWithoutExtension) == null) {
            Log.i(
                logTag,
                "isCertificateAvailable: false -> given alias does not exist or does not contain a certificate"
            )
            return false
        }
        return true
    }

}
