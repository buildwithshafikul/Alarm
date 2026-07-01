package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Alarm triggered!")

        // 1. Acquire WakeLock to keep CPU running
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HourlyReminder:AlarmWakeLock"
        )
        wakeLock.acquire(15 * 1000L) // Safe limit: 15 seconds

        val settings = SettingsRepository.loadSettings(context)
        if (!settings.isEnabled) {
            Log.d(TAG, "Alarm was triggered but settings is disabled. Skipping.")
            return
        }

        // 2. Play Selected Sound
        playSound(context, settings.selectedSound)

        // 3. Vibrate device
        vibrate(context)

        // 4. Show high-priority notification
        val friendlyTime = "${settings.intervalValue} ${settings.intervalUnit.lowercase()}"
        NotificationHelper.showAlarmNotification(
            context,
            "Hourly Reminder Alert!",
            "Your interval of $friendlyTime has elapsed."
        )

        // 5. Update Foreground Service status notification text
        ForegroundService.startService(context)

        // 6. Schedule NEXT alarm
        AlarmScheduler.scheduleNextAlarm(context)
    }

    private fun playSound(context: Context, soundFileName: String) {
        val mediaPlayer = MediaPlayer()
        try {
            val assetManager = context.assets
            // Attempt to open the custom sound asset
            val afd = assetManager.openFd("audio/$soundFileName")
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer.isLooping = false // Loop only once
            mediaPlayer.prepare()
            mediaPlayer.start()
            
            // Release resource once finished playing
            mediaPlayer.setOnCompletionListener {
                Log.d(TAG, "Asset playback complete. Releasing MediaPlayer.")
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play asset sound 'audio/$soundFileName', using default ringtone", e)
            try {
                // Release player if prepared
                mediaPlayer.release()
                
                // Fallback to playing the system alarm ringtone
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, defaultUri)
                ringtone?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        it.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    it.play()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed playing system default ringtone", ex)
            }
        }
    }

    private fun vibrate(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500), // Start immediately, vibrate 500ms, sleep 200ms, vibrate 500ms
                        -1 // No repeat
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(
                    longArrayOf(0, 500, 200, 500),
                    -1
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
}
