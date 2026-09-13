package com.facerem.core.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date helpers built on [Calendar] rather than `java.time`, because the app targets `minSdk 23`
 * without core-library desugaring.
 */
object AppClock {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeLabel = SimpleDateFormat("hh:mm a", Locale.US)
    private val dayLabel = SimpleDateFormat("EEE", Locale.US)
    private val longDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
    private val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US)

    fun todayIso(): String = isoDate.format(Date())

    fun isoDate(daysAgo: Int): String = isoDate.format(dateDaysAgo(daysAgo))

    fun dayLabel(daysAgo: Int): String = dayLabel.format(dateDaysAgo(daysAgo))

    fun longDateLabel(): String = longDate.format(Date())

    fun currentMonthLabel(): String = monthLabel.format(Date())

    fun nowTimeLabel(): String = timeLabel.format(Date())

    fun timeLabel(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return timeLabel.format(calendar.time)
    }

    fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    fun formatIsoAsReadable(iso: String): String = runCatching {
        val parsed = isoDate.parse(iso) ?: return iso
        SimpleDateFormat("d MMM yyyy", Locale.US).format(parsed)
    }.getOrDefault(iso)

    private fun dateDaysAgo(daysAgo: Int): Date = Calendar.getInstance()
        .apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
        .time
}

fun minutesToHoursLabel(minutes: Int): String {
    if (minutes <= 0) return "0h 0m"
    return "${minutes / 60}h ${minutes % 60}m"
}
