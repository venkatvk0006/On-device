package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Gemini Inspired Brand Colors
val GeminiBlue = Color(0xFF388BFD)
val GeminiPurple = Color(0xFF9333EA)
val GeminiCyan = Color(0xFF06B6D4)
val GeminiIndigo = Color(0xFF6366F1)
val GeminiPink = Color(0xFFEC4899)

// Dark Theme Colors
val BackgroundDark = Color(0xFF090D16)
val SurfaceDark = Color(0xFF111827)
val SurfaceVariantDark = Color(0xFF1E293B)
val CardDark = Color(0xFF151E32)
val TextPrimaryDark = Color(0xFFF1F5F9)
val TextSecondaryDark = Color(0xFF94A3B8)
val BorderDark = Color(0xFF26334D)

// Light Theme Colors
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val CardLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)
val BorderLight = Color(0xFFE2E8F0)

// Vibrant Accents
val AccentCyan = Color(0xFF00E5FF)
val AccentPurple = Color(0xFFA855F7)
val AccentBlue = Color(0xFF3B82F6)

// Gradients
val GeminiGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF3B82F6),
        Color(0xFF8B5CF6),
        Color(0xFFD946EF)
    )
)

val GeminiGradientHorizontal = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF00D2FF),
        Color(0xFF3A7BD5),
        Color(0xFF8B5CF6)
    )
)

val CardGlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x33388BFD),
        Color(0x00000000)
    )
)
