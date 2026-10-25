package cloud.kvd.androidoptimizer.ui

import android.content.Context
import cloud.kvd.androidoptimizer.R
import cloud.kvd.androidoptimizer.domain.BRIGHTNESS_AUTO
import cloud.kvd.androidoptimizer.domain.BRIGHTNESS_MAX
import cloud.kvd.androidoptimizer.domain.ProfileId
import cloud.kvd.androidoptimizer.domain.SettingChange
import cloud.kvd.androidoptimizer.domain.SettingField
import cloud.kvd.androidoptimizer.domain.durationParts
import java.util.Locale
import kotlin.math.roundToInt

fun formatDuration(context: Context, ms: Long): String {
    val (hours, minutes) = durationParts(ms)
    return when {
        hours > 0 -> context.getString(R.string.duration_hm, hours.toInt(), minutes.toInt())
        minutes > 0 -> context.getString(R.string.duration_m, minutes.toInt())
        else -> context.getString(R.string.duration_less_than_minute)
    }
}

fun formatTemperature(celsius: Float): String = String.format(Locale.getDefault(), "%.1f", celsius)

fun SettingField.labelRes(): Int = when (this) {
    SettingField.SCREEN_TIMEOUT -> R.string.setting_screen_timeout
    SettingField.BRIGHTNESS -> R.string.setting_brightness
}

fun ProfileId.titleRes(): Int = when (this) {
    ProfileId.SAVER -> R.string.profile_saver_title
    ProfileId.DAILY -> R.string.profile_daily_title
    ProfileId.SCREEN_ON -> R.string.profile_screen_on_title
}

fun ProfileId.descRes(): Int = when (this) {
    ProfileId.SAVER -> R.string.profile_saver_desc
    ProfileId.DAILY -> R.string.profile_daily_desc
    ProfileId.SCREEN_ON -> R.string.profile_screen_on_desc
}

fun formatSettingValue(context: Context, field: SettingField, value: Int?): String {
    if (value == null) return context.getString(R.string.value_unknown)
    return when (field) {
        SettingField.SCREEN_TIMEOUT -> {
            val seconds = value / 1000
            if (seconds >= 60 && seconds % 60 == 0) {
                context.getString(R.string.value_minutes, seconds / 60)
            } else {
                context.getString(R.string.value_seconds, seconds)
            }
        }
        SettingField.BRIGHTNESS ->
            if (value == BRIGHTNESS_AUTO) {
                context.getString(R.string.value_auto)
            } else {
                context.getString(R.string.value_percent, (value * 100f / BRIGHTNESS_MAX).roundToInt())
            }
    }
}

fun formatChange(context: Context, change: SettingChange): String = context.getString(
    R.string.change_format,
    context.getString(change.field.labelRes()),
    formatSettingValue(context, change.field, change.from),
    formatSettingValue(context, change.field, change.to)
)
