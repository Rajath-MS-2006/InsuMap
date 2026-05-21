package com.insuranceclaimsmapping.models

data class DashboardItem(
    val id: Int,
    val title: String,
    val iconResId: Int,
    val role: String // PATIENT, HOSPITAL, INSURER, ALL
)
