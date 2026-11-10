package com.example.woodfish

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Desktop widgets cannot host SceneView / a live 3D GLB renderer.
 *  They show a transparent image pre-rendered from the same 3D model.
 *  Each tap updates a local counter. Sound / haptics should be added separately.
 */
class WoodfishWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_TAP = "com.example.woodfish.ACTION_TAP"
        private const val EXTRA_WIDGET = "widget_id"
        private const val PREFS = "woodfish_widget_counter"

        private fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val count = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("count_$widgetId", 0)
            val rv = RemoteViews(context.packageName, R.layout.widget_woodfish)
            rv.setImageViewResource(R.id.woodfishImage, R.drawable.woodfish_idle)
            rv.setTextViewText(R.id.woodfishCount, count.toString())
            val tapIntent = Intent(context, WoodfishWidgetProvider::class.java).apply {
                action = ACTION_TAP
                putExtra(EXTRA_WIDGET, widgetId)
            }
            val pending = PendingIntent.getBroadcast(
                context, widgetId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widgetRoot, pending)
            manager.updateAppWidget(widgetId, rv)
        }
    }

    override fun onUpdate(
        context: Context, manager: AppWidgetManager, widgetIds: IntArray
    ) {
        widgetIds.forEach { id -> update(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return
        val id = intent.getIntExtra(EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "count_$id"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        update(context, AppWidgetManager.getInstance(context), id)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { id -> editor.remove("count_$id") }
        editor.apply()
    }
}
