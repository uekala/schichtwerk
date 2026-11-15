package de.uekala.schichtwerk.bg

import android.content.Context
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
    fun process(ctx: Context, s: Snapshot) {
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
                    if (done) Notifier.alert(ctx, "Druck fertig", lastTitle)
                    else Notifier.alert(ctx, "Druck gestoppt", "$lastTitle · Status: ${s.statusLabel}")
                }
                s.paused && !wasPaused -> Notifier.alert(ctx, "Druck pausiert", "${s.title} · ${s.progress} %")
            }
        }

        val e = sp.edit()
            .putBoolean(K_SEEN, true)
            .putBoolean(K_ACTIVE, s.active)
            .putBoolean(K_PAUSED, s.paused)
            .putBoolean(K_ERROR, s.error)
        if (s.active) e.putString(K_TITLE, s.title)
        e.apply()
    }
}
