package com.mochisofts.mata.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.domain.model.AppTheme

internal val MataLightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F397),
    onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386668),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBED),
    onTertiaryContainer = Color(0xFF002022),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFDF5),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFDFDF5),
    onSurface = Color(0xFF1A1C18),
    surfaceDim = Color(0xFFDDDDD5),
    surfaceBright = Color(0xFFFDFDF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7EF),
    surfaceContainer = Color(0xFFF1F1E9),
    surfaceContainerHigh = Color(0xFFEBEBE4),
    surfaceContainerHighest = Color(0xFFE5E5DE),
    surfaceVariant = Color(0xFFDFE4D7),
    onSurfaceVariant = Color(0xFF43483F),
    outline = Color(0xFF74796E),
    outlineVariant = Color(0xFFC3C8BC),
    inverseSurface = Color(0xFF2F312C),
    inverseOnSurface = Color(0xFFF1F1EA),
    inversePrimary = Color(0xFF9CD67D),
    surfaceTint = Color(0xFF386A20),
    scrim = Color(0xFF000000),
)

internal val MataDarkColors = darkColorScheme(
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
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C18),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF1A1C18),
    onSurface = Color(0xFFE3E3DC),
    surfaceDim = Color(0xFF1A1C18),
    surfaceBright = Color(0xFF40423C),
    surfaceContainerLowest = Color(0xFF141611),
    surfaceContainerLow = Color(0xFF22231F),
    surfaceContainer = Color(0xFF262823),
    surfaceContainerHigh = Color(0xFF30322D),
    surfaceContainerHighest = Color(0xFF3B3D37),
    surfaceVariant = Color(0xFF43483F),
    onSurfaceVariant = Color(0xFFC3C8BC),
    outline = Color(0xFF8D9387),
    outlineVariant = Color(0xFF43483F),
    inverseSurface = Color(0xFFE3E3DC),
    inverseOnSurface = Color(0xFF2F312C),
    inversePrimary = Color(0xFF386A20),
    surfaceTint = Color(0xFF9CD67D),
    scrim = Color(0xFF000000),
)

internal val MataCategoryLightColors = listOf(
    0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593,
    0xFF1565C0, 0xFF0277BD, 0xFF00838F, 0xFF00796B,
    0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFF9A825,
    0xFFEF6C00, 0xFFD84315, 0xFF5D4037, 0xFF546E7A,
).map(::Color)

internal val MataCategoryDarkColors = listOf(
    0xFFEF9A9A, 0xFFF48FB1, 0xFFCE93D8, 0xFF9FA8DA,
    0xFF90CAF9, 0xFF81D4FA, 0xFF80DEEA, 0xFF80CBC4,
    0xFFA5D6A7, 0xFFC5E1A5, 0xFFE6EE9C, 0xFFFFF59D,
    0xFFFFCC80, 0xFFFFAB91, 0xFFBCAAA4, 0xFFB0BEC5,
).map(::Color)

private val MataCategoryLightOnColors = List(MataCategoryLightColors.size) { index ->
    if (index in 11..13) Color.Black else Color.White
}
private val MataCategoryDarkOnColors = List(MataCategoryDarkColors.size) { Color.Black }

@Immutable
data class MataSemanticColors(
    val statusSuccess: Color,
    val onStatusSuccess: Color,
    val statusSuccessContainer: Color,
    val onStatusSuccessContainer: Color,
    val categoryColors: List<Color>,
    val onCategoryColors: List<Color>,
)

internal val MataLightSemanticColors = MataSemanticColors(
    statusSuccess = Color(0xFF2E7D32),
    onStatusSuccess = Color(0xFFFFFFFF),
    statusSuccessContainer = Color(0xFFB8F2B4),
    onStatusSuccessContainer = Color(0xFF002204),
    categoryColors = MataCategoryLightColors,
    onCategoryColors = MataCategoryLightOnColors,
)

internal val MataDarkSemanticColors = MataSemanticColors(
    statusSuccess = Color(0xFF9CD69A),
    onStatusSuccess = Color(0xFF003909),
    statusSuccessContainer = Color(0xFF15521E),
    onStatusSuccessContainer = Color(0xFFB8F2B4),
    categoryColors = MataCategoryDarkColors,
    onCategoryColors = MataCategoryDarkOnColors,
)

private val LocalMataSemanticColors = staticCompositionLocalOf { MataLightSemanticColors }

val MaterialTheme.mataColors: MataSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMataSemanticColors.current

private val MataShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DefaultTypography = Typography()
private val MataTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.withMataFont(),
    displayMedium = DefaultTypography.displayMedium.withMataFont(),
    displaySmall = DefaultTypography.displaySmall.withMataFont(),
    headlineLarge = DefaultTypography.headlineLarge.withMataFont(),
    headlineMedium = DefaultTypography.headlineMedium.withMataFont(),
    headlineSmall = DefaultTypography.headlineSmall.withMataFont(),
    titleLarge = DefaultTypography.titleLarge.withMataFont(),
    titleMedium = DefaultTypography.titleMedium.withMataFont(),
    titleSmall = DefaultTypography.titleSmall.withMataFont(),
    bodyLarge = DefaultTypography.bodyLarge.withMataFont(),
    bodyMedium = DefaultTypography.bodyMedium.withMataFont(),
    bodySmall = DefaultTypography.bodySmall.withMataFont(),
    labelLarge = DefaultTypography.labelLarge.withMataFont(),
    labelMedium = DefaultTypography.labelMedium.withMataFont(),
    labelSmall = DefaultTypography.labelSmall.withMataFont(),
)

private fun TextStyle.withMataFont(): TextStyle = copy(fontFamily = FontFamily.SansSerif)

internal fun mataUsesDarkTheme(appTheme: AppTheme, systemInDarkTheme: Boolean): Boolean =
    when (appTheme) {
        AppTheme.SYSTEM -> systemInDarkTheme
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

@Composable
fun mataUsesDarkTheme(appTheme: AppTheme): Boolean =
    mataUsesDarkTheme(appTheme, isSystemInDarkTheme())

@Composable
fun MataTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = mataUsesDarkTheme(appTheme)
    val colors: ColorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        MataDarkColors
    } else {
        MataLightColors
    }
    val semanticColors = if (dark) MataDarkSemanticColors else MataLightSemanticColors
    CompositionLocalProvider(LocalMataSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = MataTypography,
            shapes = MataShapes,
            content = content,
        )
    }
}
