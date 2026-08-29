package com.pbcam.tv.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TvScreenMetrics(
    val densityDpi: Int,
    val density: Float,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestWidthDp: Int,
    val isTv: Boolean,
    val scaleFactor: Float,
    val dialogWidthDp: Dp,
    val pairingCardWidthDp: Dp
)

@Composable
fun rememberTvScreenMetrics(): TvScreenMetrics {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current

    val dpi = config.densityDpi
    val sw = config.smallestScreenWidthDp
    val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    val scaleFactor = when {
        isTv -> (sw / 500f).coerceIn(1.2f, 2.2f)
        sw >= 600 -> 1.25f
        dpi >= 560 -> 1.15f
        dpi <= 240 -> 0.9f
        else -> 1.0f
    }

    return remember(dpi, sw, config.screenWidthDp, config.screenHeightDp) {
        TvScreenMetrics(
            densityDpi = dpi,
            density = density.density,
            screenWidthDp = config.screenWidthDp,
            screenHeightDp = config.screenHeightDp,
            smallestWidthDp = sw,
            isTv = isTv,
            scaleFactor = scaleFactor,
            dialogWidthDp = (620 * scaleFactor).dp.coerceIn(520.dp, 800.dp),
            pairingCardWidthDp = (620 * scaleFactor).dp.coerceIn(520.dp, 750.dp)
        )
    }
}
