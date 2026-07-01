package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed detected.")
            val settings = SettingsRepository.loadSettings(context)
            var hasScheduledAnything = false

            if (settings.isEnabled && settings.restartOnBoot) {
                Log.d(TAG, "Hourly Reminder was enabled. Rescheduling reminder alarm.")
                AlarmScheduler.scheduleNextAlarm(context)
                hasScheduledAnything = true
            }

            if (settings.isSalamEnabled && settings.restartOnBoot) {
                Log.d(TAG, "Hourly Salam was enabled. Rescheduling hourly alarm.")
                AlarmScheduler.scheduleNextHourlyAlarm(context)
                hasScheduledAnything = true
            }

            if (hasScheduledAnything) {
                Log.d(TAG, "Starting ForegroundService after boot reschedule.")
                ForegroundService.startService(context)
            }
        }
    }
}
