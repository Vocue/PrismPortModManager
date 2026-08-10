package com.prismport.modmanager.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object GitHubScraper {
    private val client = OkHttpClient()
    private val urlRegex = Regex("""https?://[^\s"'<>\)]+""", RegexOption.IGNORE_CASE)

    suspend fun extractModLinksFromUrl(url: String): List<String> = withContext(Dispatchers.IO) {
        val rawUrl = when {
            url.contains("github.com") && !url.contains("raw.githubusercontent.com") -> {
                url.replace("github.com", "raw.githubusercontent.com")
                    .replace("/blob/", "/")
            }
            else -> url
        }

        val request = Request.Builder().url(rawUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyList()

        val foundUrls = urlRegex.findAll(body).map { it.value }.toList()

        return@withContext foundUrls.filter { downloadUrl ->
            downloadUrl.endsWith(".jar") ||
            downloadUrl.contains("modrinth.com/mod/") ||
            downloadUrl.contains("curseforge.com/minecraft/mc-mods/")
        }.distinct()
    }
}
