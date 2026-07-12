package com.hermes.agent.core.common.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp

val HermesShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

// Chat bubble shapes
val UserBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 4.dp,
    bottomEnd = 16.dp
)

val AssistantBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 16.dp,
    bottomEnd = 4.dp
)
