package cloud.kvd.androidoptimizer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OptimizerBlue = Color(0xFF2762F3)
val OptimizerTeal = Color(0xFF0F9F91)

@Composable
fun OptimizerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFAFC6FF), background = Color(0xFF101724), surface = Color(0xFF182131),
        surfaceVariant = Color(0xFF263246), secondary = Color(0xFF70DDCB)
    ) else lightColorScheme(
        primary = OptimizerBlue, secondary = OptimizerTeal,
        background = Color(0xFFF3F6FC), surface = Color.White, surfaceVariant = Color(0xFFE9EFF9),
        onSurface = Color(0xFF18243C), onSurfaceVariant = Color(0xFF63718B),
        primaryContainer = Color(0xFFDFE9FF), onPrimaryContainer = Color(0xFF163674)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
