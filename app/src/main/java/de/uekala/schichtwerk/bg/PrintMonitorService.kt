package de.uekala.schichtwerk.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.uekala.schichtwerk.data.Prefs
import de.uekala.schichtwerk.data.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Läuft nur, solange ein Druck aktiv ist: fragt Home Assistant alle 15 Sekunden ab,
 * hält die Dauerbenachrichtigung und das Widget aktuell und beendet sich danach selbst.
 */
class PrintMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var last: Snapshot? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifier.ID_LIVE, Notifier.live(this, last), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        if (job == null) job = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        var idle = 0
        while (scope.isActive) {
            val client = Prefs.client(this)
            if (client == null || !Prefs.live(this)) break
            try {
                val s = client.snapshot()
                Monitor.process(this, s, client)
                PrinterWidget.render(this, s)
                if (s.active) {
                    idle = 0
                    last = s
                    Notifier.showLive(this, s)
                } else if (s.online && ++idle >= 2) {
                    break
                }
            } catch (e: Exception) {
                // Netz kurz weg: Anzeige stehen lassen und weiter versuchen.
            }
            delay(15_000)
        }
        running = false
        job = null
        last = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Startet den Dienst; gibt false zurück, wenn Android den Start aus dem Hintergrund verweigert. */
        @Volatile
        var running = false
            private set

        fun start(ctx: Context): Boolean = if (running) true else try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PrintMonitorService::class.java))
            true
        } catch (e: Exception) {
            false
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PrintMonitorService::class.java))
        }
    }
}
