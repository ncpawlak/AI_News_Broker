package com.ainewsbroker.ui

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun relativeTime(millis: Long, now: Long): String {
    val diff = (now - millis).coerceAtLeast(0)
    return when {
        diff < MINUTE -> "Just now"
        diff < HOUR -> "${diff / MINUTE}m ago"
        diff < DAY -> "${diff / HOUR}h ago"
        diff < 7 * DAY -> "${diff / DAY}d ago"
        else -> shortDate(millis)
    }
}

enum class DayBucket(val label: String) { Today("Today"), Yesterday("Yesterday"), ThisWeek("This week"), Older("Earlier") }

fun dayBucket(millis: Long, now: Long): DayBucket {
    val diff = now - millis
    return when {
        diff < DAY -> DayBucket.Today
        diff < 2 * DAY -> DayBucket.Yesterday
        diff < 7 * DAY -> DayBucket.ThisWeek
        else -> DayBucket.Older
    }
}

/** UTC date like "Nov 3"; good enough for items older than a week. */
private fun shortDate(millis: Long): String {
    val days = Math.floorDiv(millis, DAY)
    // civil_from_days (Howard Hinnant)
    val z = days + 719_468
    val era = Math.floorDiv(z, 146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    return "${monthNames[(m - 1).toInt()]} $d"
}

private object Math {
    fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q
    }
}
