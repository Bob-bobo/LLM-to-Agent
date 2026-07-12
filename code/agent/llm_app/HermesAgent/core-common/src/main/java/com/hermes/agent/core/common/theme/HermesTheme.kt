package com.hermes.agent.core.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ========== Extended Color Palette (chat-specific) ==========
data class ExtendedColors(
    val userBubble: Color,
    val onUserBubble: Color,
    val assistantBubble: Color,
    val onAssistantBubble: Color,
    val streamingCursor: Color,
    val toolCallBackground: Color,
    val toolCallBorder: Color,
    val codeBackground: Color,
    val onCode: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        userBubble = Color.Unspecified,
        onUserBubble = Color.Unspecified,
        assistantBubble = Color.Unspecified,
        onAssistantBubble = Color.Unspecified,
        streamingCursor = Color.Unspecified,
        toolCallBackground = Color.Unspecified,
        toolCallBorder = Color.Unspecified,
        codeBackground = Color.Unspecified,
        onCode = Color.Unspecified,
    )
}

object HermesThemeExt {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

// ========== Light & Dark Schemes ==========
private val LightColorScheme = lightColorScheme(
    primary = HermesLight.Primary,
    onPrimary = HermesLight.OnPrimary,
    primaryContainer = HermesLight.PrimaryContainer,
    onPrimaryContainer = HermesLight.OnPrimaryContainer,
    secondary = HermesLight.Secondary,
    onSecondary = HermesLight.OnSecondary,
    secondaryContainer = HermesLight.SecondaryContainer,
    onSecondaryContainer = HermesLight.OnSecondaryContainer,
    tertiary = HermesLight.Tertiary,
    background = HermesLight.Background,
    onBackground = HermesLight.OnBackground,
    surface = HermesLight.Surface,
    onSurface = HermesLight.OnSurface,
    surfaceVariant = HermesLight.SurfaceVariant,
    onSurfaceVariant = HermesLight.OnSurfaceVariant,
    outline = HermesLight.Outline,
    outlineVariant = HermesLight.OutlineVariant,
    error = HermesLight.Error,
    onError = HermesLight.OnError,
)

private val DarkColorScheme = darkColorScheme(
    primary = HermesDark.Primary,
    onPrimary = HermesDark.OnPrimary,
    primaryContainer = HermesDark.PrimaryContainer,
    onPrimaryContainer = HermesDark.OnPrimaryContainer,
    secondary = HermesDark.Secondary,
    onSecondary = HermesDark.OnSecondary,
    secondaryContainer = HermesDark.SecondaryContainer,
    onSecondaryContainer = HermesDark.OnSecondaryContainer,
    tertiary = HermesDark.Tertiary,
    background = HermesDark.Background,
    onBackground = HermesDark.OnBackground,
    surface = HermesDark.Surface,
    onSurface = HermesDark.OnSurface,
    surfaceVariant = HermesDark.SurfaceVariant,
    onSurfaceVariant = HermesDark.OnSurfaceVariant,
    outline = HermesDark.Outline,
    outlineVariant = HermesDark.OutlineVariant,
    error = HermesDark.Error,
    onError = HermesDark.OnError,
)

private val LightExtendedColors = ExtendedColors(
    userBubble = HermesLight.UserBubble,
    onUserBubble = HermesLight.OnUserBubble,
    assistantBubble = HermesLight.AssistantBubble,
    onAssistantBubble = HermesLight.OnAssistantBubble,
    streamingCursor = HermesLight.StreamingCursor,
    toolCallBackground = HermesLight.ToolCallBackground,
    toolCallBorder = HermesLight.ToolCallBorder,
    codeBackground = HermesLight.CodeBackground,
    onCode = HermesLight.OnCode,
)

private val DarkExtendedColors = ExtendedColors(
    userBubble = HermesDark.UserBubble,
    onUserBubble = HermesDark.OnUserBubble,
    assistantBubble = HermesDark.AssistantBubble,
    onAssistantBubble = HermesDark.OnAssistantBubble,
    streamingCursor = HermesDark.StreamingCursor,
    toolCallBackground = HermesDark.ToolCallBackground,
    toolCallBorder = HermesDark.ToolCallBorder,
    codeBackground = HermesDark.CodeBackground,
    onCode = HermesDark.OnCode,
)

// ========== Theme Composable ==========
@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HermesTypography,
            shapes = HermesShapes,
            content = content
        )
    }
}
