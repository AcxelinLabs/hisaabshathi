package com.acxelin.hisaabsaathi.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device text recognition for bills and advance slips (Latin and Devanagari scripts; the
 * model ships inside the app). Odia script is not supported by this recogniser yet.
 */
class Scanner(private val context: Context) {
    suspend fun text(photo: Uri): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        runCatching { InputImage.fromFilePath(context, photo) }
            .onFailure { cont.resume("") }
            .onSuccess { image ->
                recognizer.process(image)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
                    .addOnCompleteListener { recognizer.close() }
            }
    }
}
