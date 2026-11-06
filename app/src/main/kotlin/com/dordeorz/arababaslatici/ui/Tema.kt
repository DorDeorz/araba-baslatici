package com.dordeorz.arababaslatici.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Kronometre ve Adımsayar ile aynı palet: yeşil vurgu, sıcak nötr zemin.

private val AcikRenkler = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B2E0E),
    inversePrimary = Color(0xFF81C784),
    secondary = Color(0xFF506350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8CC),
    onSecondaryContainer = Color(0xFF0D1F0E),
    tertiary = Color(0xFF3D6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB9EBFA),
    onTertiaryContainer = Color(0xFF001F28),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F2EE),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFF2F2EE),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE1E3DB),
    onSurfaceVariant = Color(0xFF44483F),
    outline = Color(0xFF75786F),
    outlineVariant = Color(0xFFC5C8BE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF0F1EB),
    surfaceDim = Color(0xFFD4D6CE),
    surfaceBright = Color(0xFFF8F8F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFECEDE7),
    surfaceContainer = Color(0xFFE6E7E1),
    surfaceContainerHigh = Color(0xFFE0E1DB),
    surfaceContainerHighest = Color(0xFFDADCD4),
)

private val KoyuRenkler = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0B3E11),
    primaryContainer = Color(0xFF1F3A21),
    onPrimaryContainer = Color(0xFFC8E6C9),
    inversePrimary = Color(0xFF2E7D32),
    secondary = Color(0xFFA6CFA8),
    onSecondary = Color(0xFF10391A),
    secondaryContainer = Color(0xFF2A4A2D),
    onSecondaryContainer = Color(0xFFCCE8CC),
    tertiary = Color(0xFF9ED0DF),
    onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF1E4E5A),
    onTertiaryContainer = Color(0xFFB9EBFA),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1C1A),
    onBackground = Color(0xFFE3E4DE),
    surface = Color(0xFF1B1C1A),
    onSurface = Color(0xFFE3E4DE),
    surfaceVariant = Color(0xFF44483F),
    onSurfaceVariant = Color(0xFFC5C8BE),
    outline = Color(0xFF8F9388),
    outlineVariant = Color(0xFF44483F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3E4DE),
    inverseOnSurface = Color(0xFF30312D),
    surfaceDim = Color(0xFF1B1C1A),
    surfaceBright = Color(0xFF3A3C37),
    surfaceContainerLowest = Color(0xFF101210),
    surfaceContainerLow = Color(0xFF1B1C1A),
    surfaceContainer = Color(0xFF262825),
    surfaceContainerHigh = Color(0xFF2C2E2A),
    surfaceContainerHighest = Color(0xFF33352F),
)

private val Yazilar = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 16.sp, fontFeatureSettings = "tnum"),
)

@Composable
fun Tema(koyu: Boolean, dinamik: Boolean, icerik: @Composable () -> Unit) {
    val renkler = when {
        dinamik -> if (koyu) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        koyu -> KoyuRenkler
        else -> AcikRenkler
    }
    MaterialTheme(colorScheme = renkler, typography = Yazilar, content = icerik)
}
