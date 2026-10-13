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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import com.nexus.stream.presentation.theme.tokens.MaterialShapes
import com.nexus.stream.presentation.theme.tokens.MotionTokens
import com.nexus.stream.presentation.theme.tokens.NexusShapes
import com.nexus.stream.presentation.theme.tokens.NexusTypography
import com.nexus.stream.presentation.theme.tokens.Spacing

val LocalAmoledActive = staticCompositionLocalOf { false }

@Composable
fun NexusTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(settings.mode, isSystemInDarkTheme())
    val colorScheme = resolveColorScheme(settings, dark)
    val extendedColors = if (dark) DarkExtendedColors else LightExtendedColors
    val amoledActive = dark && settings.amoled

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
        LocalNexusShapes provides NexusShapes(),
        LocalMotion provides MotionTokens(),
        LocalElevation provides ElevationTokens(),
        LocalAmoledActive provides amoledActive,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            shapes = MaterialShapes,
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
}
