package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.AudioEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground Service for continuous native DSP and background YouTube karaoke playback
 * without audio interruptions or system sleep throttling.
 */
class AudioForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "youtube_karaoke_audio_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_MASTER = "com.example.service.ACTION_TOGGLE_MASTER"
        const val ACTION_RESET_PITCH = "com.example.service.ACTION_RESET_PITCH"

        fun startService(context: Context) {
            val intent = Intent(context, AudioForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore if already started or foreground transition error
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire WakeLock and WifiLock to prevent CPU deep sleep and network drops during background playback
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouTubeKaraoke:AudioWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24-hour guard
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YouTubeKaraoke:WifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Throwable) {
            // Ignore if permissions not available in test environments
        }

        // Observe audio engine changes to update notification title/text in real-time
        AudioEngineManager.engineState.onEach { state ->
            updateNotification(state.isMasterEnabled, state.pitchSemiTones)
        }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MASTER -> {
                val current = AudioEngineManager.engineState.value.isMasterEnabled
                AudioEngineManager.setMasterEnabled(!current)
            }
            ACTION_RESET_PITCH -> {
                AudioEngineManager.resetPitch()
            }
            else -> {
                val notification = buildNotification(
                    AudioEngineManager.engineState.value.isMasterEnabled,
                    AudioEngineManager.engineState.value.pitchSemiTones
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube karaoke Background Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Memutar audio YouTube karaoke tanpa henti di latar belakang"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isMasterOn: Boolean, pitch: Float): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, AudioForegroundService::class.java).apply {
            action = ACTION_TOGGLE_MASTER
        }
        val togglePending = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pitchResetIntent = Intent(this, AudioForegroundService::class.java).apply {
            action = ACTION_RESET_PITCH
        }
        val pitchResetPending = PendingIntent.getService(
            this, 2, pitchResetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AudioForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pitchText = if (pitch == 0f) "0 ST" else String.format("%+.1f ST", pitch)
        val statusText = if (isMasterOn) "Memutar di latar belakang • DSP Aktif ($pitchText)" else "Memutar di latar belakang (Bypass)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouTube karaoke")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                if (isMasterOn) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isMasterOn) "Bypass" else "Enable",
                togglePending
            )
            .addAction(android.R.drawable.ic_menu_rotate, "Reset Pitch", pitchResetPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun updateNotification(isMasterOn: Boolean, pitch: Float) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isMasterOn, pitch))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Throwable) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
