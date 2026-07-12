package com.hermes.agent.core.common.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * Hermes-specific window size classification.
 * Simplified from Material3 WindowSizeClass for our use case.
 */
enum class HermesWindowSize {
    /** Phone portrait - compact width */
    COMPACT,
    /** Phone landscape or small tablet - medium width */
    MEDIUM,
    /** Large tablet or foldable - expanded width */
    EXPANDED
}

/**
 * Determines the current window size class for adaptive layout.
 */
@Composable
fun hermesWindowSize(): HermesWindowSize {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val widthSizeClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass

    return when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> HermesWindowSize.COMPACT
        WindowWidthSizeClass.MEDIUM -> HermesWindowSize.MEDIUM
        else -> HermesWindowSize.EXPANDED
    }
}

/**
 * Whether to use a navigation rail instead of bottom navigation.
 */
@Composable
fun shouldUseNavRail(): Boolean = hermesWindowSize() != HermesWindowSize.COMPACT

/**
 * Whether to use a master-detail (list + detail) layout.
 */
@Composable
fun shouldUseMasterDetail(): Boolean = hermesWindowSize() == HermesWindowSize.EXPANDED
