package com.pbcam.app.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DeviceScreenMetrics(
    val densityDpi: Int,
    val density: Float,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestWidthDp: Int,
    val isTv: Boolean,
    val isTablet: Boolean,
    val isPhone: Boolean,
    val scaleFactor: Float,
    val controlIconSize: Dp,
    val controlButtonSize: Dp,
    val cardWidthDp: Dp
)

@Composable
fun rememberDeviceScreenMetrics(): DeviceScreenMetrics {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val dpi = config.densityDpi
    val sw = config.smallestScreenWidthDp
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val isTablet = sw >= 600 && !isTv
    val isPhone = !isTablet && !isTv

    val scaleFactor = when {
        isTv -> (sw / 500f).coerceIn(1.3f, 2.2f)
        isTablet -> 1.25f
        dpi >= 560 -> 1.15f
        dpi <= 240 -> 0.9f
        else -> 1.0f
    }

    return remember(dpi, sw, config.screenWidthDp, config.screenHeightDp) {
        DeviceScreenMetrics(
            densityDpi = dpi,
            density = density.density,
            screenWidthDp = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp,
            smallestWidthDp = sw,
            isTv = isTv,
            isTablet = isTablet,
            isPhone = isPhone,
            scaleFactor = scaleFactor,
            controlIconSize = (24 * scaleFactor).dp,
            controlButtonSize = if (isTablet) 72.dp else (56 * scaleFactor).dp,
            cardWidthDp = when {
                isTv -> 620.dp
                isTablet -> 400.dp
                else -> 320.dp
            }
        )
    }
}
