package cloud.kvd.androidoptimizer

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OptimizerBlue = Color(0xFF2384FF)
val OptimizerTeal = Color(0xFF00B96B)

@Composable
fun OptimizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF0965D6), onPrimary = Color.White,
        secondary = OptimizerTeal, onSecondary = Color.White,
        background = Color.Black, onBackground = Color(0xFFF1F1F1),
        surface = Color(0xFF1B1B1B), onSurface = Color(0xFFF1F1F1),
        surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFB0B0B0),
        primaryContainer = Color(0xFF153762), onPrimaryContainer = Color(0xFFD7E8FF),
        secondaryContainer = Color(0xFF173D2C), onSecondaryContainer = Color(0xFFB3F3CC),
        outline = Color(0xFF777777)
    ), content = content)
}
