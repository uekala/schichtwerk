package de.uekala.schichtwerk.data

import okhttp3.Interceptor
import java.io.IOException

/**
 * Unverschlüsseltes HTTP nur im Heimnetz. Home Assistant (z. B. http://192.168.50.13:8123) und die
 * Druckerschnittstelle (Port 8898) sprechen im LAN nur HTTP; das Manifest erlaubt Klartext deshalb
 * grundsätzlich. Diese Sperre lässt ihn nur zu privaten Adressen zu – alles andere muss HTTPS sein.
 */
object Klartext {

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps && !erlaubt(url.host)) {
            throw IOException("Unverschlüsselte Verbindung zu ${url.host} blockiert – außerhalb des Heimnetzes nur HTTPS")
        }
        chain.proceed(chain.request())
    }

    /** True für private IPv4/IPv6-Adressen, Tailscale (100.64.0.0/10), localhost und Namen im Heimnetz. */
    fun erlaubt(host: String): Boolean {
        val h = host.trim().trimStart('[').trimEnd(']').lowercase()
        if (h.isEmpty()) return false
        ipv4(h)?.let { (a, b) ->
            return a == 10 || a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        if (':' in h) {
            return h == "::1" || h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") ||
                h.startsWith("feb") || h.startsWith("fc") || h.startsWith("fd")
        }
        if (h == "localhost") return true
        // Name ohne Punkt (z. B. "homeassistant") oder mit Heimnetz-Endung.
        return '.' !in h || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa") ||
            h.endsWith(".fritz.box") || h.endsWith(".internal")
    }

    /** Die ersten beiden Oktette einer IPv4-Adresse, sonst null. Kein DNS. */
    private fun ipv4(h: String): Pair<Int, Int>? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val n = parts.map { p -> p.toIntOrNull()?.takeIf { p.isNotEmpty() && p.length <= 3 && it in 0..255 } ?: return null }
        return n[0] to n[1]
    }
}
