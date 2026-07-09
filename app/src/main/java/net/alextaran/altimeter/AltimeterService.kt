package net.alextaran.altimeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow

class AltimeterService : Service() {

    companion object {
        private const val CHANNEL_ID = "AltimeterChannel"
        private const val NOTIFICATION_ID = 1
        val isRunning = MutableStateFlow(false)
    }

    private lateinit var locationManager: LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d("AltimeterService", "Location received: lat=${location.latitude}, lon=${location.longitude}, alt=${location.altitude}, hasAlt=${location.hasAltitude()}")
            if (location.hasAltitude()) {
                val altitude = location.altitude.toInt().toString()
                updateNotification(altitude, location.time)
            } else {
                updateNotification(getString(R.string.status_no_alt), location.time)
            }
        }
        
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AltimeterService", "onStartCommand called")
        isRunning.value = true
        val notification = buildNotification(getString(R.string.status_wait), System.currentTimeMillis())
        
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        requestLocationUpdates()

        return START_STICKY
    }

    private fun requestLocationUpdates() {
        try {
            Log.d("AltimeterService", "Requesting location updates")
            
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation != null && lastLocation.hasAltitude()) {
                updateNotification(lastLocation.altitude.toInt().toString(), lastLocation.time)
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                30000L,
                1f,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e("AltimeterService", "Permission denied for location", e)
        } catch (e: Exception) {
            Log.e("AltimeterService", "Error requesting location", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
        locationManager.removeUpdates(locationListener)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, time: Long): Notification {
        val icon = createTextIcon(text)
        
        val contentText = if (text.toIntOrNull() != null) {
            getString(R.string.notification_current_altitude, text)
        } else {
            getString(R.string.notification_status, text)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setWhen(time)
            .setShowWhen(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String, time: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text, time))
    }

    private fun createTextIcon(text: String): IconCompat {
        val size = 96 
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(text)
        val maxWidth = size.toFloat()
        if (textWidth > maxWidth) {
            textPaint.textScaleX = maxWidth / textWidth
        }

        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        
        canvas.drawText(text, xPos, yPos, textPaint)

        return IconCompat.createWithBitmap(bitmap)
    }
}
