package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class ForegroundService : Service() {

    companion object {
        private const val TAG = "ForegroundService"
        
        fun startService(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand triggered")
        
        val settings = SettingsRepository.loadSettings(this)
        val statusText = if (settings.isEnabled) {
            "Reminder is scheduled every ${settings.intervalValue} ${settings.intervalUnit.lowercase()}."
        } else {
            "No active timers scheduled."
        }

        val notification = NotificationHelper.buildServiceNotification(this, statusText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ / 14+ requires service types. We use FOREGROUND_SERVICE_TYPE_SPECIAL_USE or mediaPlayback as requested
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        }

        // If service is killed, restart it to keep alarms working reliably
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "ForegroundService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
