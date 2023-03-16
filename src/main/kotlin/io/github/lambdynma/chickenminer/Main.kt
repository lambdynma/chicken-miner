package io.github.lambdynma.chickenminer

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import io.github.lambdynma.chickenminer.DriveManager.APPLICATION_NAME
import io.github.lambdynma.chickenminer.DriveManager.JSON_FACTORY
import kotlinx.coroutines.*
import net.sourceforge.tess4j.Tesseract
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import javax.sound.sampled.*

object DriveManager {
    const val APPLICATION_NAME = "Google Drive API Java Quickstart"
    val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
    private const val TOKENS_DIRECTORY_PATH = "tokens"
    private val SCOPES = listOf(DriveScopes.DRIVE_FILE)
    private const val CREDENTIALS_FILE_PATH = "/credentials.json"

    @Throws(IOException::class)
    fun getCredentials(httpTransport: NetHttpTransport): Credential {
        val `in` = File("./credentials.json").also { if (!it.exists()) throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH") }
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(ByteArrayInputStream(`in`.readText(StandardCharsets.UTF_8).toByteArray())))

        val flow = GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }
}

suspend fun main() {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val service = Drive.Builder(httpTransport, JSON_FACTORY, DriveManager.getCredentials(httpTransport)).setApplicationName(APPLICATION_NAME).build()
    val tesseract = Tesseract()
    val requestJobs = mutableListOf<Job>()
    var previousList: List<com.google.api.services.drive.model.File>? = null

    while (true) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                service.files().list().apply {
                    supportsAllDrives = true
                    includeItemsFromAllDrives = true
                    q = "'GOOGLE_DRIVE_FOLDER_LINK' in parents and mimeType = 'image/jpeg'"
                }.execute().let {
                    previousList?.let { previous ->
                        val diff = it.files - previous.toSet()
                        if (diff.isNotEmpty()) {
                            diff.forEach { file ->
                                previousList = it.files
                                requestJobs.filter { job -> job != this && job.isActive }.forEach { job ->
                                    job.cancel()
                                    requestJobs.remove(job)
                                }
                                val id = file.id
                                val name = file.name
                                val image = ImageIO.read(URL("https://drive.google.com/uc?id=${id}&export=download"))
                                val code = "900${tesseract.doOCR(image, Rectangle(456, 1450, 264, 51))}"

                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                                Runtime.getRuntime().exec("wscript ./changeFocus.vbs")
                                Robot().keyPress(KeyEvent.VK_F9)

                                println("ID: $id")
                                println("NAME: $name")
                                println("CODE: $code")
                            }
                        }
                    } ?: run { previousList = it.files }
                }
            }
            catch (e: TimeoutException) {
                println("GOT TIMEOUT")
            }
        }.also { requestJobs.add(it) }
        delay(200L)

        requestJobs.filter { it.isCompleted }.forEach { requestJobs.remove(it) }
    }
}