package com.acxelin.hisaabsaathi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.acxelin.hisaabsaathi.core.Improve
import com.acxelin.hisaabsaathi.core.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val lang: Lang = Lang.HI,
    val region: String = "",
    val onboarded: Boolean = false,
    val reminders: Boolean = true,
    val monthlyGoalPaise: Long = 0,
    /**
     * "Help improve HisaabSaathi": on unless the worker turned it off (Settings, or the card
     * that tells them about it). Only true records and sends anything.
     */
    val share: Boolean? = true,
    /** The worker has been told, once, that their voice clips are sent and how to stop it. */
    val shareNoticed: Boolean = false,
    /** How many exchanges this install has sent. */
    val sharedCount: Int = 0,
    /** The version of setup last finished (see [SETUP_VERSION]). */
    val setupDone: Int = 0,
    /** Epoch day the app was first set up (0: not known). */
    val firstUseDay: Long = 0,
    /** "Help improve" is not asked on Home before this epoch day ("Later" sets it a week on). */
    val shareAskAfterDay: Long = 0,
) {
    /**
     * Setup shows on a new phone, and once more after an update that adds to it (0.9.3: your
     * details and the language choice), keeping everything already chosen.
     */
    val needsSetup: Boolean get() = !onboarded || setupDone < SETUP_VERSION

    companion object {
        const val SETUP_VERSION = 2
    }
}

private val Context.dataStore by preferencesDataStore("settings")

/** Preferences only. Nothing about the worker's money is stored here. */
class SettingsStore(private val context: Context) {
    private val lang = stringPreferencesKey("lang")
    private val region = stringPreferencesKey("region")
    private val onboarded = booleanPreferencesKey("onboarded")
    private val reminders = booleanPreferencesKey("reminders")
    private val goal = longPreferencesKey("monthly_goal_paise")
    private val share = booleanPreferencesKey("share_improve")
    private val shareConsent = intPreferencesKey("share_consent_version")
    private val sharedCount = intPreferencesKey("shared_count")
    private val setupDone = intPreferencesKey("setup_done")
    private val installId = stringPreferencesKey("install_id")
    private val installToken = stringPreferencesKey("install_token")
    private val pendingDeletes = stringSetPreferencesKey("pending_deletes")
    private val firstUse = longPreferencesKey("first_use_day")
    private val shareAskAfter = longPreferencesKey("share_ask_after_day")
    private val shareNoticed = booleanPreferencesKey("share_noticed")
    private val usage = stringPreferencesKey("usage_counts")
    private val usageDay = longPreferencesKey("usage_last_day")

    val flow: Flow<Settings> = context.dataStore.data.map {
        Settings(
            lang = Lang.fromCode(it[lang]),
            region = it[region] ?: "",
            onboarded = it[onboarded] ?: false,
            reminders = it[reminders] ?: true,
            monthlyGoalPaise = it[goal] ?: 0,
            // On by default; off only when the worker turned it off.
            share = it[share] != false,
            shareNoticed = it[shareNoticed] ?: false,
            sharedCount = it[sharedCount] ?: 0,
            setupDone = it[setupDone] ?: 0,
            firstUseDay = it[firstUse] ?: 0,
            shareAskAfterDay = it[shareAskAfter] ?: 0,
        )
    }

    /** Every setting back to its default, as on a new install (tests start from here). */
    suspend fun clear() = context.dataStore.edit { it.clear() }

    suspend fun setLang(v: Lang) = context.dataStore.edit { it[lang] = v.code }
    suspend fun setRegion(v: String) = context.dataStore.edit { it[region] = v }
    suspend fun setOnboarded(v: Boolean = true) = context.dataStore.edit { it[onboarded] = v }
    suspend fun setSetupDone(v: Int) = context.dataStore.edit { it[setupDone] = v }
    suspend fun setReminders(v: Boolean) = context.dataStore.edit { it[reminders] = v }
    suspend fun setFirstUse(day: java.time.LocalDate, onlyIfUnset: Boolean = false) = context.dataStore.edit {
        if (!onlyIfUnset || it[firstUse] == null) it[firstUse] = day.toEpochDay()
    }
    suspend fun setShareAskAfter(day: java.time.LocalDate) = context.dataStore.edit { it[shareAskAfter] = day.toEpochDay() }
    suspend fun setGoal(paise: Long) = context.dataStore.edit { it[goal] = paise }
    /** Saying no (or turning it off) also drops the weekly counts not yet sent. */
    suspend fun setShare(v: Boolean) = context.dataStore.edit {
        it[share] = v
        it[shareNoticed] = true
        if (v) it[shareConsent] = Improve.CONSENT_VERSION else { it.remove(usage); it.remove(usageDay) }
    }

