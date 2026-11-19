package com.chronos.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/** Theme preference. SYSTEM follows the OS; DARK is the product default look. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** User preferences (spec §4 Configurações), persisted via DataStore (spec §2). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val use24hClock: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val defaultLeadMinutes: Int = 15,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "chronos_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val CLOCK_24 = booleanPreferencesKey("clock_24h")
        val FIRST_DAY = intPreferencesKey("first_day_of_week")
        val LEAD = intPreferencesKey("default_lead")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            use24hClock = p[Keys.CLOCK_24] ?: true,
            firstDayOfWeek = p[Keys.FIRST_DAY]?.let { DayOfWeek.of(it) } ?: DayOfWeek.SUNDAY,
            defaultLeadMinutes = p[Keys.LEAD] ?: 15,
            soundEnabled = p[Keys.SOUND] ?: true,
            vibrationEnabled = p[Keys.VIBRATION] ?: true,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = edit { it[Keys.DYNAMIC] = on }
    suspend fun setClock24h(on: Boolean) = edit { it[Keys.CLOCK_24] = on }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) = edit { it[Keys.FIRST_DAY] = day.value }
    suspend fun setDefaultLead(minutes: Int) = edit { it[Keys.LEAD] = minutes }
    suspend fun setSound(on: Boolean) = edit { it[Keys.SOUND] = on }
    suspend fun setVibration(on: Boolean) = edit { it[Keys.VIBRATION] = on }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
