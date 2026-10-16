package com.team.riskauditor.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.team.riskauditor.model.RiskBand
import com.team.riskauditor.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads launcher icons off the main thread with a small memory cache. */
class AppIconLoader(private val pm: PackageManager) {
    private val cache = LruCache<String, ImageBitmap>(160)

    suspend fun load(packageName: String, px: Int): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap().also { cache.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

val LocalIconLoader = staticCompositionLocalOf<AppIconLoader?> { null }

@Composable
fun AppIcon(packageName: String, size: Dp = 44.dp) {
    val loader = LocalIconLoader.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName) { value = loader?.load(packageName, px) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(
                Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Rounded score tile: number on the band colour. */
@Composable
fun ScoreBadge(score: Int, band: RiskBand, modifier: Modifier = Modifier) {
    val c = band.colors
    Column(
        modifier
            .width(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.container)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "Risk score $score, ${band.label}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$score", color = c.onContainer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(band.label, color = c.onContainer, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun BandPill(band: RiskBand, modifier: Modifier = Modifier, text: String = band.label) {
    val c = band.colors
    Surface(modifier, shape = CircleShape, color = c.container) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(6.dp))
            Text(text, color = c.onContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Large circular gauge for the detail header. */
@Composable
fun ScoreGauge(score: Int, band: RiskBand, size: Dp = 112.dp) {
    val c = band.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier.size(size).semantics { contentDescription = "Risk score $score out of 100, ${band.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(c.accent, startAngle = 135f, sweepAngle = 270f * score / 100f, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(band.label, style = MaterialTheme.typography.labelMedium, color = c.accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}

/** Small status chip for observed states (On / Off / Unknown …). */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        Tone.Danger -> RiskBand.CRITICAL.colors.let { it.container to it.onContainer }
        Tone.Warning -> RiskBand.MEDIUM.colors.let { it.container to it.onContainer }
        Tone.Ok -> RiskBand.LOW.colors.let { it.container to it.onContainer }
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = bg) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

enum class Tone { Danger, Warning, Ok, Neutral }

@Composable
fun IconBubble(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
