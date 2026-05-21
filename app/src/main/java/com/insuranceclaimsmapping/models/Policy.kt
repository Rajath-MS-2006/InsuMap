package com.insuranceclaimsmapping.models

data class Policy(
    val insurerId: String = "",
    val name: String = "",
    val pdfUrl: String = "",
    val copayPercentage: Double = 0.0,
    val deductibleLimit: Double = 0.0,
    val coverageDetails: String = "",
    val version: Int = 1,
    val uploadedAt: Long = System.currentTimeMillis()
)
