package com.example

import android.content.Context
import android.content.SharedPreferences

data class AlarmSettings(
    val intervalValue: Int = 15,
    val intervalUnit: String = "Minutes", // "Minutes" or "Hours"
    val isEnabled: Boolean = false,
    val selectedSound: String = "chime.mp3", // "bell.mp3", "chime.mp3", "beep.mp3", "zen.mp3"
    val restartOnBoot: Boolean = true
) {
    fun getIntervalInMillis(): Long {
        val multiplier = if (intervalUnit == "Hours") 60 * 60 * 1000L else 60 * 1000L
        return intervalValue * multiplier
    }
}

object SettingsRepository {
    private const val PREFS_NAME = "hourly_reminder_prefs"
    private const val KEY_INTERVAL_VALUE = "interval_value"
    private const val KEY_INTERVAL_UNIT = "interval_unit"
    private const val KEY_IS_ENABLED = "is_enabled"
    private const val KEY_SELECTED_SOUND = "selected_sound"
    private const val KEY_RESTART_ON_BOOT = "restart_on_boot"
    private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSettings(context: Context, settings: AlarmSettings) {
        getPrefs(context).edit().apply {
            putInt(KEY_INTERVAL_VALUE, settings.intervalValue)
            putString(KEY_INTERVAL_UNIT, settings.intervalUnit)
            putBoolean(KEY_IS_ENABLED, settings.isEnabled)
            putString(KEY_SELECTED_SOUND, settings.selectedSound)
            putBoolean(KEY_RESTART_ON_BOOT, settings.restartOnBoot)
            apply()
        }
    }

    fun loadSettings(context: Context): AlarmSettings {
        val prefs = getPrefs(context)
        return AlarmSettings(
            intervalValue = prefs.getInt(KEY_INTERVAL_VALUE, 15),
            intervalUnit = prefs.getString(KEY_INTERVAL_UNIT, "Minutes") ?: "Minutes",
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
            selectedSound = prefs.getString(KEY_SELECTED_SOUND, "chime.mp3") ?: "chime.mp3",
            restartOnBoot = prefs.getBoolean(KEY_RESTART_ON_BOOT, true)
        )
    }

    fun saveNextTriggerTime(context: Context, timeMs: Long) {
        getPrefs(context).edit().putLong(KEY_NEXT_TRIGGER_TIME, timeMs).apply()
    }

    fun getNextTriggerTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_NEXT_TRIGGER_TIME, 0L)
    }
}
