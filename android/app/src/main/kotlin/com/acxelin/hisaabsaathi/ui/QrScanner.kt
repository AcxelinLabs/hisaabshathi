package com.acxelin.hisaabsaathi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.acxelin.hisaabsaathi.core.Strings
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [then] once the camera may be used, asking for the permission the first time. Needed
 * for the live QR scanner, and for the camera app (bill photos, selfie) since the app declares
 * the camera permission.
 */
@Composable
internal fun rememberWithCamera(s: Strings, then: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val action by rememberUpdatedState(then)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) action() else Toast.makeText(context, s.cameraNeeded, Toast.LENGTH_LONG).show()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) action()
        else runCatching { ask.launch(Manifest.permission.CAMERA) }
    }
}

/**
 * A live QR scanner inside the app: the camera view, a square window with four corner
 * brackets and a moving scan line, a light for dark rooms, and "choose a photo" for a saved
 * e-Aadhaar. Codes are read on the phone by the ML Kit reader bundled in the app (works
 * offline, no Google Play services). The picture is never saved; [onCode] gets the first QR read.
 */
@Composable
fun QrScannerScreen(s: Strings, accept: (String) -> Boolean = { true }, onCode: (String) -> Unit, onPickPhoto: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val found by rememberUpdatedState(onCode)
    val wanted by rememberUpdatedState(accept)
    // A QR was seen but it is another one (a UPI or shop code): keep scanning and say so.
    var wrongCode by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(owner) {
        val done = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor()
        val reader = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val main = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // Aadhaar QR codes are dense: analyse a sharp picture (about 1080p), newest frame only.
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    ).build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(worker) { frame ->
                readFrame(frame, reader, done, wanted, onOther = { main.execute { wrongCode = true } }) { raw -> main.execute { found(raw) } }
            }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            runCatching { provider?.unbindAll() }
            runCatching { reader.close() }
            worker.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanWindow(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, s.cancel, tint = Color.White) }
                Text(s.scanAadhaar, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (wrongCode) Text(
                s.notAadhaarQr, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Red.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(12.dp),
            )
            Text(
                s.scanAadhaarHint, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (torch) Marigold else Color.White.copy(alpha = 0.2f), contentColor = if (torch) Ink else Color.White),
                ) { Text(s.torch, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = onPickPhoto, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(10.dp)) {
                    Text(s.choosePhoto, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** One camera frame → the bundled ML Kit QR reader. Always closes the frame. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun readFrame(
    frame: ImageProxy, reader: BarcodeScanner, done: AtomicBoolean,
    accept: (String) -> Boolean, onOther: () -> Unit, onRaw: (String) -> Unit,
) {
    val media = frame.image
    if (media == null || done.get()) { frame.close(); return }
    val image = InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees)
    reader.process(image)
        .addOnSuccessListener { codes ->
            // An Aadhaar letter can carry more than one code: the Aadhaar one is by far the biggest.
            val raw = codes.mapNotNull { it.rawValue }.maxByOrNull { it.length } ?: return@addOnSuccessListener
            if (!accept(raw)) onOther()
            else if (done.compareAndSet(false, true)) onRaw(raw)
        }
        .addOnCompleteListener { frame.close() }
}

/** The dimmed camera view with a clear square, four marigold corner brackets and a moving scan line. */
@Composable
private fun ScanWindow(modifier: Modifier) {
    val sweep = rememberInfiniteTransition(label = "scan")
    val line by sweep.animateFloat(0.06f, 0.94f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "line")
    Canvas(modifier) {
        val side = minOf(size.width, size.height) * 0.72f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2 - size.height * 0.06f
        val window = Rect(left, top, left + side, top + side)
        val r = 18.dp.toPx()
        // Dim everything outside the window.
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(window, CornerRadius(r, r)))
        }
        drawPath(shade, Color.Black.copy(alpha = 0.55f))
        // Four corner brackets.
        val len = side * 0.16f
        val w = 6.dp.toPx()
        val stroke = Stroke(width = w, cap = StrokeCap.Round)
        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply {
                moveTo(x, y + dy * len)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * len, y)
            }
            drawPath(p, Marigold, style = stroke)
        }
        bracket(window.left, window.top, 1f, 1f)
        bracket(window.right, window.top, -1f, 1f)
        bracket(window.left, window.bottom, 1f, -1f)
        bracket(window.right, window.bottom, -1f, -1f)
        // The scan line, with a soft glow.
        val y = window.top + side * line
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Marigold.copy(alpha = 0.35f), Color.Transparent), startY = y - 14.dp.toPx(), endY = y + 14.dp.toPx()),
            topLeft = Offset(window.left + w, y - 14.dp.toPx()), size = Size(side - 2 * w, 28.dp.toPx()),
        )
        drawLine(Marigold, Offset(window.left + w * 2, y), Offset(window.right - w * 2, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}
