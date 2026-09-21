package com.buddy.ncmusic.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 网易云红，作为莫奈取色不可用时的品牌 fallback 强调色
private val BrandRed = Color(0xFFD13030)

val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF715B2E),
    surface = Color(0xFFFFF8F6),
    surfaceVariant = Color(0xFFF5DDDA),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    onSurface = Color(0xFF231918),
    onSurfaceVariant = Color(0xFF534341),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3AC),
    onPrimary = Color(0xFF680007),
    primaryContainer = Color(0xFF93000E),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB8),
    secondaryContainer = Color(0xFF5D3F3C),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFDCC28D),
    surface = Color(0xFF1A1110),
    surfaceVariant = Color(0xFF534341),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF0DEDC),
    onSurface = Color(0xFFF0DEDC),
    onSurfaceVariant = Color(0xFFD8C2BF),
)
