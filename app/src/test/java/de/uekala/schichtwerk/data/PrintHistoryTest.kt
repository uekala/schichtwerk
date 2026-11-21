package de.uekala.schichtwerk.data

import de.uekala.schichtwerk.data.PrintRecord.Result.CANCELLED
import de.uekala.schichtwerk.data.PrintRecord.Result.ERROR
import de.uekala.schichtwerk.data.PrintRecord.Result.OK
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.YearMonth

class PrintHistoryTest {

    /** Termin wie ihn die Automation in den Kalender „3D-Drucke“ schreibt. */
    private fun event(summary: String, start: String?, end: String?, description: String): JSONObject {
        val o = JSONObject().put("summary", summary).put("description", description)
        if (start != null) o.put("start", JSONObject().put("dateTime", start))
        if (end != null) o.put("end", JSONObject().put("dateTime", end))
        return o
    }

    private fun description(result: String, grams: String, filamentEur: String, kwh: String, powerEur: String, total: String) =
        "Ergebnis: $result\nDauer: 1:06 h\nFilament: $grams g ($filamentEur €)\nStrom: $kwh kWh ($powerEur €)\nKosten gesamt: $total €"

    @Test
    fun liestVerbrauchUndKostenAusDerBeschreibung() {
        val arr = JSONArray().put(
            event(
                "✅ Halter_v12_Creator5Pro_PLA.gcode.3mf",
                "2026-11-20T11:32:03+01:00", "2026-11-20T12:38:35+01:00",
                description("Erfolgreich abgeschlossen", "19.8", "0.21", "0.173", "0.05", "0.26"),
            )
        )
        val r = PrintHistory.parse(arr).single()
        assertEquals("Halter v12 Creator5Pro PLA", r.name)
        assertEquals(OK, r.result)
        assertEquals(19.8, r.grams!!, 1e-9)
        assertEquals(0.21, r.costFilament!!, 1e-9)
        assertEquals(0.173, r.kwh!!, 1e-9)
        assertEquals(0.05, r.costPower!!, 1e-9)
        assertEquals(0.26, r.costTotal!!, 1e-9)
        assertEquals(3_992L, r.durationSeconds)
    }

    @Test
    fun kommaAlsDezimaltrennzeichen() {
        val arr = JSONArray().put(
            event(
                "✅ Teil.gcode.3mf", "2026-11-20T10:00:00Z", "2026-11-20T11:00:00Z",
                description("Erfolgreich abgeschlossen", "51,4", "0,51", "0,334", "0,09", "0,60"),
            )
        )
        val r = PrintHistory.parse(arr).single()
        assertEquals(51.4, r.grams!!, 1e-9)
        assertEquals(0.51, r.costFilament!!, 1e-9)
        assertEquals(0.334, r.kwh!!, 1e-9)
        assertEquals(0.09, r.costPower!!, 1e-9)
        assertEquals(0.60, r.costTotal!!, 1e-9)
    }

    @Test
    fun ergebnisAusSymbolOderTextNeuesteZuerst() {
        val arr = JSONArray()
            .put(event("Teil_B.3mf", "2026-11-17T10:00:00+01:00", "2026-11-17T11:00:00+01:00", "Ergebnis: Erfolgreich abgeschlossen"))
            .put(
                event(
                    "⛔ Teil_A.gcode.3mf", "2026-11-18T21:53:26+01:00", "2026-11-19T00:24:06+01:00",
                    description("Abgebrochen", "51.4", "0.51", "0.334", "0.09", "0.60"),
                )
            )
            .put(event("❌ Teil_C.gcode.3mf", "2026-11-16T10:00:00+01:00", "2026-11-16T10:30:00+01:00", "Ergebnis: Fehler"))
        val list = PrintHistory.parse(arr)
        assertEquals(listOf("Teil A", "Teil B", "Teil C"), list.map { it.name })
        assertEquals(listOf(CANCELLED, OK, ERROR), list.map { it.result })
        assertNull(list[1].grams)
        assertNull(list[1].costTotal)
    }

    @Test
    fun ohneStartUebersprungenOhneEndeDauerNull() {
        val arr = JSONArray()
            .put(event("✅ Ohne_Start.3mf", null, "2026-11-20T11:00:00+01:00", ""))
            .put(event("✅ Ohne_Ende.3mf", "2026-11-20T10:00:00+01:00", null, ""))
            .put(event("✅ ", "2026-11-19T10:00:00+01:00", "2026-11-19T10:30:00+01:00", ""))
        val list = PrintHistory.parse(arr)
        assertEquals(listOf("Ohne Ende", "Druck"), list.map { it.name })
        assertEquals(0L, list[0].durationSeconds)
        assertEquals(1_800L, list[1].durationSeconds)
    }

    private fun record(start: String, hours: Long, result: PrintRecord.Result, grams: Double?, cost: Double?): PrintRecord {
        val s = OffsetDateTime.parse(start)
        return PrintRecord(s, s.plusHours(hours), "Druck", result, grams, cost, null, null, null)
    }

    @Test
    fun monatssummen() {
        val months = PrintHistory.months(
            listOf(
                record("2026-10-15T12:00:00Z", 2, OK, 40.0, 0.5),
                record("2026-11-10T12:00:00Z", 3, OK, 100.0, 1.25),
                record("2026-11-12T12:00:00Z", 1, CANCELLED, 20.0, null),
                record("2026-11-14T12:00:00Z", 2, ERROR, null, 0.25),
            )
        )
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })

        val nov = months[0]
        assertEquals(3, nov.prints)
        assertEquals(1, nov.successful)
        assertEquals(120.0, nov.grams, 1e-9)
        assertEquals(1.5, nov.cost, 1e-9)
        assertEquals(6.0, nov.hours, 1e-9)

        val oct = months[1]
        assertEquals(1, oct.prints)
        assertEquals(1, oct.successful)
        assertEquals(40.0, oct.grams, 1e-9)
        assertEquals(0.5, oct.cost, 1e-9)
        assertEquals(2.0, oct.hours, 1e-9)
    }
}
