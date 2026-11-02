package com.evie.suicareader.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F3D2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4F3E3),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4A635A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FBF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF7FBF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF414944),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7BE3B0),
    onPrimary = Color(0xFF003821),
    primaryContainer = Color(0xFF235543),
    onPrimaryContainer = Color(0xFF9CF7C9),
    secondary = Color(0xFFB1CCC0),
    onSecondary = Color(0xFF1C352B),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun SuicaReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.hostActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

private fun View.hostActivity(): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
