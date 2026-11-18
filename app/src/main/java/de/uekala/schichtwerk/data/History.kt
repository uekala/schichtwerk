package de.uekala.schichtwerk.data

import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Ein beendeter Druck aus dem Kalender „3D-Drucke“. */
data class PrintRecord(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val name: String,
    val result: Result,
    val grams: Double?,
    val costTotal: Double?,
    val costFilament: Double?,
    val costPower: Double?,
    val kwh: Double?,
) {
    enum class Result { OK, CANCELLED, ERROR }

    val durationSeconds: Long get() = (end.toEpochSecond() - start.toEpochSecond()).coerceAtLeast(0)
    val month: YearMonth get() = YearMonth.from(start.atZoneSameInstant(ZoneId.systemDefault()))
}

data class MonthSummary(
    val month: YearMonth,
    val prints: Int,
    val successful: Int,
    val grams: Double,
    val cost: Double,
    val hours: Double,
)

object PrintHistory {
    const val CALENDAR = "calendar.3d_drucke"

    private fun number(text: String, label: String): Double? =
        Regex("$label:\\s*([0-9]+(?:[.,][0-9]+)?)").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    private fun euroIn(text: String, label: String): Double? =
        Regex("$label:[^(\\n]*\\(([0-9]+(?:[.,][0-9]+)?)\\s*€\\)").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    private fun time(o: org.json.JSONObject?): OffsetDateTime? {
        if (o == null) return null
        val s = o.optString("dateTime").ifBlank { return null }
        return runCatching { OffsetDateTime.parse(s) }.getOrNull()
    }

    fun parse(arr: JSONArray): List<PrintRecord> {
        val out = ArrayList<PrintRecord>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val start = time(e.optJSONObject("start")) ?: continue
            val end = time(e.optJSONObject("end")) ?: start
            val summary = e.optString("summary")
            val desc = e.optString("description")
            val result = when {
                summary.startsWith("✅") || desc.contains("Erfolgreich") -> PrintRecord.Result.OK
                summary.startsWith("⛔") || desc.contains("Abgebrochen") -> PrintRecord.Result.CANCELLED
                else -> PrintRecord.Result.ERROR
            }
            val name = summary.replace(Regex("^[^A-Za-z0-9ÄÖÜäöü]+"), "")
                .replace(Regex("(\\.gcode)?\\.(3mf|gcode|gx|g)$", RegexOption.IGNORE_CASE), "")
                .replace('_', ' ')
                .trim()
            out += PrintRecord(
                start = start,
                end = end,
                name = name.ifBlank { "Druck" },
                result = result,
                grams = number(desc, "Filament"),
                costTotal = number(desc, "Kosten gesamt"),
                costFilament = euroIn(desc, "Filament"),
                costPower = euroIn(desc, "Strom"),
                kwh = number(desc, "Strom"),
            )
        }
        return out.sortedByDescending { it.start }
    }

    fun months(records: List<PrintRecord>): List<MonthSummary> =
        records.groupBy { it.month }.map { (m, list) ->
            MonthSummary(
                month = m,
                prints = list.size,
                successful = list.count { it.result == PrintRecord.Result.OK },
                grams = list.sumOf { it.grams ?: 0.0 },
                cost = list.sumOf { it.costTotal ?: 0.0 },
                hours = list.sumOf { it.durationSeconds } / 3600.0,
            )
        }.sortedByDescending { it.month }
}
