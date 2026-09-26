package cloud.kvd.androidoptimizer

import android.content.Context

enum class ChangeType { NAVIGATION, REVERSIBLE_CHANGE }

data class ChangeRecord(
    val id:String,
    val description:String,
    val timestamp:Long,
    val type:ChangeType,
    val rollbackPayload:String?=null
)

object ChangeJournal {
    private const val PREFS="optimizer_journal"
    private const val KEY="records"

    fun add(context:Context, description:String, type:ChangeType=ChangeType.NAVIGATION, rollbackPayload:String?=null) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val current=prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        val cleanDescription=description.replace("|"," ")
        val cleanPayload=rollbackPayload?.replace("|"," ")
        current.add(listOf(System.currentTimeMillis().toString(),type.name,cleanDescription,cleanPayload.orEmpty()).joinToString("|"))
        prefs.edit().putStringSet(KEY,current).apply()
    }

    fun read(context:Context):List<ChangeRecord> =
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().mapNotNull {
            val parts=it.split("|",limit=4)
            val time=parts.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            if(parts.size>=3) {
                ChangeRecord(it,parts[2],time,runCatching{ChangeType.valueOf(parts[1])}.getOrDefault(ChangeType.NAVIGATION),parts.getOrNull(3)?.ifBlank{null})
            } else {
                ChangeRecord(it,parts.getOrElse(1){""},time,ChangeType.NAVIGATION)
            }
        }.sortedByDescending{it.timestamp}

    fun remove(context:Context,id:String) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val current=prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        current.remove(id)
        prefs.edit().putStringSet(KEY,current).apply()
    }

    fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
