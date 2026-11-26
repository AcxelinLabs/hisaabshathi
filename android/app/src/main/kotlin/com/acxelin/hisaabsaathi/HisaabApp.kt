package com.acxelin.hisaabsaathi

import android.app.Application
import com.acxelin.hisaabsaathi.ai.Scanner
import com.acxelin.hisaabsaathi.ai.SlmEngine
import com.acxelin.hisaabsaathi.core.WagePack
import com.acxelin.hisaabsaathi.core.Wages
import com.acxelin.hisaabsaathi.data.CorrectionLog
import com.acxelin.hisaabsaathi.data.LedgerDb
import com.acxelin.hisaabsaathi.data.LedgerRepository
import com.acxelin.hisaabsaathi.data.SampleQueue
import com.acxelin.hisaabsaathi.core.Seal
import com.acxelin.hisaabsaathi.core.ShareClient
import com.acxelin.hisaabsaathi.core.ShareServer
import com.acxelin.hisaabsaathi.work.Sharing
import com.acxelin.hisaabsaathi.work.Uploader
import kotlinx.coroutines.flow.first
import com.acxelin.hisaabsaathi.data.SettingsStore
import com.acxelin.hisaabsaathi.export.Exporter
import com.acxelin.hisaabsaathi.models.PackManager
import com.acxelin.hisaabsaathi.speech.OnDeviceStt
import com.acxelin.hisaabsaathi.speech.Speaker
import com.acxelin.hisaabsaathi.speech.SystemOnDeviceStt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class HisaabApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(this) }
    private val db by lazy { openDb() }
    val ledger by lazy { LedgerRepository(db.entries()) }
    val corrections by lazy { CorrectionLog(db.corrections()) }
    val samples by lazy { SampleQueue(db.samples()) }

    /** The team's upload service; none in builds made without one (samples then wait on the phone). */
    protected open fun shareServer(): ShareServer? =
        BuildConfig.SHARE_URL.takeIf { it.isNotBlank() }?.let { runCatching { ShareClient(it) }.getOrNull() }

    /** The team's public key: every batch is sealed to it. Without one, nothing is sent. */
    protected open fun shareSeal(): Seal? = Seal.fromBase64(BuildConfig.SHARE_KEY)

    val uploader by lazy { Uploader(settings, samples, shareServer(), shareSeal(), BuildConfig.VERSION_NAME) }

    /** This build can send: it has both the service's address and the team's key. */
    val canShare: Boolean get() = shareServer() != null && shareSeal() != null

    override fun onCreate() {
        super.onCreate()
        // Sending is background work; pick it up again after an update or a restart.
        scope.launch { pins } // read the PIN directory early, off the main thread
        scope.launch {
            runCatching {
                // The day the app was first used (installs from before this was kept: today).
                // Written here, off the main thread: a settings change started from a screen
                // runs on the main thread and can be left half-done when the screen goes.
                settings.setFirstUse(java.time.LocalDate.now(), onlyIfUnset = true)
                if (settings.flow.first().share == true) Sharing.schedule(this@HisaabApp)
                if (settings.pendingDeletes().isNotEmpty()) Sharing.now(this@HisaabApp)
            }
        }
    }

    /** One approximate location fix (only for "Use my location"); tests give a fixed position. */
    open suspend fun oneLocation(): android.location.Location? = com.acxelin.hisaabsaathi.data.OneLocation.get(this)

    /** Encrypted on a phone; tests replace it with an in-memory database. */
    protected open fun openDb(): LedgerDb = LedgerDb.open(this)

    val packs by lazy { PackManager(this, scope) }
    val stt by lazy { OnDeviceStt(packs) }
    val systemStt by lazy { SystemOnDeviceStt(this) }
    val speaker by lazy { Speaker(this, packs) }
    val slm by lazy { SlmEngine(this, packs) }
    val scanner by lazy { Scanner(this) }

    /** Tests give what the worker "said"; on a phone this is null and the speech engines listen. */
    open val testListener: (suspend () -> com.acxelin.hisaabsaathi.speech.Heard)? = null

    /** The "listening" and "got it" sounds. */
    val earcons by lazy { com.acxelin.hisaabsaathi.speech.Earcons(this) }

    /** The text on a photo, read on the phone (tests give their own). */
    open suspend fun readPhoto(photo: android.net.Uri): String = scanner.text(photo)
    val qr by lazy { com.acxelin.hisaabsaathi.ai.QrReader(this) }
    /** The worker's details and photo from setup, encrypted on the phone. */
    val profile by lazy { com.acxelin.hisaabsaathi.data.ProfileStore(this).also { p -> scope.launch { p.load() } } }

    /** Start the voice downloads as soon as a language is chosen in setup (tests turn this off: no network). */
    open val autoDownloadVoice: Boolean = true

    /** The welcome screen and its ting when the app opens (tests turn it off). */
    open val showWelcome: Boolean = true

    /**
     * Seconds with nothing happening (the app silent, not listening, nobody touching) before
     * what is in progress is saved and the app is put away. 0: never (tests).
     */
    open val idleSeconds: Int = 5

    /** Every PIN code with its state and district (India Post's directory), for filling in details on the phone. */
    val pins: com.acxelin.hisaabsaathi.core.PinDirectory by lazy {
        runCatching { com.acxelin.hisaabsaathi.core.PinDirectory.parse(assets.open("pin_in.txt").bufferedReader().use { it.readText() }) }
            .getOrDefault(com.acxelin.hisaabsaathi.core.PinDirectory.EMPTY)
    }
    val exporter by lazy { Exporter(this) }
    /** Android is short of memory: drop the speech model (it reloads on the next listen). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) scope.launch { runCatching { stt.release() } }
    }

    val wagePack: WagePack by lazy {
        runCatching { Wages.parse(assets.open("wage_pack_in.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(WagePack(0, ""))
    }
}
