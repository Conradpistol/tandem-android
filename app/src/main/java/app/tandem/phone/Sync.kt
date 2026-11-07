package app.tandem.phone

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Sends today's and yesterday's totals (and the top apps) to Tandem. */
object Sync {
    const val OK = "Sent"

    /** [back] = also fill in every past day Android still has complete data for (up to two weeks, which is what Tandem accepts). */
    fun run(ctx: Context, back: Boolean = false): String {
        val s = Store(ctx)
        if (!s.connected) return "Not connected"
        if (!Usage.granted(ctx)) return "Usage access is off"
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = JSONArray()
        var todayMin = 0
        var range = 0..1
        if (back) {
            val first = Usage.earliest(ctx)
            var oldest = 1
            if (first != null) while (oldest < 13 && Usage.dayStart(oldest + 1) >= first) oldest++
            range = 0..oldest
        }
        for (ago in range) {
            val (ms, apps) = Usage.forDay(ctx, ago)
            val min = (ms / 60_000).toInt().coerceIn(0, 1440)
            if (ago == 0) todayMin = min
            val a = JSONArray()
            apps.take(15).forEach { a.put(JSONObject().put("p", it.pkg).put("l", it.label).put("m", it.ms / 60_000)) }
            days.put(JSONObject().put("date", fmt.format(Usage.dayStart(ago))).put("minutes", min).put("apps", a))
        }
        val device = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).take(60)
        val body = JSONObject().put("k", s.key).put("days", days).put("p_device", device).toString()
        val c = URL(s.url + "/rest/v1/rpc/phone_sync").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 20_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("apikey", s.apiKey)
            if (s.apiKey?.startsWith("sb_") == false) c.setRequestProperty("Authorization", "Bearer " + s.apiKey)
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code in 200..299) {
                val o = runCatching { JSONObject(text) }.getOrNull()
                o?.optString("name")?.takeIf { it.isNotBlank() }?.let { s.name = it }
                s.lastSent = System.currentTimeMillis(); s.lastMinutes = todayMin; s.lastMsg = OK
                if (back) s.backDays = days.length()
                OK
            } else {
                val m = if (text.contains("unknown key")) "Tandem doesn't know this phone any more. Get a new code in Tandem › Settings."
                else "Tandem said $code"
                s.lastMsg = m; m
            }
        } finally { c.disconnect() }
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("tandem-sync", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("tandem-sync")
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val r = Sync.run(applicationContext)
            if (r == Sync.OK || r == "Not connected" || r == "Usage access is off" || r.startsWith("Tandem doesn't")) Result.success() else Result.retry()
        } catch (e: Exception) {
            Store(applicationContext).lastMsg = "Couldn't reach Tandem. Trying again soon."
            Result.retry()
        }
    }
}
