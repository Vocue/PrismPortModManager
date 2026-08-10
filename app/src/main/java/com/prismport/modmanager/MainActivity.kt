package com.prismport.modmanager

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prismport.modmanager.data.GitHubScraper
import com.prismport.modmanager.data.ModHit
import com.prismport.modmanager.data.ModDownloader
import com.prismport.modmanager.data.ModrinthClient
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var folderUri by remember { mutableStateOf<Uri?>(null) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folderUri = it
            Toast.makeText(context, "Amethyst Directory Linked!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prism Port") },
                actions = {
                    Button(
                        onClick = { dirPickerLauncher.launch(null) },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (folderUri == null) "Select Folder" else "Folder Linked")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Modrinth") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("GitHub Scraper") },
                    icon = {}
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ModrinthScreen(folderUri)
                1 -> GitHubScraperScreen(folderUri)
            }
        }
    }
}

@Composable
fun ModrinthScreen(folderUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var mcVersion by remember { mutableStateOf("1.20.1") }
    var loader by remember { mutableStateOf("fabric") }
    var searchResults by remember { mutableStateOf<List<ModHit>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Mods") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = mcVersion,
                onValueChange = { mcVersion = it },
                label = { Text("Version") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = loader,
                onValueChange = { loader = it },
                label = { Text("Loader") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        val facets = "[[\"versions:$mcVersion\"],[\"categories:$loader\"]]"
                        val response = ModrinthClient.service.searchMods(query, facets)
                        searchResults = response.hits
                    } catch (e: Exception) {
                        Toast.makeText(context, "Search failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Searching..." else "Search Modrinth")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(searchResults) { mod ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = mod.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = mod.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (folderUri == null) {
                                    Toast.makeText(context, "Please select Amethyst folder first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    try {
                                        val versions = ModrinthClient.service.getProjectVersions(
                                            mod.projectId,
                                            loaders = "[\"$loader\"]",
                                            gameVersions = "[\"$mcVersion\"]"
                                        )
                                        val primaryFile = versions.firstOrNull()?.files?.firstOrNull { it.primary }
                                            ?: versions.firstOrNull()?.files?.firstOrNull()

                                        if (primaryFile != null) {
                                            val success = ModDownloader.downloadModToFolder(
                                                context, folderUri, primaryFile.url, primaryFile.filename
                                            )
                                            if (success) {
                                                Toast.makeText(context, "Downloaded ${primaryFile.filename}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to write file", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "No file found for $mcVersion / $loader", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Download to Amethyst")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubScraperScreen(folderUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var githubUrl by remember { mutableStateOf("") }
    var foundLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var isScraping by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = githubUrl,
            onValueChange = { githubUrl = it },
            label = { Text("GitHub README URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    isScraping = true
                    try {
                        foundLinks = GitHubScraper.extractModLinksFromUrl(githubUrl)
                        Toast.makeText(context, "Found ${foundLinks.size} links", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to scrape: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isScraping = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScraping) "Scraping..." else "Scan README for Mods")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(foundLinks) { link ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = link,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (folderUri == null) {
                                    Toast.makeText(context, "Select Amethyst folder first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    if (link.endsWith(".jar")) {
                                        val fileName = link.substringAfterLast("/")
                                        val success = ModDownloader.downloadModToFolder(
                                            context, folderUri, link, fileName
                                        )
                                        if (success) {
                                            Toast.makeText(context, "Downloaded $fileName", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Found project page link", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}