    /** The worker heard and saw the notice, and left it on. */
    suspend fun setShareNoticed() = context.dataStore.edit { it[shareNoticed] = true }

    // ---- weekly counts (core/Usage.kt), only while the worker's yes covers them

    /** Counts [events] for this week, and the day as a day the app was used. Nothing without consent. */
    suspend fun countUsage(today: java.time.LocalDate, vararg events: String) = context.dataStore.edit {
        if (it[share] == false) return@edit
        val week = com.acxelin.hisaabsaathi.core.Usage.week(today)
        var store = com.acxelin.hisaabsaathi.core.Usage.read(it[usage])
        for (e in events) store = com.acxelin.hisaabsaathi.core.Usage.add(store, week, e)
        if (it[usageDay] != today.toEpochDay()) {
            store = com.acxelin.hisaabsaathi.core.Usage.add(store, week, com.acxelin.hisaabsaathi.core.Usage.Event.DAYS)
            it[usageDay] = today.toEpochDay()
        }
        it[usage] = com.acxelin.hisaabsaathi.core.Usage.write(store)
    }

    /** Finished weeks waiting to be sent. */
    suspend fun usageToSend(today: java.time.LocalDate, appVersion: String): List<com.acxelin.hisaabsaathi.core.WeekCounts> =
        context.dataStore.data.first().let { p ->
            if (p[share] == false) emptyList()
            else com.acxelin.hisaabsaathi.core.Usage.finished(com.acxelin.hisaabsaathi.core.Usage.read(p[usage]), today, Improve.CONSENT_VERSION, appVersion)
        }

    /** These weeks reached the team: they leave the phone. */
    suspend fun usageSent(weeks: Collection<String>) = context.dataStore.edit {
        it[usage] = com.acxelin.hisaabsaathi.core.Usage.write(com.acxelin.hisaabsaathi.core.Usage.read(it[usage]) - weeks.toSet())
    }

    // ---- this install's anonymous identity at the upload service (never shown, never in Settings)

    /** The install id and token, if registered. */
    suspend fun install(): Pair<String, String>? = context.dataStore.data.first().let { p ->
        val id = p[installId]; val token = p[installToken]
        if (id.isNullOrEmpty() || token.isNullOrEmpty()) null else id to token
    }

    suspend fun setInstall(id: String, token: String) = context.dataStore.edit { it[installId] = id; it[installToken] = token }

    /** The service no longer knows the token: register again next time. */
    suspend fun forgetInstall() = context.dataStore.edit { it.remove(installId); it.remove(installToken) }

    /** Counts what was sent, if [id] is still this install (not deleted meanwhile). */
    suspend fun addShared(id: String, n: Int) = context.dataStore.edit { if (it[installId] == id) it[sharedCount] = (it[sharedCount] ?: 0) + n }

    /**
     * "Delete what I shared": this install's id and token move to a list the uploader works
     * through when online; the next upload starts a new, unrelated install.
     */
    suspend fun requestDelete() = context.dataStore.edit {
        val id = it[installId]; val token = it[installToken]
        if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) it[pendingDeletes] = (it[pendingDeletes] ?: emptySet()) + "$id $token"
        it.remove(installId); it.remove(installToken); it[sharedCount] = 0
        it.remove(usage) // counts not yet sent go too
    }

    /** Deletions still to send, as (install id, token). */
    suspend fun pendingDeletes(): List<Pair<String, String>> = context.dataStore.data.first()[pendingDeletes].orEmpty()
        .mapNotNull { e -> e.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }

    suspend fun deleteDone(id: String) = context.dataStore.edit { p ->
        p[pendingDeletes] = p[pendingDeletes].orEmpty().filterNot { it.startsWith("$id ") }.toSet()
    }
}
