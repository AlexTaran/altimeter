package net.alextaran.altimeter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import net.alextaran.altimeter.AltimeterService
import net.alextaran.altimeter.R

class AltimeterWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "net.alextaran.altimeter.WIDGET_TOGGLE"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AltimeterWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val isRunning = AltimeterService.isRunning.value

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_simple)

                views.setTextViewText(R.id.tv_widget_status, if (isRunning) "STOP" else "START")

                views.setTextColor(
                        R.id.tv_widget_status,
                        if (isRunning) android.graphics.Color.parseColor("#E57373")
                        else android.graphics.Color.parseColor("#81C784")
                )

                val intent =
                        Intent(context, AltimeterWidgetProvider::class.java).apply {
                            action = ACTION_TOGGLE
                        }

                val pendingIntent =
                        PendingIntent.getBroadcast(
                                context,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE) {
            val serviceIntent = Intent(context, AltimeterService::class.java)

            if (AltimeterService.isRunning.value) {
                context.stopService(serviceIntent)
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            updateAllWidgets(context)
        }
    }
}
