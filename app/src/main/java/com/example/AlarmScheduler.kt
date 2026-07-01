package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    
    const val ACTION_HOURLY = "com.example.ACTION_HOURLY"
    
    private const val ALARM_REQUEST_CODE_HOURLY = 1000

    fun scheduleNextHourlyAlarm(context: Context) {
        val settings = SettingsRepository.loadSettings(context)
        if (!settings.isSalamEnabled) {
            cancelHourlyAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Calculate the next top of the hour
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val triggerTimeMs = calendar.timeInMillis

        // Save next scheduled trigger time for UI display
        SettingsRepository.saveNextTriggerTime(context, triggerTimeMs)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_HOURLY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_HOURLY,
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
                        Log.d(TAG, "Scheduled exact hourly alarm at $triggerTimeMs (${calendar.time})")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTimeMs,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled inexact hourly alarm at $triggerTimeMs (${calendar.time})")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact hourly alarm at $triggerTimeMs (${calendar.time})")
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMs,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled standard hourly alarm at $triggerTimeMs (${calendar.time})")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling exact hourly alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelHourlyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_HOURLY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE_HOURLY,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        SettingsRepository.saveNextTriggerTime(context, 0L)
        Log.d(TAG, "Cancelled hourly alarm")
    }

    fun updateServiceState(context: Context) {
        val settings = SettingsRepository.loadSettings(context)
        if (settings.isSalamEnabled) {
            ForegroundService.startService(context)
        } else {
            ForegroundService.stopService(context)
        }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
}
