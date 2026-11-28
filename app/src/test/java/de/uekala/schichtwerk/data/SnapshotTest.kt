package de.uekala.schichtwerk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {

    private fun snap(vararg e: Pair<String, Ent>) = Snapshot(mapOf(*e))
    private fun ent(state: String, unit: String? = null) = Ent(state, unit)

    @Test
    fun restzeitInSekundenUnabhaengigVonDerEinheit() {
        assertEquals(90L, snap(Ids.REMAINING to ent("90", "s")).seconds(Ids.REMAINING))
        assertEquals(5_400L, snap(Ids.REMAINING to ent("90", "min")).seconds(Ids.REMAINING))
        assertEquals(9_000L, snap(Ids.REMAINING to ent("2.5", "h")).seconds(Ids.REMAINING))
        assertEquals(42L, snap(Ids.REMAINING to ent("42")).seconds(Ids.REMAINING))
    }

    @Test
    fun restzeitFehltOhneGueltigenWert() {
        assertNull(snap(Ids.REMAINING to ent("unavailable", "s")).seconds(Ids.REMAINING))
        assertNull(snap(Ids.REMAINING to ent("unknown", "s")).seconds(Ids.REMAINING))
        assertNull(snap().seconds(Ids.REMAINING))
    }

    @Test
    fun dauerLangUndKurz() {
        assertEquals("0 min", fmtDuration(0))
        assertEquals("0 min", fmtDuration(-30))
        assertEquals("59 min", fmtDuration(59 * 60 + 59))
        assertEquals("1 h 05 min", fmtDuration(3_600 + 5 * 60))
        assertEquals("4:00 h", fmtDurationShort(4 * 3_600))
        assertEquals("1:05 h", fmtDurationShort(3_600 + 5 * 60 + 30))
        assertEquals("45 min", fmtDurationShort(45 * 60))
    }

    @Test
    fun zusammenfassungMitSchichtUndRestzeit() {
        val s = snap(
            Ids.LAYER to ent("12"),
            Ids.LAYERS to ent("200"),
            Ids.REMAINING to ent("65", "min"),
        )
        assertEquals("Schicht 12/200 · noch 1 h 05 min", s.summary)
        assertEquals("", snap().summary)
    }

    @Test
    fun fortschrittBleibtZwischenNullUndHundert() {
        assertEquals(43, snap(Ids.PROGRESS to ent("42.6")).progress)
        assertEquals(100, snap(Ids.PROGRESS to ent("100.4")).progress)
        assertEquals(0, snap(Ids.PROGRESS to ent("-3")).progress)
        assertEquals(0, snap(Ids.PROGRESS to ent("unavailable")).progress)
    }

    @Test
    fun betriebsdauerAusDemTextVonHomeAssistant() {
        assertEquals(341.8, snap(Ids.LIFETIME_RUNTIME to ent("341h:48m")).lifetimeHours!!, 1e-9)
        assertEquals(7.0, snap(Ids.LIFETIME_RUNTIME to ent("7h")).lifetimeHours!!, 1e-9)
        assertEquals(12.5, snap(Ids.LIFETIME_RUNTIME to ent("12.5")).lifetimeHours!!, 1e-9)
        assertNull(snap(Ids.LIFETIME_RUNTIME to ent("unavailable")).lifetimeHours)
    }

    @Test
    fun titelOhneDateiendung() {
        assertEquals("Halter v2", snap(Ids.FILE to ent("Halter_v2.gcode.3mf")).title)
        assertEquals("Deckel", snap(Ids.FILE to ent("Deckel.GCODE")).title)
        assertEquals("Kein Druckauftrag", snap().title)
    }

    @Test
    fun statusTexte() {
        val online = Ids.ONLINE to ent("on")
        assertEquals("Offline", snap(Ids.ONLINE to ent("off")).statusLabel)
        assertEquals("Fehler", snap(online, Ids.ERROR to ent("on")).statusLabel)
        assertEquals("Pausiert", snap(online, Ids.PAUSED to ent("on")).statusLabel)
        assertEquals("Druckt", snap(online, Ids.STATUS to ent("printing")).statusLabel)
        assertEquals("Bereit", snap(online, Ids.STATUS to ent("ready")).statusLabel)
        assertEquals("Fertig", snap(online, Ids.STATUS to ent("completed")).statusLabel)
    }

    @Test
    fun druckFertigAuchWennDerDruckerGleichAufBereitSpringt() {
        val online = Ids.ONLINE to ent("on")
        assertTrue(snap(online, Ids.STATUS to ent("completed"), Ids.PROGRESS to ent("0")).finishedAfter(40))
        // „Fertig“ nur Sekunden zu sehen: schon „Bereit“ mit 0 %, vorher 99 %.
        assertTrue(snap(online, Ids.STATUS to ent("ready"), Ids.PROGRESS to ent("0")).finishedAfter(99))
        // Home Assistant hat den Erfolg schon festgehalten.
        val erfolg = Ids.LAST_RESULT to ent("Erfolgreich abgeschlossen")
        assertTrue(snap(online, Ids.STATUS to ent("ready"), erfolg).finishedAfter(40))
    }

    @Test
    fun druckGestopptBeiAbbruchOderOhneHinweisAufErfolg() {
        val online = Ids.ONLINE to ent("on")
        val bereit = Ids.STATUS to ent("ready")
        assertFalse(snap(online, bereit, Ids.LAST_RESULT to ent("Druck läuft")).finishedAfter(42))
        assertFalse(snap(online, Ids.STATUS to ent("cancelled")).finishedAfter(99))
        assertFalse(snap(online, Ids.STATUS to ent("unknown")).finishedAfter(99))
        assertFalse(snap(online, bereit, Ids.ERROR to ent("on")).finishedAfter(100))
        // Ergebnis eines früheren Drucks zählt nicht, wenn dieser Druck nie Fortschritt hatte.
        assertFalse(snap(online, bereit, Ids.LAST_RESULT to ent("Erfolgreich abgeschlossen")).finishedAfter(0))
    }

    @Test
    fun wertMitEinheitImDeutschenFormat() {
        assertEquals("4,68 GB", snap(Ids.DISK_FREE to ent("4.68", "GB")).value(Ids.DISK_FREE, 2))
        assertEquals("150 g", snap(Ids.FILAMENT_WARN to ent("150.0", "g")).value(Ids.FILAMENT_WARN))
        assertEquals("–", snap().value(Ids.TVOC))
    }

    @Test
    fun liestDieAntwortDerVorlage() {
        val slot = Ids.slotImage(1)
        val json = """
            {
              "${Ids.BED}": {"s": "24.0", "u": "°C"},
              "$slot": {"s": "2026-11-19T21:57:59+00:00", "u": null,
                        "a": {"material": "PLA", "material_color": "#FFFFFF", "has_filament": true}},
              "${Ids.ETA}": {"s": "unknown", "u": null}
            }
        """.trimIndent()
        val s = Snapshot.parse(json)
        assertEquals(24.0, s.num(Ids.BED)!!, 1e-9)
        assertEquals("°C", s.unit(Ids.BED))
        assertEquals("PLA", s.attrStr(slot, "material"))
        assertEquals(true, s.attr(slot, "has_filament"))
        assertNull(s.raw(Ids.ETA))
        assertEquals("", s.unit(Ids.ETA))
    }
}
