package com.salahlock.app.ui.camera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.salahlock.app.camera.GravitySensorReader
import com.salahlock.app.camera.VerificationProviderFactory
import com.salahlock.app.data.model.VerificationResult
import com.salahlock.app.theme.*
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prayerName = intent.getStringExtra(com.salahlock.app.service.UsageStatsPollingService.EXTRA_PRAYER_NAME) ?: "FAJR"
        setContent {
            com.salahlock.app.theme.SalahLockTheme {
                CameraVerificationScreen(
                    prayerName = prayerName,
                    onVerified = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onDismiss = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
fun CameraVerificationScreen(
    prayerName: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var verificationState by remember { mutableStateOf<VerificationState>(VerificationState.Idle) }
    var retryCount by remember { mutableIntStateOf(0) }
    val gravityReader = remember { GravitySensorReader(context) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        gravityReader.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            gravityReader.stop()
            cameraExecutor.shutdown()
        }
    }

    // Success auto-dismiss after 1.5s
    LaunchedEffect(verificationState) {
        if (verificationState is VerificationState.Success) {
            kotlinx.coroutines.delay(1500L)
            onVerified()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Camera Preview ──
        if (verificationState !is VerificationState.Success) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    setupCamera(ctx, lifecycleOwner, previewView) { capture ->
                        imageCapture = capture
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Dark overlay gradient at top and bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
        )

        // ── Alignment Guide (mat outline) ──
        if (verificationState is VerificationState.Idle || verificationState is VerificationState.Error) {
            Box(
                modifier = Modifier
                    .size(240.dp, 320.dp)
                    .align(Alignment.Center)
                    .border(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            )
        }

        // ── Top Bar ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Prayer Verification",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Please align your camera to capture your prayer mat.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }

        // ── Bottom Controls ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Feedback message
            AnimatedVisibility(visible = verificationState is VerificationState.Error) {
                val msg = (verificationState as? VerificationState.Error)?.message ?: ""
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Success indicator
            AnimatedVisibility(visible = verificationState is VerificationState.Success) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Verification Successful",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "May Allah accept your prayer.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            // Capture button
            if (verificationState !is VerificationState.Success) {
                val isLoading = verificationState is VerificationState.Processing

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoading) Color.White.copy(alpha = 0.5f) else Color.White
                        )
                        .clickable(enabled = !isLoading) {
                            val capture = imageCapture ?: return@clickable
                            verificationState = VerificationState.Processing
                            captureAndVerify(
                                context = context,
                                capture = capture,
                                executor = cameraExecutor,
                                gravity = gravityReader.latestGravity,
                                onResult = { result ->
                                    when (result) {
                                        is VerificationResult.Verified -> {
                                            verificationState = VerificationState.Success
                                        }
                                        else -> {
                                            retryCount++
                                            verificationState = VerificationState.Error(
                                                VerificationProviderFactory.getActiveProvider().getFeedbackMessage(result)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = EmeraldPrimary,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 4.dp,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(4.dp, EmeraldPrimary, CircleShape)
                                .background(Color.White, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (retryCount > 0) "Retry attempt ${retryCount}/3" else "Tap to verify",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onCapture: (ImageCapture) -> Unit,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
        onCapture(capture)
    }, ContextCompat.getMainExecutor(context))
}

private fun captureAndVerify(
    context: Context,
    capture: ImageCapture,
    executor: ExecutorService,
    gravity: FloatArray?,
    onResult: (VerificationResult) -> Unit,
) {
    val provider = VerificationProviderFactory.getActiveProvider()

    capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            try {
                val bitmap = imageProxyToBitmap(image)
                val result = provider.verify(bitmap, gravity)
                image.close()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(result)
                }
            } catch (e: Exception) {
                image.close()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(VerificationResult.Error("Capture failed: ${e.message}"))
                }
            }
        }

        override fun onError(exception: ImageCaptureException) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onResult(VerificationResult.Error("Camera error: ${exception.message}"))
            }
        }
    })
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    // Rotate according to imageProxy rotation
    val rotation = imageProxy.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    } else bmp
}

sealed class VerificationState {
    data object Idle : VerificationState()
    data object Processing : VerificationState()
    data object Success : VerificationState()
    data class Error(val message: String) : VerificationState()
}
