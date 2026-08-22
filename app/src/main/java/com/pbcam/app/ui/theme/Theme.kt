package com.pbcam.app.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pbcam.app.data.AppTheme

private val MidnightProColorScheme = darkColorScheme(
    primary = PickleGreen,
    onPrimary = MidnightBackground,
    primaryContainer = PickleGreen.copy(alpha = 0.2f),
    onPrimaryContainer = PickleGreen,
    
    secondary = BlazeOrange,
    onSecondary = OffWhite,
    secondaryContainer = BlazeOrange.copy(alpha = 0.2f),
    onSecondaryContainer = BlazeOrange,
    
    tertiary = DarkSlate,
    onTertiary = OffWhite,
    
    background = MidnightBackground,
    onBackground = OffWhite,
    
    surface = MidnightSurface,
    onSurface = OffWhite,
    surfaceVariant = DarkSlate,
    onSurfaceVariant = OffWhite.copy(alpha = 0.7f),
    
    error = RacingRed,
    onError = OffWhite,
    errorContainer = RacingRed.copy(alpha = 0.2f),
    onErrorContainer = RacingRed
)

private val SportyDarkColorScheme = darkColorScheme(
    primary = PickleGreen,
    onPrimary = Color.Black,
    primaryContainer = PickleGreen.copy(alpha = 0.15f),
    onPrimaryContainer = PickleGreen,
    
    secondary = BlazeOrange,
    onSecondary = Color.White,
    
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF252429),
    onSurface = Color(0xFFE6E1E5)
)

private val SportyLightColorScheme = lightColorScheme(
    primary = PickleGreenDark,
    onPrimary = Color.White,
    primaryContainer = PickleGreenDark.copy(alpha = 0.1f),
    onPrimaryContainer = PickleGreenDark,
    
    secondary = Color(0xFFD84315), // Deep orange for better contrast on white
    onSecondary = Color.White,
    
    background = Color(0xFFFDFBFF),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun PBCamTheme(
    themeMode: AppTheme = AppTheme.MIDNIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppTheme.LIGHT -> SportyLightColorScheme
        AppTheme.DARK -> SportyDarkColorScheme
        AppTheme.MIDNIGHT -> MidnightProColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            val isLight = themeMode == AppTheme.LIGHT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
