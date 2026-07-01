package com.example

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder {
    private var recorder: MediaRecorder? = null

    fun startRecording(context: Context, outputFile: File) {
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            Log.d("AudioRecorder", "Recording started successfully to ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            recorder = null
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording (e.g., stopped too early)", e)
        } finally {
            recorder = null
        }
    }
}
