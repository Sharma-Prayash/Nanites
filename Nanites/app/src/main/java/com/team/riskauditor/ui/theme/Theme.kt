package com.team.riskauditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.riskauditor.model.RiskBand

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF00796F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9EEE8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF5B5C9A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C21),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C21),
    surfaceVariant = Color(0xFFE0E3EA),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFE9EDF3),
    surfaceContainerHighest = Color(0xFFE2E6ED),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFD6DAE2),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF0F4785),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF7FD6CB),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF005049),
    onSecondaryContainer = Color(0xFFC9EEE8),
    tertiary = Color(0xFFC2C1FF),
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFE0E2E9),
    surface = Color(0xFF0F141B),
    onSurface = Color(0xFFE0E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0A0E14),
    surfaceContainerLow = Color(0xFF171C23),
    surfaceContainer = Color(0xFF1B2028),
    surfaceContainerHigh = Color(0xFF252A32),
    surfaceContainerHighest = Color(0xFF30353D),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF3A3F47),
    error = Color(0xFFFFB4AB),
)

@Immutable
data class BandColors(val accent: Color, val container: Color, val onContainer: Color)

private val LightBands = mapOf(
    RiskBand.LOW to BandColors(Color(0xFF2E7D32), Color(0xFFDDF3DF), Color(0xFF0D3B12)),
    RiskBand.MEDIUM to BandColors(Color(0xFF9A6700), Color(0xFFFFEFC7), Color(0xFF3E2A00)),
    RiskBand.HIGH to BandColors(Color(0xFFC2410C), Color(0xFFFFE2D3), Color(0xFF4A1500)),
    RiskBand.CRITICAL to BandColors(Color(0xFFB3261E), Color(0xFFFADAD7), Color(0xFF410E0B)),
)

private val DarkBands = mapOf(
    RiskBand.LOW to BandColors(Color(0xFF8BD68F), Color(0xFF1C3720), Color(0xFFC8F0CA)),
    RiskBand.MEDIUM to BandColors(Color(0xFFF2C14E), Color(0xFF3A2E10), Color(0xFFFFE6A8)),
    RiskBand.HIGH to BandColors(Color(0xFFFFA36E), Color(0xFF45230F), Color(0xFFFFD9C4)),
    RiskBand.CRITICAL to BandColors(Color(0xFFFF8A80), Color(0xFF4A1A17), Color(0xFFFFDAD6)),
)

private val LocalBandColors = staticCompositionLocalOf { LightBands }

val RiskBand.colors: BandColors
    @Composable get() = LocalBandColors.current.getValue(this)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.3.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun NanitesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBandColors provides if (dark) DarkBands else LightBands) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
