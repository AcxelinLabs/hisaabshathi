package com.acxelin.hisaabsaathi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.acxelin.hisaabsaathi.core.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val lang: Lang = Lang.HI,
    val region: String = "",
    val onboarded: Boolean = false,
    val reminders: Boolean = true,
    val monthlyGoalPaise: Long = 0,
    /** Keep a local log of the worker's fixes (core/Corrections.kt); shared only if they choose. */
    val keepCorrections: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("settings")

/** Preferences only. Nothing about the worker's money is stored here. */
class SettingsStore(private val context: Context) {
    private val lang = stringPreferencesKey("lang")
    private val region = stringPreferencesKey("region")
    private val onboarded = booleanPreferencesKey("onboarded")
    private val reminders = booleanPreferencesKey("reminders")
    private val goal = longPreferencesKey("monthly_goal_paise")
    private val keepCorrections = booleanPreferencesKey("keep_corrections")

    val flow: Flow<Settings> = context.dataStore.data.map {
        Settings(
            lang = Lang.fromCode(it[lang]),
            region = it[region] ?: "",
            onboarded = it[onboarded] ?: false,
            reminders = it[reminders] ?: true,
            monthlyGoalPaise = it[goal] ?: 0,
            keepCorrections = it[keepCorrections] ?: true,
        )
    }

    suspend fun setLang(v: Lang) = context.dataStore.edit { it[lang] = v.code }
    suspend fun setRegion(v: String) = context.dataStore.edit { it[region] = v }
    suspend fun setOnboarded() = context.dataStore.edit { it[onboarded] = true }
    suspend fun setReminders(v: Boolean) = context.dataStore.edit { it[reminders] = v }
    suspend fun setGoal(paise: Long) = context.dataStore.edit { it[goal] = paise }
    suspend fun setKeepCorrections(v: Boolean) = context.dataStore.edit { it[keepCorrections] = v }
}
