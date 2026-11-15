package de.uekala.schichtwerk.bg

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import de.uekala.schichtwerk.MainActivity
import de.uekala.schichtwerk.R
import de.uekala.schichtwerk.data.Prefs
import de.uekala.schichtwerk.data.Snapshot

/** Homescreen-Widget: Status, Datei, Fortschritt und Restzeit. */
class PrinterWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        draw(ctx, mgr, ids)
        PrintCheckWorker.runOnce(ctx)
    }

    companion object {
        private const val K_STATUS = "w_status"
        private const val K_TITLE = "w_title"
        private const val K_SUB = "w_sub"
        private const val K_PROGRESS = "w_progress"
        private const val K_ACTIVE = "w_active"

        /** Speichert den Stand und zeichnet alle Widgets neu. */
        fun render(ctx: Context, s: Snapshot) {
            Prefs.sp(ctx).edit()
                .putString(K_STATUS, "Creator 5 Pro · ${s.statusLabel}")
                .putString(K_TITLE, if (s.active) s.title else s.statusLabel)
                .putString(K_SUB, if (s.active) s.summary else "")
                .putInt(K_PROGRESS, s.progress)
                .putBoolean(K_ACTIVE, s.active)
                .apply()
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrinterWidget::class.java))
            if (ids.isNotEmpty()) draw(ctx, mgr, ids)
        }

        private fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val sp = Prefs.sp(ctx)
            val active = sp.getBoolean(K_ACTIVE, false)
            val progress = sp.getInt(K_PROGRESS, 0)
            val views = RemoteViews(ctx.packageName, R.layout.printer_widget).apply {
                setTextViewText(R.id.widget_status, sp.getString(K_STATUS, "Schichtwerk"))
                setTextViewText(
                    R.id.widget_title,
                    sp.getString(K_TITLE, if (Prefs.isConfigured(ctx)) "Noch keine Daten" else "App öffnen und verbinden"),
                )
                setTextViewText(R.id.widget_sub, sp.getString(K_SUB, ""))
                setTextViewText(R.id.widget_percent, if (active) "$progress %" else "")
                setProgressBar(R.id.widget_progress, 100, if (active) progress else 0, false)
                setViewVisibility(R.id.widget_progress, if (active) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_sub, if (active) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        ctx, 0, Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            mgr.updateAppWidget(ids, views)
        }
    }
}
