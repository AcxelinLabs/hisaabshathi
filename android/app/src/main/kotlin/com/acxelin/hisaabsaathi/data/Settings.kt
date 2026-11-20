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
     * The worker's answer to "help improve HisaabSaathi?": null until asked (at setup, or once
     * after updating), then their choice. Only true sends anything.
     */
    val share: Boolean? = null,
    /** How many exchanges this install has sent. */
    val sharedCount: Int = 0,
)

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
    private val installId = stringPreferencesKey("install_id")
    private val installToken = stringPreferencesKey("install_token")
    private val pendingDeletes = stringSetPreferencesKey("pending_deletes")

    val flow: Flow<Settings> = context.dataStore.data.map {
        Settings(
            lang = Lang.fromCode(it[lang]),
            region = it[region] ?: "",
            onboarded = it[onboarded] ?: false,
            reminders = it[reminders] ?: true,
            monthlyGoalPaise = it[goal] ?: 0,
            // A yes counts only for the question as it was then: a newer question is asked again.
            share = it[share]?.let { yes -> if (yes && (it[shareConsent] ?: 1) < Improve.CONSENT_VERSION) null else yes },
            sharedCount = it[sharedCount] ?: 0,
        )
    }

    suspend fun setLang(v: Lang) = context.dataStore.edit { it[lang] = v.code }
    suspend fun setRegion(v: String) = context.dataStore.edit { it[region] = v }
    suspend fun setOnboarded(v: Boolean = true) = context.dataStore.edit { it[onboarded] = v }
    suspend fun setReminders(v: Boolean) = context.dataStore.edit { it[reminders] = v }
    suspend fun setGoal(paise: Long) = context.dataStore.edit { it[goal] = paise }
    suspend fun setShare(v: Boolean) = context.dataStore.edit { it[share] = v; if (v) it[shareConsent] = Improve.CONSENT_VERSION }

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
    }

    /** Deletions still to send, as (install id, token). */
    suspend fun pendingDeletes(): List<Pair<String, String>> = context.dataStore.data.first()[pendingDeletes].orEmpty()
        .mapNotNull { e -> e.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }

    suspend fun deleteDone(id: String) = context.dataStore.edit { p ->
        p[pendingDeletes] = p[pendingDeletes].orEmpty().filterNot { it.startsWith("$id ") }.toSet()
    }
}
