package com.insuranceclaimsmapping.ui.theme

import androidx.compose.ui.graphics.Color

// Core Premium Colors
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)
val Slate800 = Color(0xFF1E293B)
val Slate300 = Color(0xFFCBD5E1)

// Role Base Colors
val PatientAmber = Color(0xFFF59E0B)
val PatientAmberDark = Color(0xFFB45309)
val HospitalEmerald = Color(0xFF10B981)
val HospitalEmeraldDark = Color(0xFF047857)
val InsurerCobalt = Color(0xFF3B82F6)
val InsurerCobaltDark = Color(0xFF1D4ED8)

// Semantic
val ErrorRed = Color(0xFFEF4444)
val SuccessGreen = Color(0xFF10B981)
val WarningYellow = Color(0xFFF59E0B)

// Translucency Helpers
val GlassWhite10 = Color.White.copy(alpha = 0.1f)
val GlassWhite20 = Color.White.copy(alpha = 0.2f)
val GlassWhite05 = Color.White.copy(alpha = 0.05f)
val GlassBlack20 = Color.Black.copy(alpha = 0.2f)
val GlassBlack50 = Color.Black.copy(alpha = 0.5f)

// Theme Helper Function
fun getRoleColor(role: String): Color {
    return when (role) {
        "HOSPITAL" -> HospitalEmerald
        "INSURER" -> InsurerCobalt
        "PATIENT" -> PatientAmber
        else -> Slate300
    }
}

fun getRoleColorDark(role: String): Color {
    return when (role) {
        "HOSPITAL" -> HospitalEmeraldDark
        "INSURER" -> InsurerCobaltDark
        "PATIENT" -> PatientAmberDark
        else -> Slate800
    }
}
