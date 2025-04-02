package com.example.audiocontrolapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku

class AudioControlService : Service() {

    private lateinit var audioManager: AudioManager
    private val TAG = "AudioControlService"
    private val NOTIFICATION_CHANNEL_ID = "multi_audio_channel"
    private val NOTIFICATION_ID = 101

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startForeground(NOTIFICATION_ID, createNotification())

        Thread {
            try {
                if (Shizuku.pingBinder()) {
                    setupMultiAudio()
                } else {
                    Log.e(TAG, "Shizuku not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up multi-audio", e)
            }
        }.start()
    }

    private fun setupMultiAudio() {
        if (!setSystemAudioSettings()) {
            configureAudioFocus()
        }
    }

    private fun setSystemAudioSettings(): Boolean {
        return try {
            // First try with Shizuku context
            val success1 = runCommandWithShizuku("settings put system sound_effects_enabled 0")
            val success2 = runCommandWithShizuku("settings put global audio_focus_control duck")

            if (!success1 || !success2) {
                // Fallback to standard process execution
                Log.w(TAG, "Trying fallback method...")
                executeCommand("settings put system sound_effects_enabled 0")
                executeCommand("settings put global audio_focus_control duck")
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error modifying system settings", e)
            false
        }
    }

    private fun runCommandWithShizuku(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku command failed: $command", e)
            false
        }
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            false
        }
    }

    private fun executeShizukuCommand(command: String): Boolean {
        return try {
            // Using Runtime.exec with Shizuku context
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            false
        }
    }

    private fun configureAudioFocus() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> Log.d(TAG, "Audio focus lost")
                    AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "Audio focus gained")
                }
            }

            val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusListener)
                    .setWillPauseWhenDucked(false)
                    .build()
            } else {
                null
            }

            val result = if (focusRequest != null) {
                audioManager.requestAudioFocus(focusRequest)
            } else {
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> Log.d(TAG, "Audio focus granted")
                else -> Log.w(TAG, "Audio focus request failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring audio focus", e)
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Multi-Audio Active")
            .setContentText("Playing multiple audio streams")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Multi-Audio Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls simultaneous audio playback"
            }

            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            executeShizukuCommand("settings put global audio_focus_control default")
            executeShizukuCommand("settings put system sound_effects_enabled 1")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio settings", e)
        }
    }
}