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

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var processing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Point at a product") }

    val categories by viewModel.categories.collectAsState()

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
                        onNavigateBack,
                        { statusMessage = it },
                        { processing = false }
                    )
                },
                enabled = !processing && categories.isNotEmpty()
            ) {
                Text("Capture Product")
            }
        }
    }
}

private fun captureAndProcess(
    imageCapture: ImageCapture?,
    executor: ExecutorService,
    context: android.content.Context,
    viewModel: ShoppingViewModel,
    scope: CoroutineScope,
    onNavigateBack: () -> Unit,
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
                        // Found text! Add it
                        val firstCategory = viewModel.categories.value.firstOrNull()
                        if (firstCategory != null) {
                            viewModel.addItem(detectedText.lines().first(), firstCategory.id)
                            ContextCompat.getMainExecutor(context).execute { onNavigateBack() }
                        } else {
                            setStatus("No categories available")
                            onFinished()
                        }
                    } else {
                        // 2. OCR failed, try Gemini
                        processWithGemini(bitmap, context, viewModel, scope, onNavigateBack, setStatus, onFinished)
                    }
                }
                .addOnFailureListener {
                    processWithGemini(bitmap, context, viewModel, scope, onNavigateBack, setStatus, onFinished)
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
    onNavigateBack: () -> Unit,
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
                val firstCategory = viewModel.categories.value.firstOrNull()
                if (firstCategory != null) {
                    viewModel.addItem(productName.trim(), firstCategory.id)
                    onNavigateBack()
                } else {
                    setStatus("No categories available")
                }
            } else {
                setStatus("Could not identify product")
            }
        } catch (e: Exception) {
            setStatus("Gemini Error: ${e.message}")
        } finally {
            onFinished()
        }
    }
}
