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
            if (settings.isEnabled && settings.restartOnBoot) {
                Log.d(TAG, "Hourly Reminder was enabled. Rescheduling alarm and restarting ForegroundService.")
                
                // Reschedule alarm
                AlarmScheduler.scheduleNextAlarm(context)
                
                // Start active state service
                ForegroundService.startService(context)
            } else {
                Log.d(TAG, "Hourly Reminder was not enabled or auto-restart is disabled. Skipping reschedule.")
            }
        }
    }
}
