package cloud.kvd.androidoptimizer

enum class OemRisk { RECOMMENDED_REVIEW, CAUTION, PROTECTED }

data class KnownOemPackage(
    val packageName:String,
    val vendor:String,
    val title:String,
    val risk:OemRisk,
    val note:String
)

object OemDebloatDatabase {
    private val entries=listOf(
        KnownOemPackage("com.google.android.printservice.recommendation","Google","Print service recommendations",OemRisk.CAUTION,"Подбирает службы печати для обнаруженных принтеров. Это не признак рекламы или слежки; отключение может нарушить подбор служб печати."),
        KnownOemPackage("com.transsion.hamal","Transsion","User experience logging",OemRisk.RECOMMENDED_REVIEW,"Сообщество связывает пакет с журналированием пользовательского опыта."),
        KnownOemPackage("com.transsion.trancare","Transsion","Telemetry candidate",OemRisk.RECOMMENDED_REVIEW,"Кандидат телеметрии; требуется проверка на конкретной прошивке."),
        KnownOemPackage("com.transsion.statisticalsales","Transsion","Statistical sales",OemRisk.RECOMMENDED_REVIEW,"Служебная статистика производителя."),
        KnownOemPackage("com.transsion.magazineservice.hios","TECNO/HiOS","Magazine service",OemRisk.RECOMMENDED_REVIEW,"Контент/рекомендации экрана блокировки."),
        KnownOemPackage("com.transsion.carlcare","Transsion","Carlcare",OemRisk.RECOMMENDED_REVIEW,"Сервисное приложение производителя."),
        KnownOemPackage("com.transsnet.store","Transsion","Palm Store",OemRisk.RECOMMENDED_REVIEW,"OEM-магазин приложений."),
        KnownOemPackage("net.bat.store","Transsion","AHA Games",OemRisk.RECOMMENDED_REVIEW,"OEM-каталог игр."),
        KnownOemPackage("com.talpa.hibrowser","TECNO/HiOS","Hi Browser",OemRisk.RECOMMENDED_REVIEW,"OEM-браузер."),
        KnownOemPackage("com.transsion.zahooc","Transsion","Privacy features",OemRisk.CAUTION,"Может обеспечивать App Lock, App Twin и другие функции приватности."),
        KnownOemPackage("com.transsion.batterylab","Transsion","Battery features",OemRisk.CAUTION,"На некоторых HiOS содержит экран батареи и энергосбережения."),
        KnownOemPackage("com.transsion.resolver","Transsion","Share resolver",OemRisk.CAUTION,"Отключение может ухудшить системное меню «Поделиться»."),
        KnownOemPackage("com.hoffnung","Transsion","Core package",OemRisk.PROTECTED,"Не отключать: в community reports связан с риском нарушения загрузки устройства.")
    )
    fun find(packageName:String):KnownOemPackage?=entries.firstOrNull{it.packageName==packageName}
}
