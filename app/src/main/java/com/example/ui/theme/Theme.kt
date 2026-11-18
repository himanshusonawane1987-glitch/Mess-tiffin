package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.AppLanguage

private val TiffinLightColorScheme = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = Color.White,
    primaryContainer = SkyBackgroundTint,
    onPrimaryContainer = DeepBlueText,
    secondary = SkySecondary,
    onSecondary = DeepBlueText,
    secondaryContainer = Color(0xFFEAF5FF),
    onSecondaryContainer = DeepBlueText,
    tertiary = DeepBlueText,
    onTertiary = Color.White,
    background = SkyBackground,
    onBackground = PrimaryText,
    surface = Color.White,
    onSurface = PrimaryText,
    surfaceVariant = SkyBackgroundTint,
    onSurfaceVariant = SecondaryText,
    outline = GlassBorderBlue,
    outlineVariant = Color(0xFFDCEBFB),
    error = ErrorCoral,
    onError = Color.White,
    errorContainer = ErrorCoralContainer,
    onErrorContainer = ErrorCoral
)

val TiffinShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TiffinMessTheme(
    language: AppLanguage = AppLanguage.DEFAULT,
    content: @Composable () -> Unit
) {
    ProvideAppLanguage(language = language) {
        MaterialTheme(
            colorScheme = TiffinLightColorScheme,
            typography = TiffinTypography,
            shapes = TiffinShapes,
            content = content
        )
    }
}
