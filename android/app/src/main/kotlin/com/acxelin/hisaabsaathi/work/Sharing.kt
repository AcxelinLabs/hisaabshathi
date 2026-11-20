package com.acxelin.hisaabsaathi.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acxelin.hisaabsaathi.HisaabApp
import com.acxelin.hisaabsaathi.core.Improve
import com.acxelin.hisaabsaathi.core.Seal
import com.acxelin.hisaabsaathi.core.ShareClient
import com.acxelin.hisaabsaathi.core.ShareServer
import com.acxelin.hisaabsaathi.data.SampleQueue
import com.acxelin.hisaabsaathi.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends what the worker agreed to share, and carries out "delete what I shared". Runs in the
 * background (WorkManager), on Wi-Fi with the battery not low unless the worker taps "Send now".
 * One run at a time, so a deletion never overtakes an upload still in flight.
 */
class Uploader(
    private val settings: SettingsStore,
    private val queue: SampleQueue,
    /** null when this build has no upload service: samples wait on the phone (at most [Improve.LIMIT]). */
    private val server: ShareServer?,
    /** The team's key: batches leave the phone sealed, readable only by the training pipeline. */
    private val seal: Seal?,
    private val appVersion: String,
) {
    private val lock = Mutex()

    /** true when done for now; false to try again later (offline, or the service is down). */
    suspend fun run(maxBatches: Int = 8): Boolean = lock.withLock {
        val server = server ?: return true
        val seal = seal ?: return true // never send in the clear
        withContext(Dispatchers.IO) {
            try {
                for ((id, token) in settings.pendingDeletes()) {
                    try {
                        server.deleteInstall(id, token)
                    } catch (e: ShareClient.Refused) {
                        if (e.code != 404 && e.code != 401) throw e // already gone: nothing left to delete
                    }
                    settings.deleteDone(id)
                }
                if (settings.flow.first().share != true) return@withContext true
                repeat(maxBatches) { round ->
                    val batch = queue.oldest(Improve.BATCH)
                    // Finished weeks' counts go with the first upload of a run (alone if nothing else waits).
                    val weeks = if (round == 0) settings.usageToSend(java.time.LocalDate.now(), appVersion) else emptyList()
                    if (batch.isEmpty() && weeks.isEmpty()) return@withContext true
                    val (id, token) = settings.install() ?: server.register(appVersion).also { settings.setInstall(it.installId, it.token) }
                        .let { it.installId to it.token }
                    val clips = batch.filter { it.hasAudio }.associate { it.id to queue.audio(it.id) }
                    val sealed = seal.seal(Improve.batch(batch, { clips[it] }, appVersion, System.currentTimeMillis(), weeks))
                    try {
                        server.upload(token, sealed, batch.size + weeks.size)
                    } catch (e: ShareClient.Refused) {
                        if (e.code == 401) settings.forgetInstall() // the service forgot this install: register again
                        throw e
                    }
                    queue.delete(batch.map { it.id })
                    settings.usageSent(weeks.map { it.week })
                    settings.addShared(id, batch.size)
                }
                true
            } catch (e: IOException) {
                false
            }
        }
    }
}

class ShareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if ((applicationContext as HisaabApp).uploader.run()) Result.success() else Result.retry()
}

object Sharing {
    private const val DAILY = "share_daily"
    private const val NOW = "share_now"

    /** Twice a day, on Wi-Fi, battery not low. Never throws. */
    fun schedule(context: Context) = runCatching {
        val request = PeriodicWorkRequestBuilder<ShareWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }.let { }

    /** As soon as there is any internet ("Send now", or a deletion the worker asked for). */
    fun now(context: Context) = runCatching {
        val request = OneTimeWorkRequestBuilder<ShareWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }.let { }

    fun cancel(context: Context) = runCatching { WorkManager.getInstance(context).cancelUniqueWork(DAILY) }.let { }
}
