package com.mochisofts.mata.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mochisofts.mata.domain.model.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MataThemeTest {
    @Test
    fun explicitTheme_overridesSystemTheme() {
        assertFalse(mataUsesDarkTheme(AppTheme.LIGHT, systemInDarkTheme = true))
        assertTrue(mataUsesDarkTheme(AppTheme.DARK, systemInDarkTheme = false))
        assertTrue(mataUsesDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = true))
        assertFalse(mataUsesDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun fixedColorSchemes_meetNormalTextContrast() {
        assertColorSchemeContrast(MataLightColors)
        assertColorSchemeContrast(MataDarkColors)
    }

    @Test
    fun semanticStatusColors_meetNormalTextContrast() {
        listOf(MataLightSemanticColors, MataDarkSemanticColors).forEach { colors ->
            assertContrastAtLeast(colors.onStatusSuccess, colors.statusSuccess, 4.5f)
            assertContrastAtLeast(
                colors.onStatusSuccessContainer,
                colors.statusSuccessContainer,
                4.5f,
            )
        }
    }

    @Test
    fun categoryPalettes_haveStableIdsAndIconContrast() {
        listOf(MataLightSemanticColors, MataDarkSemanticColors).forEach { colors ->
            assertEquals(16, colors.categoryColors.size)
            assertEquals(colors.categoryColors.size, colors.onCategoryColors.size)
            colors.categoryColors.zip(colors.onCategoryColors).forEach { (background, foreground) ->
                assertContrastAtLeast(foreground, background, 3f)
            }
        }
    }

    private fun assertColorSchemeContrast(colors: ColorScheme) {
        listOf(
            colors.onPrimary to colors.primary,
            colors.onPrimaryContainer to colors.primaryContainer,
            colors.onSecondary to colors.secondary,
            colors.onSecondaryContainer to colors.secondaryContainer,
            colors.onTertiary to colors.tertiary,
            colors.onTertiaryContainer to colors.tertiaryContainer,
            colors.onError to colors.error,
            colors.onErrorContainer to colors.errorContainer,
            colors.onBackground to colors.background,
            colors.onSurface to colors.surface,
            colors.onSurfaceVariant to colors.surfaceVariant,
            colors.inverseOnSurface to colors.inverseSurface,
        ).forEach { (foreground, background) ->
            assertContrastAtLeast(foreground, background, 4.5f)
        }
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("contrast ratio $ratio was below $minimum", ratio >= minimum)
    }
}
