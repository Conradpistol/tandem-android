package app.tandem.phone

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/** One day from Health Connect: steps, sleep that ended that day, lowest resting heart rate. Nulls when there's nothing. */
data class HealthDay(val steps: Long?, val sleepMin: Long?, val rhr: Long?)

/**
 * Steps, sleep and resting heart rate from Android's Health Connect (filled by Samsung Health, Google Fit, Fitbit,
 * watches…). Read-only, and only after the person grants each type in Health Connect's own screen.
 */
object Health {
    val PERMS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet() else HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()

    suspend fun day(ctx: Context, daysAgo: Int): HealthDay? {
        if (!available(ctx)) return null
        val c = HealthConnectClient.getOrCreate(ctx)
        val have = c.permissionController.getGrantedPermissions()
        val start = Instant.ofEpochMilli(Usage.dayStart(daysAgo))
        val end = Instant.ofEpochMilli(minOf(Usage.dayStart(daysAgo - 1), System.currentTimeMillis()))
        if (!end.isAfter(start)) return null
        var steps: Long? = null; var sleep: Long? = null; var rhr: Long? = null
        if (HealthPermission.getReadPermission(StepsRecord::class) in have) runCatching {
            val r = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))
            steps = r[StepsRecord.COUNT_TOTAL]
        }
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in have) runCatching {
            // sleep belongs to the day you woke up: sessions that ended inside this day
            val r = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start.minusSeconds(18 * 3600), end)))
            val ms = r.records.filter { !it.endTime.isBefore(start) && it.endTime.isBefore(end) }
                .sumOf { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
            if (ms > 0) sleep = ms / 60_000
        }
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in have) runCatching {
            val r = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end)))
            rhr = r.records.minOfOrNull { it.beatsPerMinute }
        }
        return if (steps == null && sleep == null && rhr == null) null else HealthDay(steps, sleep, rhr)
    }
}
