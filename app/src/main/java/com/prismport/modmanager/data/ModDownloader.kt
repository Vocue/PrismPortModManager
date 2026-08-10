package com.prismport.modmanager.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

object ModDownloader {
    private val client = OkHttpClient()

    suspend fun downloadModToFolder(
        context: Context,
        folderUri: Uri,
        downloadUrl: String,
        fileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootDir = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext false
            val targetDir = if (rootDir.name == "mods") {
                rootDir
            } else {
                rootDir.findFile("mods") ?: rootDir.createDirectory("mods") ?: rootDir
            }

            val existingFile = targetDir.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                existingFile.delete()
            }

            val targetFile = targetDir.createFile("application/java-archive", fileName) 
                ?: return@withContext false

            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()
            val inputStream: InputStream = response.body?.byteStream() ?: return@withContext false

            context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                inputStream.copyTo(output)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
