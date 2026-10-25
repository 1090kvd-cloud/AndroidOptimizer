package cloud.kvd.androidoptimizer.domain

/** Значение яркости «автоматически». Ручная яркость — 1..255. */
const val BRIGHTNESS_AUTO = -1
const val BRIGHTNESS_MAX = 255

/** Допуск при сравнении ручной яркости: система округляет значения ползунка. */
private const val BRIGHTNESS_TOLERANCE = 3

enum class ProfileId { SAVER, DAILY, SCREEN_ON }

enum class SettingField { SCREEN_TIMEOUT, BRIGHTNESS }

/** Набор управляемых настроек; null — «не трогать». */
data class SettingsValues(
    val screenTimeoutMs: Int? = null,
    val brightness: Int? = null
) {
    /** Значения [other] поверх текущих; null в [other] оставляет текущее. */
    fun overlay(other: SettingsValues) = SettingsValues(
        screenTimeoutMs = other.screenTimeoutMs ?: screenTimeoutMs,
        brightness = other.brightness ?: brightness
    )

    fun valueOf(field: SettingField): Int? = when (field) {
        SettingField.SCREEN_TIMEOUT -> screenTimeoutMs
        SettingField.BRIGHTNESS -> brightness
    }

    fun encode(): String = listOfNotNull(
        screenTimeoutMs?.let { "t=$it" },
        brightness?.let { "b=$it" }
    ).joinToString(",")

    companion object {
        fun decode(text: String): SettingsValues {
            var t: Int? = null
            var b: Int? = null
            for (part in text.split(',')) {
                val kv = part.split('=', limit = 2)
                if (kv.size != 2) continue
                val n = kv[1].toIntOrNull() ?: continue
                when (kv[0]) {
                    "t" -> t = n
                    "b" -> b = n
                }
            }
            return SettingsValues(t, b)
        }
    }
}

/**
 * Профили меняют только то, что приложение вправе менять без root (разрешение WRITE_SETTINGS):
 * таймаут экрана и яркость. Вибро-отклик не трогаем: его системная константа объявлена устаревшей.
 * Профиля «Производительность» здесь нет намеренно: без Shizuku/root он ничего реально не ускорит.
 */
fun profileValues(id: ProfileId): SettingsValues = when (id) {
    ProfileId.SAVER -> SettingsValues(screenTimeoutMs = 15_000, brightness = 77) // ~30%
    ProfileId.DAILY -> SettingsValues(screenTimeoutMs = 60_000, brightness = BRIGHTNESS_AUTO)
    ProfileId.SCREEN_ON -> SettingsValues(screenTimeoutMs = 600_000)
}

data class SettingChange(val field: SettingField, val from: Int?, val to: Int)

private fun sameValue(field: SettingField, current: Int?, target: Int): Boolean {
    if (current == null) return false
    if (field == SettingField.BRIGHTNESS && current != BRIGHTNESS_AUTO && target != BRIGHTNESS_AUTO) {
        return kotlin.math.abs(current - target) <= BRIGHTNESS_TOLERANCE
    }
    return current == target
}

/** Что именно изменится при применении [target] к [current]. */
fun diff(current: SettingsValues, target: SettingsValues): List<SettingChange> =
    SettingField.entries.mapNotNull { field ->
        val to = target.valueOf(field) ?: return@mapNotNull null
        val from = current.valueOf(field)
        if (sameValue(field, from, to)) null else SettingChange(field, from, to)
    }

fun isApplied(current: SettingsValues, target: SettingsValues): Boolean = diff(current, target).isEmpty()

/** Запись журнала: снимок «до» позволяет вернуть настройки как были. */
data class JournalEntry(
    val id: Long,
    val profile: ProfileId,
    val before: SettingsValues,
    val after: SettingsValues,
    val reverted: Boolean = false
) {
    fun encode(): String =
        "$id|${profile.name}|${before.encode()}|${after.encode()}|${if (reverted) 1 else 0}"

    companion object {
        fun decode(line: String): JournalEntry? {
            val p = line.split('|')
            if (p.size != 5) return null
            val id = p[0].toLongOrNull() ?: return null
            val profile = ProfileId.entries.firstOrNull { it.name == p[1] } ?: return null
            return JournalEntry(id, profile, SettingsValues.decode(p[2]), SettingsValues.decode(p[3]), p[4] == "1")
        }
    }
}

const val JOURNAL_MAX = 20

fun encodeJournal(entries: List<JournalEntry>): String = entries.joinToString("\n") { it.encode() }

fun decodeJournal(text: String): List<JournalEntry> = text.lines().mapNotNull { JournalEntry.decode(it) }

/** Новые записи — в начало, хранится не больше [JOURNAL_MAX]. */
fun List<JournalEntry>.withNew(entry: JournalEntry): List<JournalEntry> = (listOf(entry) + this).take(JOURNAL_MAX)

/** Откатывать можно только самую свежую неотменённую запись — иначе снимки «до» теряют смысл. */
fun List<JournalEntry>.latestRevertable(): JournalEntry? = firstOrNull { !it.reverted }

fun List<JournalEntry>.markReverted(id: Long): List<JournalEntry> =
    map { if (it.id == id) it.copy(reverted = true) else it }
