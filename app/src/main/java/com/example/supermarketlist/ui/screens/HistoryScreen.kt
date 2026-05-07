package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: ShoppingViewModel, onNavigateBack: () -> Unit) {
    val sessions by viewModel.sessions.collectAsState()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No shopping history yet")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessions) { session ->
                    val sessionItems by viewModel.getItemsForSession(session.id).collectAsState(initial = emptyList())

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Shopping in ${session.categoryName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(session.timestamp)),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Group session items by status
                            val bought = sessionItems.filter { it.status == "BOUGHT" }
                            val foundNotBought = sessionItems.filter { it.status == "FOUND_NOT_BOUGHT" }
                            val notFound = sessionItems.filter { it.status == "NOT_FOUND" }

                            if (bought.isNotEmpty()) {
                                Text("Bought:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                                bought.forEach {
                                    Text("• ${it.itemName} (R$ ${String.format("%.2f", it.price)})", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (foundNotBought.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Found but not bought:", style = MaterialTheme.typography.labelMedium, color = Color.Red)
                                foundNotBought.forEach {
                                    Text("• ${it.itemName} (R$ ${String.format("%.2f", it.price)})", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (notFound.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Not found:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                notFound.forEach {
                                    Text("• ${it.itemName}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
