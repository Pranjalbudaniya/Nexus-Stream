package com.nexus.stream.presentation.theme

import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nexus.stream.domain.model.CardStyle
import com.nexus.stream.domain.model.ThemeSettings
import com.nexus.stream.presentation.theme.tokens.DarkExtendedColors
import com.nexus.stream.presentation.theme.tokens.ElevationTokens
import com.nexus.stream.presentation.theme.tokens.ExtendedColors
import com.nexus.stream.presentation.theme.tokens.LightExtendedColors
import com.nexus.stream.presentation.theme.tokens.LocalElevation
import com.nexus.stream.presentation.theme.tokens.LocalExtendedColors
import com.nexus.stream.presentation.theme.tokens.LocalMotion
import com.nexus.stream.presentation.theme.tokens.LocalNexusShapes
import com.nexus.stream.presentation.theme.tokens.LocalSpacing
import com.nexus.stream.presentation.theme.tokens.MotionTokens
import com.nexus.stream.presentation.theme.tokens.NexusShapes
import com.nexus.stream.presentation.theme.tokens.Spacing
import com.nexus.stream.presentation.theme.tokens.fontFamilyFor
import com.nexus.stream.presentation.theme.tokens.materialShapes
import com.nexus.stream.presentation.theme.tokens.nexusShapes
import com.nexus.stream.presentation.theme.tokens.nexusTypography

val LocalAmoledActive = staticCompositionLocalOf { false }
val LocalCardStyle = staticCompositionLocalOf { CardStyle.FILLED }

@Composable
fun NexusTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(settings.mode, isSystemInDarkTheme())
    val colorScheme = resolveColorScheme(settings, dark)
    val extendedColors = if (dark) DarkExtendedColors else LightExtendedColors
    val amoledActive = dark && settings.amoled

    val baseDensity = LocalDensity.current
    val density =
        remember(baseDensity, settings.uiSize, settings.textSize) {
            scaledDensity(baseDensity, settings.uiSize, settings.textSize)
        }
    val nexusShapes = remember(settings.roundness) { nexusShapes(settings.roundness.scale) }
    val materialShapes = remember(settings.roundness) { materialShapes(settings.roundness.scale) }
    val typography =
        remember(settings.font) {
            nexusTypography(fontFamilyFor(settings.font))
        }

    val context = LocalContext.current
    val activity = context as? ComponentActivity

    if (activity != null) {
        SideEffect {
            activity.window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
        }
        DisposableEffect(dark) {
            activity.enableEdgeToEdge(
                statusBarStyle =
                    SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                navigationBarStyle =
                    SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
            )
            onDispose {}
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalSpacing provides Spacing(),
        LocalNexusShapes provides nexusShapes,
        LocalMotion provides MotionTokens(),
        LocalElevation provides ElevationTokens(),
        LocalAmoledActive provides amoledActive,
        LocalCardStyle provides settings.cardStyle,
        LocalDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = materialShapes,
            content = content,
        )
    }
}

object NexusTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val shapes: NexusShapes
        @Composable get() = LocalNexusShapes.current

    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current

    val motion: MotionTokens
        @Composable get() = LocalMotion.current

    val elevation: ElevationTokens
        @Composable get() = LocalElevation.current

    val amoledActive: Boolean
        @Composable get() = LocalAmoledActive.current

    val cardStyle: CardStyle
        @Composable get() = LocalCardStyle.current
}
