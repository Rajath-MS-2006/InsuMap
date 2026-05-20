package com.insuranceclaimsmapping.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = InsurerCobalt,
    secondary = PatientAmber,
    tertiary = HospitalEmerald,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

// Force dark mode aesthetic globally for the futuristic feel
private val LightColorScheme = DarkColorScheme 

@Composable
fun InsuMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    role: String = "PATIENT",
    content: @Composable () -> Unit
) {
    // Dynamic Role Coloring
    val roleColor = getRoleColor(role)
    
    val colorScheme = DarkColorScheme.copy(
        primary = roleColor,
        secondary = if (role == "HOSPITAL") PatientAmber else HospitalEmerald
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
