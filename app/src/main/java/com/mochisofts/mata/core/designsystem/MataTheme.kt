package com.mochisofts.mata.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MataLightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F397),
    onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386668),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBED),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFFDFDF5),
    surface = Color(0xFFFDFDF5),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFDFE4D7),
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF74796E),
)

private val MataDarkColors = darkColorScheme(
    primary = Color(0xFF9CD67D),
    onPrimary = Color(0xFF0C3900),
    primaryContainer = Color(0xFF225106),
    onPrimaryContainer = Color(0xFFB7F397),
    secondary = Color(0xFFBDCBAF),
    onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A36),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFD1),
    onTertiary = Color(0xFF003739),
    tertiaryContainer = Color(0xFF1E4E50),
    onTertiaryContainer = Color(0xFFBCEBED),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF1A1C18),
    surface = Color(0xFF1A1C18),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BC),
    outline = Color(0xFF8D9387),
)

@Composable
fun MataTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        MataDarkColors
    } else {
        MataLightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

val CategoryLightColors = listOf(
    0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593,
    0xFF1565C0, 0xFF0277BD, 0xFF00838F, 0xFF00796B,
    0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFF9A825,
    0xFFEF6C00, 0xFFD84315, 0xFF5D4037, 0xFF546E7A,
).map(::Color)

