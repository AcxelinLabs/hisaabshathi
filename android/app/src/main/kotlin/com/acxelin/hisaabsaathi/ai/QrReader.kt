package com.acxelin.hisaabsaathi.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads QR codes in a photo, on the phone (ML Kit, model bundled in the app). Used for the
 * Aadhaar QR during setup; what it reads is handed to core/AadhaarQr and then dropped.
 */
class QrReader(private val context: Context) {
    suspend fun read(photo: Uri): List<String> = suspendCancellableCoroutine { cont ->
        val image = runCatching { InputImage.fromFilePath(context, photo) }.getOrNull()
        if (image == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        scanner.process(image)
            .addOnSuccessListener { codes -> if (cont.isActive) cont.resume(codes.mapNotNull { it.rawValue }) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
            .addOnCompleteListener { scanner.close() }
    }
}
