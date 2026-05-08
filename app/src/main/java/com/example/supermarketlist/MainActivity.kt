package com.example.supermarketlist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.ui.screens.CategoryScreen
import com.example.supermarketlist.ui.screens.HistoryScreen
import com.example.supermarketlist.ui.screens.MainScreen
import com.example.supermarketlist.ui.screens.ShoppingScreen
import com.example.supermarketlist.ui.theme.SupermarketListTheme
import com.example.supermarketlist.viewmodel.ShoppingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SupermarketListTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: ShoppingViewModel = viewModel()
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()

    var showVoiceCategoryDialog by remember { mutableStateOf(false) }
    var voiceDetectedItemName by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) {
                handleVoiceInput(spokenText, categories) { itemName, categoryId ->
                    if (categoryId != null) {
                        viewModel.addItem(itemName, categoryId)
                    } else {
                        voiceDetectedItemName = itemName
                        showVoiceCategoryDialog = true
                    }
                }
            }
        }
    }

    if (showVoiceCategoryDialog) {
        var selectedCategoryId by remember(voiceDetectedItemName) { mutableStateOf<Long?>(viewModel.lastUsedCategoryId) }
        var showIntentionalConfirm by remember { mutableStateOf(false) }
        var showNewCategoryDialog by remember { mutableStateOf(false) }
        var newCategoryName by remember { mutableStateOf("") }

        if (showIntentionalConfirm) {
            AlertDialog(
                onDismissRequest = { showIntentionalConfirm = false },
                title = { Text("No Category Selected") },
                text = { Text("Are you sure you want to add '$voiceDetectedItemName' to the Uncategorized list?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addItem(voiceDetectedItemName, null)
                        showVoiceCategoryDialog = false
                        showIntentionalConfirm = false
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showIntentionalConfirm = false }) {
                        Text("No")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showVoiceCategoryDialog = false },
                title = { Text("Add to List") },
                text = {
                    Column {
                        Text("Item: $voiceDetectedItemName")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Category (Optional):")
                        CategoryDropdownWithNone(
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onCategorySelected = { selectedCategoryId = it },
                            onAddNewCategory = { showNewCategoryDialog = true }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedCategoryId != null) {
                            viewModel.addItem(voiceDetectedItemName, selectedCategoryId)
                            showVoiceCategoryDialog = false
                        } else {
                            showIntentionalConfirm = true
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVoiceCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showNewCategoryDialog = false },
                title = { Text("New Category") },
                text = {
                    TextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName) { newId ->
                                selectedCategoryId = newId
                            }
                            newCategoryName = ""
                            showNewCategoryDialog = false
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            voiceLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Audio permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToCategories = { navController.navigate("categories") },
                onNavigateToHistory = { navController.navigate("history") },
                onStartShopping = { catId ->
                    navController.navigate("shopping/${catId ?: -1L}")
                },
                onStartVoiceInput = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        voiceLauncher.launch(intent)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
        composable("categories") {
            CategoryScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
        }
        composable("history") {
            HistoryScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            "shopping/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId")
            ShoppingScreen(
                viewModel = viewModel,
                categoryId = if (categoryId == -1L) null else categoryId,
                onFinished = { navController.popBackStack("main", false) },
                onStartVoiceInput = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        voiceLauncher.launch(intent)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }
}

@Composable
fun CategoryDropdownWithNone(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onAddNewCategory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedCategory?.name ?: "No Category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ Create New Category", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onAddNewCategory()
                    expanded = false
                }
            )
        }
    }
}

private fun handleVoiceInput(
    spokenText: String,
    categories: List<Category>,
    onResult: (String, Long?) -> Unit
) {
    val lowerText = spokenText.lowercase().trim()
    var handled = false

    // English: "add [item] to [category]"
    if (lowerText.startsWith("add ")) {
        val content = lowerText.substring(4)
        val parts = content.split(" to ")
        if (parts.size == 2) {
            val itemName = parts[0].trim()
            val categoryName = parts[1].trim()
            val category = categories.find { it.name.lowercase() == categoryName }
            if (category != null) {
                onResult(itemName, category.id)
                handled = true
            }
        }
    }

    // Portuguese: "adicionar [item] em [categoria]" or "adicionar [item] na [categoria]" or "adicionar [item] no [categoria]"
    // Or more specifically: "adicionar [item] na categoria [categoria]"
    if (!handled && lowerText.startsWith("adicionar ")) {
        val content = lowerText.substring(10)

        val delimiters = listOf(" na categoria ", " no categoria ", " em categoria ", " na ", " no ", " em ")
        for (delimiter in delimiters) {
            if (content.contains(delimiter)) {
                val parts = content.split(delimiter)
                if (parts.size >= 2) {
                    val itemName = parts[0].trim()
                    val categoryName = parts[1].trim()
                    val category = categories.find { it.name.lowercase() == categoryName }
                    if (category != null) {
                        onResult(itemName, category.id)
                        handled = true
                        break
                    }
                }
            }
        }
    }

    if (!handled) {
        // Fallback: If "adicionar" or "add" was used but category wasn't found,
        // or if just the item name was spoken.
        val cleanedText = if (lowerText.startsWith("add ")) {
            lowerText.substring(4).trim()
        } else if (lowerText.startsWith("adicionar ")) {
            lowerText.substring(10).trim()
        } else {
            lowerText
        }
        onResult(cleanedText, null)
    }
}
