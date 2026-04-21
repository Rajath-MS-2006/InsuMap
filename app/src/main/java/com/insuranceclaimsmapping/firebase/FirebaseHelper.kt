package com.insuranceclaimsmapping.firebase

import android.net.Uri
import com.insuranceclaimsmapping.models.Claim
import com.insuranceclaimsmapping.models.User
import com.insuranceclaimsmapping.models.Policy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FirebaseHelper {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = db.collection("users")
    private val claimsCollection = db.collection("claims")
    private val storage = com.google.firebase.storage.FirebaseStorage.getInstance().reference

    fun saveUserProfile(user: User, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection.document(user.uid).set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getUserProfile(uid: String, onSuccess: (User?) -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection.document(uid).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                onSuccess(user)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun uploadFile(folder: String, fileName: String, fileUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val fileRef = storage.child("$folder/$fileName")
        
        fileRef.putFile(fileUri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            fileRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onSuccess(task.result.toString())
            } else {
                onFailure(task.exception ?: Exception("Upload failed"))
            }
        }
    }

    fun addClaim(claim: Claim, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        claimsCollection.add(claim)
            .addOnSuccessListener { onSuccess(it.id) }
            .addOnFailureListener { onFailure(it) }
    }

    private val policiesCollection = db.collection("policies")

    fun savePolicy(policy: Policy, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(policy.insurerId).set(policy)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPolicy(insurerId: String, onSuccess: (Policy?) -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(insurerId).get()
            .addOnSuccessListener { document ->
                val policy = document.toObject(Policy::class.java)
                onSuccess(policy)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateClaim(claim: Claim, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        claimsCollection.document(claim.id).set(claim)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getClaimsByRole(role: String, userId: String, onSuccess: (List<Claim>) -> Unit, onFailure: (Exception) -> Unit) {
        val query = when (role) {
            "PATIENT" -> claimsCollection.whereEqualTo("patientId", userId)
            "HOSPITAL" -> claimsCollection.whereEqualTo("userId", userId)
            "INSURER" -> claimsCollection
            else -> claimsCollection.whereEqualTo("userId", userId)
        }

        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val claims = result.documents.mapNotNull { doc ->
                    safeMapToClaim(doc)
                }
                onSuccess(claims)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Safely maps a Firestore document to a Claim object, handling common type mismatches 
     * (like String vs Double) that cause crashes in toObject().
     */
    fun safeMapToClaim(doc: com.google.firebase.firestore.DocumentSnapshot?): Claim? {
        if (doc == null || !doc.exists()) return null
        return try {
            val data = doc.data ?: return null
            
            // Map items list first
            val itemsList = data["items"] as? List<Map<String, Any>>
            val billItems = itemsList?.map { itemMap ->
                com.insuranceclaimsmapping.models.BillItem(
                    description = itemMap["description"] as? String ?: "",
                    amount = (itemMap["amount"] as? Number)?.toDouble() ?: 0.0,
                    coveredAmount = (itemMap["coveredAmount"] as? Number)?.toDouble() ?: 0.0,
                    status = itemMap["status"] as? String ?: "PENDING",
                    reasoning = itemMap["reasoning"] as? String ?: ""
                )
            } ?: emptyList()

            Claim(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                patientId = data["patientId"] as? String ?: "",
                name = data["name"] as? String ?: "",
                hospital = data["hospital"] as? String ?: "",
                amount = data["amount"]?.toString() ?: "", 
                description = data["description"] as? String ?: "",
                status = data["status"] as? String ?: "PENDING",
                billUrl = data["billUrl"] as? String ?: "",
                policyUrl = data["policyUrl"] as? String ?: "",
                timestamp = data["timestamp"] as? com.google.firebase.Timestamp ?: com.google.firebase.Timestamp.now(),
                isBillLoaded = data["isBillLoaded"] as? Boolean ?: false,
                isPolicyLoaded = data["isPolicyLoaded"] as? Boolean ?: false,
                aiReasoning = data["aiReasoning"] as? String ?: "",
                items = billItems,
                coveredAmount = try {
                    when (val v = data["coveredAmount"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } catch (e: Exception) { 0.0 },
                patientLiability = try {
                    when (val v = data["patientLiability"]) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } catch (e: Exception) { 0.0 }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser

    fun getRoleUserCount(role: String, onSuccess: (Int) -> Unit) {
        usersCollection.whereEqualTo("role", role).get()
            .addOnSuccessListener { result ->
                onSuccess(result.size())
            }
            .addOnFailureListener {
                onSuccess(0)
            }
    }

    fun getUserIdByCustomId(customId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection.whereEqualTo("customId", customId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    onSuccess(result.documents[0].id)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun generateNextCustomId(role: String, onSuccess: (String) -> Unit) {
        getRoleUserCount(role) { count ->
            val prefix = when (role) {
                "PATIENT" -> "PAT"
                "HOSPITAL" -> "HOS"
                "INSURER" -> "INS"
                else -> "PAT"
            }
            val sequenceNumber = count + 1
            val customId = "$prefix-${String.format("%03d", sequenceNumber)}"
            onSuccess(customId)
        }
    }

    fun updateClaimLinkage(claimId: String, patientUid: String, customId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        claimsCollection.document(claimId)
            .update(
                mapOf(
                    "patientId" to patientUid,
                    "customPatientId" to customId
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
