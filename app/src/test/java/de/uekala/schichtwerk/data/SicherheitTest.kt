package de.uekala.schichtwerk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SicherheitTest {

    @Test
    fun klartextNurImHeimnetz() {
        for (h in listOf("192.168.50.13", "10.0.0.5", "172.16.0.1", "172.31.255.1", "127.0.0.1", "100.71.35.44",
            "169.254.1.1", "localhost", "homeassistant", "drucker.local", "nas.lan", "fe80::1", "[fd7a:115c:a1e0::1]", "::1")) {
            assertTrue(h, Klartext.erlaubt(h))
        }
        for (h in listOf("homeassistant.tail1966da.ts.net", "example.com", "8.8.8.8", "172.32.0.1", "100.128.0.1",
            "192.169.1.1", "2001:db8::1", "192.168.1", "192.168.1.300", "")) {
            assertFalse(h, Klartext.erlaubt(h))
        }
    }

    @Test
    fun versionAusTag() {
        assertEquals("0.2.0", Updater.versionFromTag("mobile-v0.2.0"))
        assertEquals("0.1.42", Updater.versionFromTag("v0.1.42"))
        assertNull(Updater.versionFromTag("nightly"))
    }

    @Test
    fun neuereVersion() {
        assertTrue(Updater.isNewerVersion("0.2.0", "0.1.42"))
        assertTrue(Updater.isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(Updater.isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(Updater.isNewerVersion("0.1.43", "0.2.0"))
        assertFalse(Updater.isNewerVersion("0.3.0", "0.2.0-test"))
    }
}
