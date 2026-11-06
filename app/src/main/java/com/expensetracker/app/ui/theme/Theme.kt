package com.expensetracker.app.ui.theme

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

private val CategoryPalette = listOf(
    Color(0xFF5C6BC0),
    Color(0xFF26A69A),
    Color(0xFFEF6C00),
    Color(0xFFAB47BC),
    Color(0xFF42A5F5),
    Color(0xFFEC407A),
    Color(0xFF66BB6A),
    Color(0xFF8D6E63),
    Color(0xFFFFB300),
    Color(0xFF78909C),
)

fun categoryColor(index: Int): Color = CategoryPalette[Math.floorMod(index, CategoryPalette.size)]

/** Follows the phone's light/dark setting and, on Android 12+, its wallpaper colours. */
@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
