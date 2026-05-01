package com.example.supermarketlist.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.supermarketlist.util.SettingsManager
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(viewModel: ShoppingViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var processing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Point at a product") }

    val categories by viewModel.categories.collectAsState()
    var detectedProductName by remember { mutableStateOf<String?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder().build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        if (processing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(statusMessage, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    processing = true
                    statusMessage = "Analyzing..."
                    captureAndProcess(
                        imageCapture,
                        cameraExecutor,
                        context,
                        viewModel,
                        scope,
                        onDetected = { name ->
                            detectedProductName = name
                            showCategoryDialog = true
                            processing = false
                            statusMessage = "Product detected: $name"
                        },
                        setStatus = { statusMessage = it },
                        onFinished = { processing = false }
                    )
                },
                enabled = !processing && categories.isNotEmpty()
            ) {
                Text("Capture Product")
            }
        }

        if (showCategoryDialog && detectedProductName != null) {
            var selectedCategoryId by remember { mutableLongStateOf(categories.firstOrNull()?.id ?: -1L) }

            AlertDialog(
                onDismissRequest = { showCategoryDialog = false },
                title = { Text("Add to List") },
                text = {
                    Column {
                        Text("Detected: $detectedProductName")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Category:")
                        CategoryDropdown(
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onCategorySelected = { selectedCategoryId = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedCategoryId != -1L) {
                            viewModel.addItem(detectedProductName!!, selectedCategoryId)
                            onNavigateBack()
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun captureAndProcess(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: android.content.Context,
    viewModel: ShoppingViewModel,
    scope: CoroutineScope,
    onDetected: (String) -> Unit,
    setStatus: (String) -> Unit,
    onFinished: () -> Unit
) {
    if (imageCapture == null) {
        onFinished()
        return
    }

    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageProxy: ImageProxy) {
            val bitmap = imageProxy.toBitmap()
            imageProxy.close()

            // 1. Try OCR
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val detectedText = visionText.text
                    if (detectedText.isNotBlank()) {
                        // Found text!
                        val productName = detectedText.lines().first().trim()
                        ContextCompat.getMainExecutor(context).execute { onDetected(productName) }
                    } else {
                        // 2. OCR failed, try Gemini
                        processWithGemini(bitmap, context, viewModel, scope, onDetected, setStatus, onFinished)
                    }
                }
                .addOnFailureListener {
                    processWithGemini(bitmap, context, viewModel, scope, onDetected, setStatus, onFinished)
                }
        }

        override fun onError(exception: ImageCaptureException) {
            setStatus("Error capturing image")
            onFinished()
        }
    })
}

private fun processWithGemini(
    bitmap: Bitmap,
    context: android.content.Context,
    viewModel: ShoppingViewModel,
    scope: CoroutineScope,
    onDetected: (String) -> Unit,
    setStatus: (String) -> Unit,
    onFinished: () -> Unit
) {
    val settingsManager = SettingsManager(context)

    scope.launch {
        val apiKey = settingsManager.apiKey.first()
        if (apiKey.isNullOrBlank()) {
            setStatus("OCR failed and no Gemini API Key set.")
            onFinished()
            return@launch
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val prompt = content {
                image(bitmap)
                text("Identify the product in this image and return only its name, nothing else.")
            }

            val response = generativeModel.generateContent(prompt)
            val productName = response.text

            if (!productName.isNullOrBlank()) {
                onDetected(productName.trim())
            } else {
                setStatus("Could not identify product")
                onFinished()
            }
        } catch (e: Exception) {
            setStatus("Gemini Error: ${e.message}")
            onFinished()
        }
    }
}
