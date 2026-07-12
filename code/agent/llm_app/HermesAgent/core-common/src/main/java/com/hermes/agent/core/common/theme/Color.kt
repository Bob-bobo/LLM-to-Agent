package com.hermes.agent.core.common.theme

import androidx.compose.ui.graphics.Color

// ========== Light Color Scheme ==========
object HermesLight {
    val Primary = Color(0xFF00897B)           // Teal 600
    val OnPrimary = Color.White
    val PrimaryContainer = Color(0xFFB2DFDB)  // Teal 100
    val OnPrimaryContainer = Color(0xFF004D40)
    val Secondary = Color(0xFF0288D1)         // Light Blue 700
    val OnSecondary = Color.White
    val SecondaryContainer = Color(0xFFB3E5FC)
    val OnSecondaryContainer = Color(0xFF01579B)
    val Tertiary = Color(0xFF7C4DFF)          // Deep Purple A200
    val Background = Color(0xFFFAFAFA)        // Near-white
    val OnBackground = Color(0xFF212121)
    val Surface = Color.White
    val OnSurface = Color(0xFF212121)
    val SurfaceVariant = Color(0xFFF5F5F5)
    val OnSurfaceVariant = Color(0xFF757575)
    val Outline = Color(0xFFE0E0E0)
    val OutlineVariant = Color(0xFFBDBDBD)
    val Error = Color(0xFFD32F2F)
    val OnError = Color.White

    // Chat-specific colors
    val UserBubble = Color(0xFF00897B)        // Teal
    val OnUserBubble = Color.White
    val AssistantBubble = Color(0xFFF5F5F5)   // Light gray
    val OnAssistantBubble = Color(0xFF212121)
    val StreamingCursor = Color(0xFF00897B)
    val ToolCallBackground = Color(0xFFE8F5E9) // Light green
    val ToolCallBorder = Color(0xFF66BB6A)     // Green 400
    val CodeBackground = Color(0xFF263238)     // Dark blue-gray
    val OnCode = Color(0xFFECEFF1)
}

// ========== Dark Color Scheme ==========
object HermesDark {
    val Primary = Color(0xFF4DB6AC)           // Teal 300
    val OnPrimary = Color(0xFF00332E)
    val PrimaryContainer = Color(0xFF004D40)
    val OnPrimaryContainer = Color(0xFFB2DFDB)
    val Secondary = Color(0xFF4FC3F7)         // Light Blue 300
    val OnSecondary = Color(0xFF01579B)
    val SecondaryContainer = Color(0xFF01579B)
    val OnSecondaryContainer = Color(0xFFB3E5FC)
    val Tertiary = Color(0xFFB388FF)          // Deep Purple 200
    val Background = Color(0xFF121212)        // Material dark
    val OnBackground = Color(0xFFE0E0E0)
    val Surface = Color(0xFF1E1E1E)
    val OnSurface = Color(0xFFE0E0E0)
    val SurfaceVariant = Color(0xFF2C2C2C)
    val OnSurfaceVariant = Color(0xFFBDBDBD)
    val Outline = Color(0xFF424242)
    val OutlineVariant = Color(0xFF616161)
    val Error = Color(0xFFEF5350)
    val OnError = Color(0xFF121212)

    // Chat-specific colors
    val UserBubble = Color(0xFF00695C)        // Darker teal
    val OnUserBubble = Color.White
    val AssistantBubble = Color(0xFF2C2C2C)   // Dark gray
    val OnAssistantBubble = Color(0xFFE0E0E0)
    val StreamingCursor = Color(0xFF4DB6AC)
    val ToolCallBackground = Color(0xFF1B5E20) // Dark green
    val ToolCallBorder = Color(0xFF66BB6A)
    val CodeBackground = Color(0xFF1E1E1E)
    val OnCode = Color(0xFFE0E0E0)
}
