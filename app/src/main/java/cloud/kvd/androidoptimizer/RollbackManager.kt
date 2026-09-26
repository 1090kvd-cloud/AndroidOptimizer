package cloud.kvd.androidoptimizer

import android.content.Context

data class RollbackResult(val success:Boolean,val message:String)

object RollbackManager {
    fun rollback(context:Context, record:ChangeRecord):RollbackResult {
        if(record.type != ChangeType.REVERSIBLE_CHANGE) {
            return RollbackResult(false,"Это запись журнала, а не изменение системы")
        }
        return RollbackResult(false,"Для этого типа изменения обработчик отката ещё не зарегистрирован")
    }
}
