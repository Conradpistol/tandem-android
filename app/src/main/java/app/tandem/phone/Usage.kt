package app.tandem.phone

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUse(val pkg: String, val label: String, val ms: Long)

/**
 * Screen time the way Digital Wellbeing counts it: time each app spends in the foreground, from Android's own
 * usage events, for one local day. The home screen, the system UI and this app don't count.
 */
object Usage {
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Local midnight, [daysAgo] days back. */
    fun dayStart(daysAgo: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    private fun homePackage(ctx: Context): String? = runCatching {
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    private fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Total foreground time and the per-app split for one day. */
    fun forDay(ctx: Context, daysAgo: Int): Pair<Long, List<AppUse>> {
        val start = dayStart(daysAgo)
        val end = minOf(dayStart(daysAgo - 1), System.currentTimeMillis())
        if (end <= start) return 0L to emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start - 6 * 3_600_000L, end)
        val skip = setOfNotNull(ctx.packageName, "com.android.systemui", homePackage(ctx))
        val open = HashMap<String, Int>()      // resumed activities per app
        val since = HashMap<String, Long>()    // when the app came to the front
        val total = HashMap<String, Long>()
        fun add(p: String, from: Long, to: Long) {
            val a = maxOf(from, start); val b = minOf(to, end)
            if (b > a) total[p] = (total[p] ?: 0L) + (b - a)
        }
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val p = e.packageName ?: continue
            val t = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val n = open[p] ?: 0
                    if (n == 0) since[p] = t
                    open[p] = n + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val n = open[p] ?: 0
                    if (n <= 1) { open.remove(p); since.remove(p)?.let { add(p, it, t) } } else open[p] = n - 1
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    for ((q, s) in since) add(q, s, t)
                    since.clear(); open.clear()
                }
            }
        }
        for ((q, s) in since) add(q, s, end)   // still on screen
        val apps = total.filter { it.key !in skip && it.value >= 1000 }
            .map { AppUse(it.key, label(ctx, it.key), it.value) }
            .sortedByDescending { it.ms }
        return apps.sumOf { it.ms } to apps
    }

    /** The oldest usage event Android still holds (it keeps detailed events for a limited time). */
    fun earliest(ctx: Context): Long? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 40L * 86_400_000L, now)
        val e = UsageEvents.Event()
        return if (ev.hasNextEvent()) { ev.getNextEvent(e); e.timeStamp } else null
    }

    fun hm(ms: Long): String {
        val m = ms / 60_000
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
