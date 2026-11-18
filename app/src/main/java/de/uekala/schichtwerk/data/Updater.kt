package de.uekala.schichtwerk.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.uekala.schichtwerk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Release(val tag: String, val versionCode: Int, val assetId: Long, val assetName: String, val size: Long)

/** Neue Versionen aus den GitHub-Releases des (privaten) Repos holen und installieren. */
object Updater {
    private const val REPO = "uekala/schichtwerk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun req(url: String, token: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("X-GitHub-Api-Version", "2022-11-28")

    /** Letztes Release; null, wenn es keine passende APK gibt. */
    fun latest(token: String): Release? {
        val r = http.newCall(
            req("https://api.github.com/repos/$REPO/releases/latest", token)
                .header("Accept", "application/vnd.github+json").build()
        ).execute()
        val body = r.use {
            when (it.code) {
                401 -> throw IOException("GitHub-Token wurde abgelehnt")
                403, 404 -> throw IOException("Kein Zugriff auf das Repo – Token braucht Leserecht auf $REPO")
            }
            if (!it.isSuccessful) throw IOException("GitHub antwortet mit Fehler ${it.code}")
            it.body?.string().orEmpty()
        }
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val code = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk")) return Release(tag, code, a.getLong("id"), name, a.optLong("size"))
        }
        return null
    }

    fun isNewer(r: Release): Boolean = r.versionCode > BuildConfig.VERSION_CODE

    /** Lädt die APK in den Cache. GitHub leitet auf einen signierten Download-Link um. */
    fun download(ctx: Context, token: String, r: Release): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, r.assetName)
        http.newCall(
            req("https://api.github.com/repos/$REPO/releases/assets/${r.assetId}", token)
                .header("Accept", "application/octet-stream").build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download fehlgeschlagen (${resp.code})")
            val body = resp.body ?: throw IOException("Download leer")
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (file.length() < 100_000) throw IOException("Download unvollständig")
        return file
    }

    /** Darf die App Pakete installieren? Ab Android 8 muss das einmal erlaubt werden. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
