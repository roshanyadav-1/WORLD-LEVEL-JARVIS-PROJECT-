package com.aris.voice

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews

class ArisWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == "com.aris.voice.WIDGET_CLICKED") {
            android.util.Log.d("ArisWidgetProvider", "Widget clicked broadcast received. Starting service directly.")
            val serviceIntent = Intent(context, ConversationalAgentService::class.java).apply {
                action = ConversationalAgentService.ACTION_FORCE_LISTEN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    android.util.Log.e("ArisWidgetProvider", "Failed to start service directly, falling back to activity check", e)
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        action = "com.aris.voice.ACTION_WIDGET_TAP"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(activityIntent)
                }
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val isActive = ConversationalAgentService.isRunning

        for (appWidgetId in appWidgetIds) {
            // Widget tap broadcast explicit intent
            val intent = Intent(context, ArisWidgetProvider::class.java).apply {
                action = "com.aris.voice.WIDGET_CLICKED"
            }

            // Create a PendingIntent to broadcast safely on touch
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Bind layout and attach the PendingIntent action
            val views = RemoteViews(context.packageName, R.layout.aris_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Dynamic customization based on "Frozen (Dormant)" vs "Zinda (Live)"
            try {
                if (isActive) {
                    views.setViewVisibility(R.id.widget_icon, android.view.View.INVISIBLE)
                    views.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
                } else {
                    views.setViewVisibility(R.id.widget_icon, android.view.View.VISIBLE)
                    // Removing flat color tint so it looks like the actual creature
                    views.setInt(R.id.widget_root, "setBackgroundColor", Color.TRANSPARENT)
                }
            } catch (e: Exception) {
                android.util.Log.e("ArisWidgetProvider", "Failed to apply dynamic styles to RemoteViews", e)
            }

            // Update app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
