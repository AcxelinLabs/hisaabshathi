package com.acxelin.hisaabsaathi

import android.app.Application
import com.acxelin.hisaabsaathi.ai.Scanner
import com.acxelin.hisaabsaathi.ai.SlmEngine
import com.acxelin.hisaabsaathi.core.WagePack
import com.acxelin.hisaabsaathi.core.Wages
import com.acxelin.hisaabsaathi.data.LedgerDb
import com.acxelin.hisaabsaathi.data.LedgerRepository
import com.acxelin.hisaabsaathi.data.SettingsStore
import com.acxelin.hisaabsaathi.export.Exporter
import com.acxelin.hisaabsaathi.models.Catalog
import com.acxelin.hisaabsaathi.models.PackManager
import com.acxelin.hisaabsaathi.speech.OnDeviceStt
import com.acxelin.hisaabsaathi.speech.Speaker
import com.acxelin.hisaabsaathi.speech.SystemOnDeviceStt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class HisaabApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(this) }
    val ledger by lazy { LedgerRepository(openDb().entries()) }

    /** Encrypted on a phone; tests replace it with an in-memory database. */
    protected open fun openDb(): LedgerDb = LedgerDb.open(this)

    val packs by lazy { PackManager(this, scope) }
    val stt by lazy { OnDeviceStt(packs) }
    val systemStt by lazy { SystemOnDeviceStt(this) }
    val speaker by lazy { Speaker(this) }
    val slm by lazy { SlmEngine(this, packs) }
    val scanner by lazy { Scanner(this) }
    val exporter by lazy { Exporter(this) }
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.BUNDLED_MODEL) packs.installBundled(this, Catalog.slmHisaab, "hisaabsaathi-qwen05-ft-q8.task")
    }

    val wagePack: WagePack by lazy {
        runCatching { Wages.parse(assets.open("wage_pack_in.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(WagePack(0, ""))
    }
}
