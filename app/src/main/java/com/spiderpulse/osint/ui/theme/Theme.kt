package com.spiderpulse.osint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.spiderpulse.osint.R

val LocalIsDarkTheme = compositionLocalOf { true }

// Strict Dark Gray / Graphite MD3 Palette (No turquoise)
private val DarkMd3Scheme = darkColorScheme(
    primary = Color(0xFFB8B8C2),              // Clean metallic gray for icons, accents & active borders
    onPrimary = Color(0xFF141417),
    primaryContainer = Color(0xFF2C2C32),     // Dark gray container for buttons, badges & pills
    onPrimaryContainer = Color(0xFFF2F2F7),   // Crisp white-gray text on dark gray containers
    secondary = Color(0xFFA0A0AA),
    onSecondary = Color(0xFF18181C),
    secondaryContainer = Color(0xFF32323A),   // Dark gray for selected SegmentedButton, Chips & Nav pill
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFF90909A),
    onTertiary = Color(0xFF16161A),
    tertiaryContainer = Color(0xFF28282F),
    onTertiaryContainer = Color(0xFFE5E5EC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF691410),
    onError = Color(0xFF3B0604),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0D0F),           // Deep carbon dark background
    onBackground = Color(0xFFECECF0),
    surface = Color(0xFF121215),              // Solid dark graphite surface
    onSurface = Color(0xFFECECF0),
    surfaceVariant = Color(0xFF232329),       // Dark gray card background
    onSurfaceVariant = Color(0xFFB4B4BE),
    outline = Color(0xFF46464F)
)

private val LightMd3Scheme = lightColorScheme(
    primary = Color(0xFF2F2F35),              // Dark gray accent in Light theme
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A3A42),     // Dark gray pill/container
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF484850),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8D8E0),   // Soft slate gray for selected chips/tabs in Light theme
    onSecondaryContainer = Color(0xFF18181C),
    tertiary = Color(0xFF52525B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E2E8),
    onTertiaryContainer = Color(0xFF1C1C20),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF141417),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141417),
    surfaceVariant = Color(0xFFE6E6EC),
    onSurfaceVariant = Color(0xFF44444C),
    outline = Color(0xFF74747E)
)

@Composable
fun SpiderLogo(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val isDark = LocalIsDarkTheme.current
    val spiderColor = if (isDark) {
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
    }
    Icon(
        painter = painterResource(id = R.drawable.ic_spider_logo),
        contentDescription = "Spider Logo",
        modifier = modifier,
        tint = spiderColor
    )
}

@Composable
fun SpiderPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkMd3Scheme else LightMd3Scheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
