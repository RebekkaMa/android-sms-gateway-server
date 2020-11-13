package net.folivo.android.smsGatewayServer

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyStoreException

@RunWith(RobolectricTestRunner::class)
class RestApiWorkerTest {

    //getKeyStoreFileName
    @Test
    fun `when there is a file path starts with "content" and includes not the file name, it should get the name`() {
        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/msf%3A1035")
        val contentResolverMock = mockk<ContentResolver>()
        val matrixCursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        matrixCursor.addRow(arrayOf("certificate.p12"))
        every {
            contentResolverMock.query(
                testUri,
                null,
                null,
                null,
                null
            )
        } returns matrixCursor
        RestApiWorker.getKeyStoreFileName(contentResolverMock, testUri).shouldBe("certificate")
    }

    @Test
    fun `when there is a file path starts with "content" and includes the file name, it should get the name`() {
        val testUri = Uri.parse("content://com.android.providers.downloads.documents/document/certificate.p12")
        val contentResolverMock = mockk<ContentResolver>()
        val matrixCursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        matrixCursor.addRow(arrayOf("certificate.p12"))
        every {
            contentResolverMock.query(
                testUri,
                null,
                null,
                null,
                null
            )
        } returns matrixCursor
        RestApiWorker.getKeyStoreFileName(contentResolverMock, testUri).shouldBe("certificate")
    }

    @Test
    fun `when there is a file path starts not with "content" and includes the file name, it should get the name`() {
        val testUri = Uri.parse("/storage/emulated/0/Download/certificate.p12")
        val contentResolverMock = mockk<ContentResolver>()
        val matrixCursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME))
        matrixCursor.addRow(arrayOf("certificate.p12"))
        every {
            contentResolverMock.query(
                testUri,
                null,
                null,
                null,
                null
            )
        } returns matrixCursor
        RestApiWorker.getKeyStoreFileName(contentResolverMock, testUri).shouldBe("certificate")
    }


    @Test(expected = KeyStoreException::class)
    fun `when there is an empty file path, it should throw a KeyStoreException`(){
        val testUri = Uri.parse("")
        val contentResolverMock = mockk<ContentResolver>()
        every {
            contentResolverMock.query(
                testUri,
                null,
                null,
                null,
                null
            )
        } returns null
        RestApiWorker.getKeyStoreFileName(contentResolverMock, testUri)
    }

}