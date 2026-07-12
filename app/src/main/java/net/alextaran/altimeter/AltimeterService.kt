package net.alextaran.altimeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.alextaran.altimeter.storage.AltitudePoint
import net.alextaran.altimeter.storage.AppDatabase

class AltimeterService : Service() {

    companion object {
        private const val CHANNEL_ID = "AltimeterChannel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP_SERVICE = "net.alextaran.altimeter.ACTION_STOP_SERVICE"

        val isRunning = MutableStateFlow(false)
    }

    private lateinit var locationManager: LocationManager
    private lateinit var database: AppDatabase

    private val locationListener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(
                            "AltimeterService",
                            "Location received: lat=${location.latitude}, lon=${location.longitude}, alt=${location.altitude}, hasAlt=${location.hasAltitude()}"
                    )
                    if (location.hasAltitude()) {
                        val altitude = location.altitude.toInt().toString()
                        updateNotification(altitude, location.time)

                        CoroutineScope(Dispatchers.IO).launch {
                            val point =
                                    AltitudePoint(
                                            timestamp = location.time,
                                            altitude = location.altitude
                                    )
                            database.altitudeDao().insertPoint(point)
                        }
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
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.d("AltimeterService", "Stop action received via notification button")
            stopSelf()
            return START_NOT_STICKY
        }
        Log.d("AltimeterService", "onStartCommand called")
        isRunning.value = true
        val notification =
                buildNotification(getString(R.string.status_wait), System.currentTimeMillis())

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
        val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, time: Long): Notification {
        val activityIntent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        val contentPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        activityIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
        val stopIntent =
                Intent(this, AltimeterService::class.java).apply { action = ACTION_STOP_SERVICE }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        1,
                        stopIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
        val icon = createAltimeterIcon() // createTextIcon(text)

        val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

        val contentText: CharSequence =
                if (text.toIntOrNull() != null) {
                    val rawText =
                            getString(R.string.notification_current_altitude, text, timeFormatted)
                    Html.fromHtml(rawText, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    getString(R.string.notification_status, text)
                }

        val chipText = if (text.toIntOrNull() != null) "${text} m" else text

        return Notification.Builder(this, CHANNEL_ID)
                .setOnlyAlertOnce(true)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(contentText)
                .setContentIntent(contentPendingIntent)
                .addAction(
                        Notification.Action.Builder(
                                        Icon.createWithResource(
                                                this,
                                                android.R.drawable.ic_menu_close_clear_cancel
                                        ),
                                        getString(R.string.action_stop_altimeter),
                                        stopPendingIntent
                                )
                                .build()
                )
                .setSmallIcon(icon)
                .setOngoing(true)
                .setWhen(time)
                .setShowWhen(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setShortCriticalText(chipText)
                .setRequestPromotedOngoing(true)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .build()
    }

    private fun updateNotification(text: String, time: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text, time))
    }

    private fun createTextIcon(text: String): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

        return Icon.createWithBitmap(bitmap)
    }

    private fun createAltimeterIcon(): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }

        val bigMountain =
                android.graphics.Path().apply {
                    moveTo(40f, 20f)
                    lineTo(80f, 80f)
                    lineTo(0f, 80f)
                    close()
                }
        canvas.drawPath(bigMountain, paint)

        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        val cutout =
                android.graphics.Path().apply {
                    moveTo(68f, 40f)
                    lineTo(96f, 82f)
                    lineTo(40f, 82f)
                    close()
                }
        canvas.drawPath(cutout, paint)

        paint.xfermode = null
        val smallMountain =
                android.graphics.Path().apply {
                    moveTo(68f, 40f)
                    lineTo(96f, 80f)
                    lineTo(40f, 80f)
                    close()
                }
        canvas.drawPath(smallMountain, paint)

        return Icon.createWithBitmap(bitmap)
    }
}
