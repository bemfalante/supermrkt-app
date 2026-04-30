package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.supermarketlist.util.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val apiKey by settingsManager.apiKey.collectAsState(initial = "")
    var tempKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiKey) {
        tempKey = apiKey ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Required for product identification when OCR fails.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = tempKey,
                onValueChange = { tempKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste your API key here") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        settingsManager.saveApiKey(tempKey)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Key")
            }
        }
    }
}
