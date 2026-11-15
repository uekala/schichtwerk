package de.uekala.schichtwerk.bg

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.uekala.schichtwerk.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Schaut alle 15 Minuten nach dem Drucker, auch wenn die App zu ist: meldet Zustandswechsel,
 * aktualisiert das Widget und startet bei laufendem Druck die Live-Anzeige.
 */
class PrintCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val client = Prefs.client(ctx) ?: return Result.success()
        return try {
            val s = withContext(Dispatchers.IO) { client.snapshot() }
            Monitor.process(ctx, s)
            PrinterWidget.render(ctx, s)
            if (s.active && Prefs.live(ctx)) {
                // Ohne Ausnahme von der Akku-Optimierung darf der Dienst nicht aus dem
                // Hintergrund starten – dann wird die Anzeige wenigstens hier aufgefrischt.
                if (!PrintMonitorService.start(ctx)) Notifier.showLive(ctx, s)
            } else if (!s.active) {
                Notifier.cancelLive(ctx)
            }
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PrintCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("print-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runOnce(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<PrintCheckWorker>().setConstraints(online).build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("print-check-once", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
