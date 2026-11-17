package de.uekala.schichtwerk.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Einstellungen. Das Token liegt verschlüsselt (Android Keystore, AES-GCM) im privaten App-Speicher. */
object Prefs {
    private const val FILE = "schichtwerk"
    private const val KEY_ALIAS = "schichtwerk_token_key"
    private const val K_URL = "base_url"
    private const val K_TOKEN = "token_enc"
    private const val K_LIVE = "live_notification"
    private const val K_ALERTS = "alerts"
    private const val K_P_IP = "printer_ip"
    private const val K_P_SERIAL = "printer_serial"
    private const val K_P_CODE = "printer_code_enc"

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun baseUrl(ctx: Context): String? = sp(ctx).getString(K_URL, null)

    fun token(ctx: Context): String? {
        val enc = sp(ctx).getString(K_TOKEN, null) ?: return null
        return runCatching { decrypt(enc) }.getOrNull()
    }

    fun isConfigured(ctx: Context): Boolean = baseUrl(ctx) != null && sp(ctx).contains(K_TOKEN)

    fun client(ctx: Context): HaClient? {
        val url = baseUrl(ctx) ?: return null
        val token = token(ctx) ?: return null
        return HaClient(url, token)
    }

    fun save(ctx: Context, url: String, token: String) {
        sp(ctx).edit()
            .putString(K_URL, HaClient.normalize(url))
            .putString(K_TOKEN, encrypt(token.trim()))
            .apply()
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    fun live(ctx: Context): Boolean = sp(ctx).getBoolean(K_LIVE, true)
    fun setLive(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_LIVE, v).apply()

    fun alerts(ctx: Context): Boolean = sp(ctx).getBoolean(K_ALERTS, true)
    fun setAlerts(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(K_ALERTS, v).apply()

    /** Direktzugang zum Drucker im Heimnetz; der Prüfcode liegt verschlüsselt. */
    fun printerDirect(ctx: Context): PrinterAccess? {
        val p = sp(ctx)
        val ip = p.getString(K_P_IP, null) ?: return null
        val serial = p.getString(K_P_SERIAL, null) ?: return null
        val code = p.getString(K_P_CODE, null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: return null
        return PrinterAccess(ip, serial, code)
    }

    fun setPrinterDirect(ctx: Context, a: PrinterAccess?) {
        val e = sp(ctx).edit()
        if (a == null) {
            e.remove(K_P_IP).remove(K_P_SERIAL).remove(K_P_CODE)
        } else {
            e.putString(K_P_IP, a.ip.trim()).putString(K_P_SERIAL, a.serial.trim()).putString(K_P_CODE, encrypt(a.checkCode.trim()))
        }
        e.apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val all = Base64.decode(enc, Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val ct = all.copyOfRange(12, all.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(ct), Charsets.UTF_8)
    }
}
