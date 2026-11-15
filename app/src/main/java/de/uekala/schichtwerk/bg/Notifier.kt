package de.uekala.schichtwerk.bg

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.uekala.schichtwerk.MainActivity
import de.uekala.schichtwerk.R
import de.uekala.schichtwerk.data.Prefs
import de.uekala.schichtwerk.data.Snapshot

object Notifier {
    const val CH_LIVE = "live"
    const val CH_ALERT = "alerts"
    const val ID_LIVE = 1
    private const val ID_ALERT = 2
    private const val ACCENT = 0xFFFF7A1A.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Laufender Druck", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Fortschritt, solange ein Druck läuft"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fehler, Pause und Druckende"
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /** Dauerbenachrichtigung mit Fortschritt. [snap] = null zeigt nur "Verbinde …". */
    fun live(ctx: Context, snap: Snapshot?): Notification {
        val b = NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_layers)
            .setColor(ACCENT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (snap == null) {
            b.setContentTitle("Schichtwerk").setContentText("Verbinde mit Home Assistant …")
        } else {
            b.setContentTitle("${snap.title} · ${snap.progress} %")
                .setContentText(if (snap.paused) "Pausiert · ${snap.summary}" else snap.summary)
                .setProgress(100, snap.progress, false)
        }
        return b.build()
    }

    fun showLive(ctx: Context, snap: Snapshot?) {
        if (allowed(ctx)) NotificationManagerCompat.from(ctx).notify(ID_LIVE, live(ctx, snap))
    }

    fun cancelLive(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_LIVE)

    fun alert(ctx: Context, title: String, text: String) {
        if (!Prefs.alerts(ctx) || !allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_layers)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
    }
}
