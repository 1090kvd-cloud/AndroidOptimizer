package cloud.kvd.androidoptimizer.data

import android.content.Context
import android.provider.Settings
import cloud.kvd.androidoptimizer.domain.BRIGHTNESS_AUTO
import cloud.kvd.androidoptimizer.domain.SettingsValues

/**
 * Чтение и запись безопасных настроек экрана: таймаут и яркость.
 * Запись требует специального разрешения WRITE_SETTINGS, которое пользователь выдаёт вручную.
 */
class SystemSettingsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    fun read(): SettingsValues {
        val timeout = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        val mode = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val level = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val brightness = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) BRIGHTNESS_AUTO else level
        return SettingsValues(screenTimeoutMs = timeout, brightness = brightness)
    }

    /** Применяет только заданные (не null) значения. Возвращает true, если все записи прошли. */
    fun apply(values: SettingsValues): Boolean {
        if (!canWrite()) return false
        return try {
            var ok = true
            values.screenTimeoutMs?.let {
                ok = Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, it) && ok
            }
            values.brightness?.let { b ->
                if (b == BRIGHTNESS_AUTO) {
                    ok = Settings.System.putInt(
                        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    ) && ok
                } else {
                    ok = Settings.System.putInt(
                        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    ) && ok
                    ok = Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, b.coerceIn(1, 255)) && ok
                }
            }
            ok
        } catch (e: SecurityException) {
            false
        }
    }
}
