package cloud.kvd.androidoptimizer.data

import android.content.Context
import cloud.kvd.androidoptimizer.domain.JournalEntry
import cloud.kvd.androidoptimizer.domain.decodeJournal
import cloud.kvd.androidoptimizer.domain.encodeJournal

/** Журнал применённых профилей. Хранится только на устройстве. */
class JournalStore(context: Context) {

    private val prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE)

    fun load(): List<JournalEntry> = decodeJournal(prefs.getString(KEY, "").orEmpty())

    fun save(entries: List<JournalEntry>) {
        prefs.edit().putString(KEY, encodeJournal(entries)).apply()
    }

    private companion object {
        const val KEY = "entries"
    }
}
