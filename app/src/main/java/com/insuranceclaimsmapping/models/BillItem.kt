package com.insuranceclaimsmapping.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BillItem(
    val description: String = "",
    val amount: Double = 0.0,
    var coveredAmount: Double = 0.0,
    var status: String = "PENDING", // COVERED, REJECTED, PENDING
    var reasoning: String = "",
    var fraudWarning: Boolean = false
) : Parcelable
