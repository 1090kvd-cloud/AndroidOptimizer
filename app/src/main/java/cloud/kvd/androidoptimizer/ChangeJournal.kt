package cloud.kvd.androidoptimizer

import android.content.Context

data class ChangeRecord(val id:String,val description:String,val timestamp:Long)

object ChangeJournal {
    private const val PREFS="optimizer_journal"
    private const val KEY="records"

    fun add(context:Context, description:String) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val current=prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.add(System.currentTimeMillis().toString()+"|"+description)
        prefs.edit().putStringSet(KEY,current).apply()
    }

    fun read(context:Context):List<ChangeRecord> =
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull {
                val parts=it.split("|",limit=2)
                val time=parts.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
                ChangeRecord(it,parts.getOrElse(1){""},time)
            }.sortedByDescending{it.timestamp}

    fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
