package com.acxelin.hisaabsaathi.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.acxelin.hisaabsaathi.core.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The worker's details from setup (name, age, state, district, PIN) and their photo. Kept only
 * on this phone, in two small files encrypted with an AES key that lives in the Android Keystore
 * (its own key, separate from the ledger's). Never uploaded, never part of "Help improve
 * HisaabSaathi", never backed up to the cloud.
 *
 * If the Keystore is not available (tests on a PC), they are kept in memory only.
 */
class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "profile.enc")
    private val photoFile = File(context.filesDir, "profile_photo.enc")
    private val _flow = MutableStateFlow(Profile())
    val flow: StateFlow<Profile> = _flow

    /** The worker's photo as a small JPEG (about 480 × 480), or null. */
    private val _photo = MutableStateFlow<ByteArray?>(null)
    val photo: StateFlow<ByteArray?> = _photo

    /** Reads what was saved, once, off the main thread (called when the app starts). */
    suspend fun load() = withContext(Dispatchers.IO) {
        // Never overwrite details saved meanwhile.
        runCatching { read() }.getOrNull()?.let { if (_flow.value == Profile()) _flow.value = it }
        runCatching { decrypt(photoFile) }.getOrNull()?.let { if (_photo.value == null) _photo.value = it }
    }

    suspend fun save(p: Profile) {
        _flow.value = p
        withContext(Dispatchers.IO) { runCatching { write(p) } }
    }

    suspend fun savePhoto(jpeg: ByteArray?) {
        _photo.value = jpeg
        withContext(Dispatchers.IO) {
            if (jpeg == null) photoFile.delete() else runCatching { encrypt(photoFile, jpeg) }
        }
    }

    suspend fun clear() {
        _flow.value = Profile()
        _photo.value = null
        withContext(Dispatchers.IO) { file.delete(); photoFile.delete() }
    }

    private fun read(): Profile? {
        val o = JSONObject(String(decrypt(file) ?: return null, Charsets.UTF_8))
        return Profile(
            // Names saved by older versions may hold the whole sentence ("mera naam … hai").
            name = o.optString("name").let { if (it.isBlank()) "" else com.acxelin.hisaabsaathi.core.SpokenDetails.name(it) },
            age = if (o.has("age") && !o.isNull("age")) o.optInt("age") else null,
            state = o.optString("state"),
            district = o.optString("district"),
            pin = o.optString("pin"),
        )
    }

    private fun write(p: Profile) {
        val json = JSONObject()
            .put("name", p.name).put("age", p.age ?: JSONObject.NULL)
            .put("state", p.state).put("district", p.district).put("pin", p.pin)
            .toString().toByteArray(Charsets.UTF_8)
        encrypt(file, json)
    }

    private fun decrypt(f: File): ByteArray? {
        if (!f.exists()) return null
        val bytes = f.readBytes()
        if (bytes.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }

    private fun encrypt(f: File, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(plain))
        tmp.renameTo(f)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        private const val ALIAS = "hisaab_profile_key"

        /**
         * A camera photo made into a small square JPEG for the profile: turned upright, the
         * middle of the picture kept, about 480 × 480 (30–60 KB). The camera's file is deleted.
         */
        fun selfie(path: String, side: Int = 480): ByteArray? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
            val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (raw == null) null else {
                val turn = when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                val crop = minOf(raw.width, raw.height)
                val scale = side.toFloat() / crop
                val m = Matrix().apply { postScale(scale, scale); postRotate(turn) }
                val square = Bitmap.createBitmap(raw, (raw.width - crop) / 2, (raw.height - crop) / 2, crop, crop, m, true)
                ByteArrayOutputStream().use { out ->
                    square.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    if (square !== raw) square.recycle()
                    raw.recycle()
                    out.toByteArray()
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { File(path).delete() }
        }
    }
}
