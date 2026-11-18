package de.uekala.schichtwerk.bg

import android.content.Context
import de.uekala.schichtwerk.data.HaClient
import de.uekala.schichtwerk.data.Ids
import de.uekala.schichtwerk.data.Prefs
import de.uekala.schichtwerk.data.Snapshot

/** Vergleicht jede neue Momentaufnahme mit der letzten und meldet Zustandswechsel. */
object Monitor {
    private const val K_SEEN = "m_seen"
    private const val K_ACTIVE = "m_active"
    private const val K_PAUSED = "m_paused"
    private const val K_ERROR = "m_error"
    private const val K_TITLE = "m_title"

    @Synchronized
    fun process(ctx: Context, s: Snapshot, client: HaClient? = null) {
        val sp = Prefs.sp(ctx)
        val seen = sp.getBoolean(K_SEEN, false)
        val wasActive = sp.getBoolean(K_ACTIVE, false)
        val wasPaused = sp.getBoolean(K_PAUSED, false)
        val wasError = sp.getBoolean(K_ERROR, false)
        val lastTitle = sp.getString(K_TITLE, null) ?: "Druck"

        // Ist der Drucker gerade nicht erreichbar, bleibt der letzte bekannte Stand stehen.
        if (!s.online && wasActive) return

        if (seen) {
            when {
                s.error && !wasError -> {
                    val code = s.raw(Ids.ERROR_CODE)?.let { " (Code $it)" }.orEmpty()
                    Notifier.alert(ctx, "Druckerfehler$code", "${s.title} · ${s.progress} %")
                }
                wasActive && !s.active -> {
                    val done = s.progress >= 100 || s.status.lowercase() in setOf("completed", "complete")
                    if (done) {
                        // Kamerabild des fertigen Drucks mitschicken, falls die Kamera antwortet.
                        val photo = client?.let { c -> runCatching { c.cameraImage(Ids.CAMERA) }.getOrNull() }
                        Notifier.alert(ctx, "Druck fertig", lastTitle, photo)
                    }
                    else Notifier.alert(ctx, "Druck gestoppt", "$lastTitle · Status: ${s.statusLabel}")
                }
                s.paused && !wasPaused -> Notifier.alert(ctx, "Druck pausiert", "${s.title} · ${s.progress} %")
            }
        }

        checkSpools(ctx, s)

        val e = sp.edit()
            .putBoolean(K_SEEN, true)
            .putBoolean(K_ACTIVE, s.active)
            .putBoolean(K_PAUSED, s.paused)
            .putBoolean(K_ERROR, s.error)
        if (s.active) e.putString(K_TITLE, s.title)
        e.apply()
    }

    /** Meldet einmal, wenn eine Spule unter die Warnschwelle fällt; erneut erst nach dem Auffüllen. */
    private fun checkSpools(ctx: Context, s: Snapshot) {
        val warn = s.num(Ids.FILAMENT_WARN) ?: return
        if (warn <= 0) return
        val sp = Prefs.sp(ctx)
        val e = sp.edit()
        for (i in 1..4) {
            val rest = s.num(Ids.slotRest(i)) ?: continue
            val loaded = s.attr(Ids.slotImage(i), "has_filament") != false
            val key = "m_spool_low_$i"
            val low = loaded && rest > 0 && rest <= warn
            if (low && !sp.getBoolean(key, false)) {
                val mat = s.attrStr(Ids.slotImage(i), "material")?.let { " ($it)" }.orEmpty()
                Notifier.alert(ctx, "Spule in Slot $i fast leer", "Noch ${rest.toInt()} g$mat", id = Notifier.idSlot(i))
            }
            if (!low && rest > warn) e.putBoolean(key, false) else if (low) e.putBoolean(key, true)
        }
        e.apply()
    }
}
