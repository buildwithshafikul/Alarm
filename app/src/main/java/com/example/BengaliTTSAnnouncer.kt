package com.example

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class BengaliTTSAnnouncer(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeResult = tts?.setLanguage(Locale("bn", "BD"))
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("BengaliTTSAnnouncer", "Bengali language not supported. Falling back to default locale.")
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("BengaliTTSAnnouncer", "TTS Started speaking")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("BengaliTTSAnnouncer", "TTS Finished speaking, releasing on main thread...")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            release()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e("BengaliTTSAnnouncer", "TTS speaking error")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            release()
                        }
                    }
                })

                pendingText?.let {
                    speakNow(it)
                    pendingText = null
                }
            } else {
                Log.e("BengaliTTSAnnouncer", "TTS initialization failed")
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            speakNow(text)
        } else {
            pendingText = text
        }
    }

    private fun speakNow(text: String) {
        try {
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "announcement_salam")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "announcement_salam")
        } catch (e: Exception) {
            Log.e("BengaliTTSAnnouncer", "Error speaking text", e)
            release()
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e("BengaliTTSAnnouncer", "Error releasing TTS", e)
        }
    }

    companion object {
        fun getBengaliTimeSpeech(): String {
            val calendar = Calendar.getInstance()
            val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
            val hour12 = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            
            val displayHour = if (hour12 == 0) 12 else hour12
            
            val amPmStr = if (hour24 < 6) {
                "ভোর"
            } else if (hour24 < 12) {
                "সকাল"
            } else if (hour24 < 15) {
                "দুপুর"
            } else if (hour24 < 18) {
                "বিকাল"
            } else if (hour24 < 20) {
                "সন্ধ্যা"
            } else {
                "রাত"
            }
            
            val minuteStr = if (minute == 0) {
                ""
            } else {
                "$minute মিনিট"
            }
            
            return if (minute == 0) {
                "আসসালামু আলাইকুম। এখন সময় $amPmStr $displayHour টা।"
            } else {
                "আসসালামু আলাইকুম। এখন সময় $amPmStr $displayHour টা বেজে $minuteStr।"
            }
        }
    }
}
