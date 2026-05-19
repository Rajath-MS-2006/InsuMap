package com.insuranceclaimsmapping.models

import com.google.firebase.Timestamp
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Claim(
    var id: String = "",
    val name: String = "",
    val hospital: String = "",
    val amount: String = "", // Total Bill Amount
    val description: String = "",
    val userId: String = "", // ID of the user who submitted (Hospital or Insurer)
    val patientId: String = "", // UID of the patient this claim belongs to
    val customPatientId: String = "", // Human-readable ID (e.g., PAT-001)
    val timestamp: Timestamp? = null,
    val status: String = "PENDING", // PENDING, ADJUDICATED
    val billUrl: String = "", 
    val policyUrl: String = "", 
    val isBillLoaded: Boolean = false,
    val isPolicyLoaded: Boolean = false,
    val items: List<BillItem> = emptyList(),
    val coveredAmount: Double = 0.0, 
    val patientLiability: Double = 0.0, 
    val aiReasoning: String = "",
    val fraudWarning: Boolean = false,
    val fraudReasoning: String = ""
) : Parcelable
