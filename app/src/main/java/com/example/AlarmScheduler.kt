package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val ALARM_REQUEST_CODE = 999

    fun scheduleNextAlarm(context: Context) {
        val settings = SettingsRepository.loadSettings(context)
        if (!settings.isEnabled) {
            cancelAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intervalMs = settings.getIntervalInMillis()
        val triggerTimeMs = System.currentTimeMillis() + intervalMs

        // Save next scheduled trigger time for UI display
        SettingsRepository.saveNextTriggerTime(context, triggerTimeMs)

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled exact alarm at $triggerTimeMs")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled inexact alarm (no permission) at $triggerTimeMs")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm at $triggerTimeMs")
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled standard alarm at $triggerTimeMs")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact alarm, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        SettingsRepository.saveNextTriggerTime(context, 0L)
        Log.d(TAG, "Cancelled alarm")
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
}
