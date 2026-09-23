package com.insuranceclaimsmapping.models

data class User(
    val uid: String = "",
    val customId: String = "",
    val email: String = "",
    val role: String = "", // PATIENT, HOSPITAL, INSURER
    val displayName: String = "",
    val profilePictureUrl: String = "",
    val phoneNumber: String = "",
    val insuranceProviderId: String = "",
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
