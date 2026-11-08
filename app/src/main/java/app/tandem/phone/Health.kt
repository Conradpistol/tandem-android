package app.tandem.phone

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A blood-pressure reading that day: time, systolic, diastolic, pulse if the cuff gave one. */
data class BpReading(val at: String, val s: Long, val d: Long, val h: Long?)

/** One exercise session: Health Connect's own id, a sport name, the app's title, start time, and what it measured. */
data class Workout(val id: String, val type: String, val title: String?, val start: String, val minutes: Long,
                   val km: Double?, val kcal: Long?, val hr: Long?, val source: String?)

/** One day from Health Connect. Nulls (and an empty list) when there's nothing. */
data class HealthDay(val steps: Long?, val sleepMin: Long?, val rhr: Long?,
                     val weightKg: Double? = null, val waterMl: Long? = null, val activeKcal: Long? = null,
                     val bp: List<BpReading> = emptyList(), val workouts: List<Workout> = emptyList()) {
    val empty get() = steps == null && sleepMin == null && rhr == null && weightKg == null && waterMl == null && activeKcal == null && bp.isEmpty() && workouts.isEmpty()
}

/**
 * Read-only access to Android's Health Connect, which Samsung Health, Google Fit, Fitbit, watches and scales fill.
 * Each type is read only after the person grants it in Health Connect's own screen.
 * Steps, sleep, resting heart rate, weight, water, active calories, blood pressure, and exercise sessions.
 */
object Health {
    /** What the first "Connect" asks for. */
    val PERMS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )
    /** The ones that are a kind of data (for the "n of m allowed" line). */
    val DATA_PERMS = PERMS.filter { !it.endsWith("BACKGROUND") }

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet() else HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()

    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private fun hm(t: Instant) = HM.format(t.atZone(ZoneId.systemDefault()))

    /** Health Connect's exercise type → Tandem's sport names (what Strava would call the same thing). */
    fun sport(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Ride"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "WeightTraining"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "StairStepper"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
        ExerciseSessionRecord.EXERCISE_TYPE_BOXING, ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Boxing"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS, ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON, ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
        ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL, ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "Racquet"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL, ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN, ExerciseSessionRecord.EXERCISE_TYPE_RUGBY, ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> "TeamSport"
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING, ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snow"
        ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Climb"
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golf"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretch"
        else -> "Workout"
    }

    suspend fun day(ctx: Context, daysAgo: Int): HealthDay? {
        if (!available(ctx)) return null
        val c = HealthConnectClient.getOrCreate(ctx)
        val have = c.permissionController.getGrantedPermissions()
        fun has(k: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(k) in have
        val start = Instant.ofEpochMilli(Usage.dayStart(daysAgo))
        val end = Instant.ofEpochMilli(minOf(Usage.dayStart(daysAgo - 1), System.currentTimeMillis()))
        if (!end.isAfter(start)) return null
        val range = TimeRangeFilter.between(start, end)
        var steps: Long? = null; var sleep: Long? = null; var rhr: Long? = null
        var weight: Double? = null; var water: Long? = null; var active: Long? = null
        val bp = ArrayList<BpReading>(); val workouts = ArrayList<Workout>()
        if (has(StepsRecord::class)) runCatching {
            steps = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL]
        }
        if (has(SleepSessionRecord::class)) runCatching {
            // sleep belongs to the day you woke up: sessions that ended inside this day
            val r = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start.minusSeconds(18 * 3600), end)))
            val ms = r.records.filter { !it.endTime.isBefore(start) && it.endTime.isBefore(end) }
                .sumOf { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
            if (ms > 0) sleep = ms / 60_000
        }
        if (has(RestingHeartRateRecord::class)) runCatching {
            rhr = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range)).records.minOfOrNull { it.beatsPerMinute }
        }
        if (has(WeightRecord::class)) runCatching {
            // the last weigh-in of the day
            weight = c.readRecords(ReadRecordsRequest(WeightRecord::class, range)).records.maxByOrNull { it.time }?.weight?.inKilograms
        }
        if (has(HydrationRecord::class)) runCatching {
            val l = c.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), range))[HydrationRecord.VOLUME_TOTAL]
            if (l != null && l.inMilliliters > 0) water = l.inMilliliters.toLong()
        }
        if (has(ActiveCaloriesBurnedRecord::class)) runCatching {
            val e = c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
            if (e != null && e.inKilocalories > 0) active = e.inKilocalories.toLong()
        }
        if (has(BloodPressureRecord::class)) runCatching {
            c.readRecords(ReadRecordsRequest(BloodPressureRecord::class, range)).records.sortedBy { it.time }.take(12).forEach {
                bp.add(BpReading(hm(it.time), it.systolic.inMillimetersOfMercury.toLong(), it.diastolic.inMillimetersOfMercury.toLong(), null))
            }
        }
        if (has(ExerciseSessionRecord::class)) runCatching {
            // sessions that started this day; Tandem Phone itself never writes any, so these are all someone else's recordings
            c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records.sortedBy { it.startTime }.take(20).forEach { s ->
                val sr = TimeRangeFilter.between(s.startTime, s.endTime)
                val mins = (s.endTime.toEpochMilli() - s.startTime.toEpochMilli()) / 60_000
                if (mins < 1) return@forEach
                var km: Double? = null; var kcal: Long? = null; var hr: Long? = null
                if (has(DistanceRecord::class)) runCatching {
                    c.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), sr))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.let { if (it > 0.05) km = Math.round(it * 100) / 100.0 }
                }
                if (has(TotalCaloriesBurnedRecord::class)) runCatching {
                    c.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), sr))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.let { if (it > 0) kcal = it.toLong() }
                }
                if (kcal == null && has(ActiveCaloriesBurnedRecord::class)) runCatching {
                    c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), sr))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.let { if (it > 0) kcal = it.toLong() }
                }
                if (has(HeartRateRecord::class)) runCatching {
                    c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), sr))[HeartRateRecord.BPM_AVG]?.let { hr = it }
                }
                workouts.add(Workout(s.metadata.id, sport(s.exerciseType), s.title?.takeIf { it.isNotBlank() }, hm(s.startTime), mins, km, kcal, hr,
                    s.metadata.dataOrigin.packageName.takeIf { it.isNotBlank() }))
            }
        }
        val d = HealthDay(steps, sleep, rhr, weight, water, active, bp, workouts)
        return if (d.empty) null else d
    }
}
