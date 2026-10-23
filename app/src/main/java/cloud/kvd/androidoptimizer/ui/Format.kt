package cloud.kvd.androidoptimizer.ui

import android.content.Context
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.durationParts
import java.util.Locale

fun formatDuration(context: Context, ms: Long): String {
    val (hours, minutes) = durationParts(ms)
    return when {
        hours > 0 -> context.getString(R.string.duration_hm, hours.toInt(), minutes.toInt())
        minutes > 0 -> context.getString(R.string.duration_m, minutes.toInt())
        else -> context.getString(R.string.duration_less_than_minute)
    }
}

fun formatTemperature(celsius: Float): String = String.format(Locale.getDefault(), "%.1f", celsius)
