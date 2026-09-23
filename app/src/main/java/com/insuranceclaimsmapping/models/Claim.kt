package com.insuranceclaimsmapping.models

import com.google.firebase.Timestamp
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Claim(
    var id: String = "",
    val name: String = "",
    val hospital: String = "",
    val amount: String = "",
    val description: String = "",
    val userId: String = "",
    val patientId: String = "",
    val customPatientId: String = "",
    val timestamp: Timestamp? = null,
    val status: String = "PENDING",
    val billUrl: String = "",
    val policyUrl: String = "",
    val isBillLoaded: Boolean = false,
    val isPolicyLoaded: Boolean = false,
    val items: List<BillItem> = emptyList(),
    val coveredAmount: Double = 0.0,
    val patientLiability: Double = 0.0,
    val aiReasoning: String = "",
    val fraudWarning: Boolean = false,
    val fraudReasoning: String = "",
    val appealNote: String = ""
) : Parcelable
