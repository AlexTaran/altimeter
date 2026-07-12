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

        // Этот метод будет обновлять текст на кнопках всех добавленных виджетов
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AltimeterWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            // Читаем текущее состояние из сервиса
            val isRunning = AltimeterService.isRunning.value

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_simple)

                // Меняем текст кнопки в зависимости от статуса
                views.setTextViewText(
                        R.id.btn_widget_toggle,
                        if (isRunning) context.getString(R.string.action_stop_altimeter)
                        else context.getString(R.string.action_start_altimeter)
                )

                // Настраиваем клик по кнопке
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
                views.setOnClickPendingIntent(R.id.btn_widget_toggle, pendingIntent)

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

        // Обрабатываем клик по виджету
        if (intent.action == ACTION_TOGGLE) {
            val serviceIntent = Intent(context, AltimeterService::class.java)

            if (AltimeterService.isRunning.value) {
                context.stopService(serviceIntent)
            } else {
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            // Сразу обновляем UI виджета для отзывчивости
            updateAllWidgets(context)
        }
    }
}
