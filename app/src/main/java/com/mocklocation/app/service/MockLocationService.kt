package com.mocklocation.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mocklocation.app.R
import com.mocklocation.app.ui.MainActivity
import com.mocklocation.app.util.MockLocationManager
import kotlinx.coroutines.*

class MockLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.mocklocation.app.ACTION_STOP"
        const val ACTION_MOCK_FAILED = "com.mocklocation.app.ACTION_MOCK_FAILED"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_NAME = "name"

        var isRunning = false
            private set
        var currentMockLat = 0.0
            private set
        var currentMockLng = 0.0
            private set
    }

    private val mockManager by lazy { MockLocationManager(this) }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private var currentLat = 0.0
    private var currentLng = 0.0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMockingAndStopSelf()
            return START_NOT_STICKY
        }

        currentLat = intent?.getDoubleExtra(EXTRA_LATITUDE, 0.0) ?: 0.0
        currentLng = intent?.getDoubleExtra(EXTRA_LONGITUDE, 0.0) ?: 0.0
        val name = intent?.getStringExtra(EXTRA_NAME) ?: ""

        try {
            val notification = buildNotification(name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        val success = mockManager.startMocking(currentLat, currentLng)
        if (!success) {
            stopMockingAndStopSelf()
            val failIntent = Intent(ACTION_MOCK_FAILED)
            failIntent.setPackage(packageName)
            sendBroadcast(failIntent)
            return START_NOT_STICKY
        }

        isRunning = true
        currentMockLat = currentLat
        currentMockLng = currentLng

        startMockingLoop()
        return START_STICKY
    }

    private fun startMockingLoop() {
        mockJob?.cancel()
        mockJob = scope.launch {
            while (isActive) {
                mockManager.pushLocation(currentLat, currentLng)
                delay(500)
            }
        }
    }

    private fun stopMockingAndStopSelf() {
        mockJob?.cancel()
        mockManager.stopMocking()
        isRunning = false
        currentMockLat = 0.0
        currentMockLng = 0.0
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mockJob?.cancel()
        mockManager.stopMocking()
        isRunning = false
        currentMockLat = 0.0
        currentMockLng = 0.0
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(name: String): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_mocking, name))
            .setSmallIcon(R.drawable.ic_map)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_map,
                getString(R.string.notification_stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
