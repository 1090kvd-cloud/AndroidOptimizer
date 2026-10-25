package cloud.kvd.androidoptimizer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import cloud.kvd.androidoptimizer.domain.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Level.accentColor(): Color = when (this) {
    Level.NORMAL, Level.INFO -> MaterialTheme.colorScheme.primary
    Level.WARNING -> MaterialTheme.colorScheme.tertiary
    Level.CRITICAL -> MaterialTheme.colorScheme.error
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp)
    )
}

/** Кольцевой индикатор на 270°: плавно «заполняется» при появлении и при смене значения. */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "ring"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (progress > 0.005f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Сегментированная полоса; сегменты с нулевым весом пропускаются. */
@Composable
fun SegmentedBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.filter { it.first > 0f }.forEach { (weight, color) ->
            Box(modifier = Modifier.weight(weight).fillMaxHeight().background(color))
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusPill(text: String, level: Level, modifier: Modifier = Modifier) {
    val accent = level.accentColor()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) scheme.tertiaryContainer else scheme.surfaceVariant,
        contentColor = if (highlight) scheme.onTertiaryContainer else scheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Иконка грузится лениво и вне главного потока, чтобы не тормозить прокрутку. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        val loaded = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        value = loaded
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = modifier)
    } else {
        Spacer(modifier)
    }
}

@Composable
fun RecommendationCard(
    level: Level,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (level) {
        Level.CRITICAL -> scheme.errorContainer to scheme.onErrorContainer
        Level.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.surfaceContainerHigh to scheme.onSurface
    }
    val icon = when (level) {
        Level.NORMAL -> Icons.Filled.CheckCircle
        Level.INFO -> Icons.Filled.Info
        else -> Icons.Filled.Warning
    }
    val accent = level.accentColor()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(actionLabel) }
            }
        }
    }
}
