package com.autoclick.macro.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFF2557D6),
    secondary = Color(0xFF2E9E5B),
    tertiary = Color(0xFFD97706),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    secondary = Color(0xFF7CE0A3),
    tertiary = Color(0xFFFFB86B),
    error = Color(0xFFFFB4A8),
)

@Composable
fun AutoClickTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
