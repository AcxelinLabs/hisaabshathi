package com.acxelin.hisaabsaathi.work

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acxelin.hisaabsaathi.HisaabApp
import com.acxelin.hisaabsaathi.core.ModelRelease
import com.acxelin.hisaabsaathi.core.ModelUpdates
import com.acxelin.hisaabsaathi.models.SlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Keeps the smart model up to date without a new app (see docs/models/updating-the-model.md):
 *
 * 1. fetch the team's manifest and its signature; use it only if the signature checks out with
 *    the key built into the app, and only a version newer than the one in use, that this app
 *    version can read, pinned to a commit of the team's repo;
 * 2. if the phone never downloaded the smart model, just remember the new version (that is what
 *    it will download, if ever); otherwise download it next to the old one, only with room to
 *    spare, resuming where it stopped;
 * 3. check its size and SHA-256, then load it and ask it a known sentence;
 * 4. switch to it, and delete the old model and anything else left in the folder.
 *
 * Anything that fails leaves the model in use as it was. A version that fails its trial three
 * times is skipped. Each step is a function so tests can stand in for the network and the model.
 */
class ModelUpdater(
    private val dir: File,
    private val publicKey: String,
    private val manifestUrl: String,
    private val appVersion: Int,
    private val prefs: SharedPreferences,
    /** The model in use is fully on the phone. */
    private val installed: () -> Boolean,
    /** A small file (the manifest, its signature); null when it could not be fetched. */
    private val fetch: suspend (String) -> ByteArray?,
    /** Downloads [url] into [target] (via target.part, resuming); true once all [bytes] are there. */
    private val download: suspend (url: String, target: File, bytes: Long) -> Boolean,
    /** Loads the model file and asks it a known sentence: true, false, or null (could not try now). */
    private val tryModel: suspend (File) -> Boolean?,
    /** After the switch: unload the old model, refresh what the screens show. */
    private val switched: suspend (ModelRelease) -> Unit,
    private val note: (String) -> Unit = {},
) {
    /** true: done for now; false: try again soon (offline, interrupted, phone busy). */
    suspend fun run(): Boolean {
        if (publicKey.isBlank()) return true
        val manifest = fetch(manifestUrl) ?: return false
        val sig = fetch("$manifestUrl.sig") ?: return false
        if (!ModelUpdates.verify(manifest, String(sig), publicKey)) {
            note("model manifest: signature does not match; ignored")
            return true
        }
        val release = ModelUpdates.parse(String(manifest))
            ?.let { ModelUpdates.newer(it, SlmModel.ID, appVersion, SlmModel.current.version) }
        if (release == null || prefs.getInt(tries(release), 0) >= MAX_TRIES) {
            SlmModel.pending = null
            SlmModel.cleanUp(dir)
            return true
        }
        // Never downloaded on this phone: the new version is simply what it will get, if it ever does.
        if (!installed()) {
            SlmModel.switchTo(dir, release)
            switched(release)
            SlmModel.cleanUp(dir)
            return true
        }
        val target = File(dir, release.file)
        val part = File(dir, release.file + ".part")
        val have = if (target.exists()) target.length() else if (part.exists()) part.length() else 0L
        // Old and new are both on the phone for a while: only with room for the rest and a margin.
        if (dir.usableSpace < (release.bytes - have) + SPARE_BYTES) {
            note("model v${release.version}: not enough free space, trying next time")
            return true
        }
        SlmModel.pending = release.file
        if (!(target.exists() && target.length() == release.bytes) && !download(release.url, target, release.bytes)) return false
        if (target.length() != release.bytes || ModelUpdates.sha256(target) != release.sha256) {
            note("model v${release.version}: download did not match its fingerprint; deleted")
            target.delete(); part.delete()
            SlmModel.pending = null
            return true
        }
        when (tryModel(target)) {
            null -> return false // the phone was too busy to try it: next time
            false -> {
                val n = prefs.getInt(tries(release), 0) + 1
                prefs.edit().putInt(tries(release), n).apply()
                note("model v${release.version}: did not work on this phone (try $n of $MAX_TRIES)")
                if (n >= MAX_TRIES) { target.delete(); SlmModel.pending = null; SlmModel.cleanUp(dir) }
                return true
            }
            true -> Unit
        }
        SlmModel.switchTo(dir, release)
        SlmModel.pending = null
        switched(release)
        SlmModel.cleanUp(dir)
        note("model: now v${release.version}")
        return true
    }

    private fun tries(r: ModelRelease) = "tries_v${r.version}"

    companion object {
        const val MAX_TRIES = 3
        /** Room left on the phone after a model download. */
        const val SPARE_BYTES = 300_000_000L

        /** A small file, at most 256 KB; null on any failure. */
        suspend fun fetchSmall(url: String): ByteArray? = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 30_000; c.readTimeout = 60_000
                try {
                    if (c.responseCode != 200) return@runCatching null
                    c.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > 256 * 1024) return@runCatching null
                        }
                        out.toByteArray()
                    }
                } finally { c.disconnect() }
            }.getOrNull()
        }

        /** Resumable download into target.part, renamed to [target] when complete. */
        suspend fun downloadResuming(url: String, target: File, bytes: Long): Boolean = withContext(Dispatchers.IO) {
            val part = File(target.path + ".part")
            try {
                var have = if (part.exists()) part.length() else 0L
                if (have > bytes) { part.delete(); have = 0 }
                if (have < bytes) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 30_000; c.readTimeout = 120_000
                    if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                    try {
                        val append = when (c.responseCode) {
                            206 -> true
                            200 -> false // the server sent it all again: start over
                            else -> throw IOException("HTTP ${c.responseCode}")
                        }
                        c.inputStream.use { input ->
                            java.io.FileOutputStream(part, append).use { out -> input.copyTo(out, 1 shl 16) }
                        }
                    } finally { c.disconnect() }
                }
                if (part.length() != bytes) return@withContext false
                target.delete()
                part.renameTo(target)
            } catch (e: IOException) {
                false
            }
        }
    }
}

class ModelUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if ((applicationContext as HisaabApp).modelUpdater.run()) Result.success() else Result.retry()
}

object ModelUpdateSchedule {
    private const val NAME = "model_update_weekly"

    /** About once a week, on Wi-Fi, battery and storage not low. Never throws. */
    fun schedule(context: Context) = runCatching {
        val request = PeriodicWorkRequestBuilder<ModelUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }.let { }
}
