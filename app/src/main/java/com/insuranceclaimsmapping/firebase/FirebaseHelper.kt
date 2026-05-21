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

    fun updateUserProfile(user: User, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        saveUserProfile(user, onSuccess, onFailure)
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

    fun uploadProfilePicture(fileUri: Uri, uid: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        uploadFile("profile_pics", "$uid.jpg", fileUri, onSuccess, onFailure)
    }

    fun addClaim(claim: Claim, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val claimWithUser = claim.copy(userId = currentUserId)
        claimsCollection.add(claimWithUser)
            .addOnSuccessListener { onSuccess(it.id) }
            .addOnFailureListener { onFailure(it) }
    }

    private val policiesCollection = db.collection("policies")

    fun savePolicy(policy: Policy, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(policy.insurerId).set(policy)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun savePolicyWithHistory(policy: Policy, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(policy.insurerId).get().addOnSuccessListener { doc ->
            val currentPolicy = doc.toObject(Policy::class.java)
            val nextVersion = (currentPolicy?.version ?: 0) + 1
            val policyToSave = policy.copy(version = nextVersion, uploadedAt = System.currentTimeMillis())
            
            policiesCollection.document(policy.insurerId).set(policyToSave)
                .addOnSuccessListener {
                    policiesCollection.document(policy.insurerId).collection("history")
                        .add(policyToSave)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onFailure(it) }
                }
                .addOnFailureListener { onFailure(it) }
        }.addOnFailureListener { onFailure(it) }
    }

    fun getPolicy(insurerId: String, onSuccess: (Policy?) -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(insurerId).get()
            .addOnSuccessListener { document ->
                val policy = document.toObject(Policy::class.java)
                onSuccess(policy)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPolicyHistory(insurerId: String, onSuccess: (List<Policy>) -> Unit, onFailure: (Exception) -> Unit) {
        policiesCollection.document(insurerId).collection("history")
            .orderBy("version", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val history = result.toObjects(Policy::class.java)
                onSuccess(history)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun deletePolicyHistory(insurerId: String, deletedVersion: Int, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val historyRef = policiesCollection.document(insurerId).collection("history")
        
        historyRef.whereEqualTo("version", deletedVersion).get()
            .addOnSuccessListener { deleteResult ->
                if (!deleteResult.isEmpty) {
                    deleteResult.documents[0].reference.delete().addOnSuccessListener {
                        
                        historyRef.whereGreaterThan("version", deletedVersion).get()
                            .addOnSuccessListener { renumberResult ->
                                val batch = db.batch()
                                
                                for (doc in renumberResult.documents) {
                                    val currentVer = doc.getLong("version") ?: continue
                                    batch.update(doc.reference, "version", currentVer - 1)
                                }
                                
                                val activePolicyRef = policiesCollection.document(insurerId)
                                activePolicyRef.get().addOnSuccessListener { activeDoc ->
                                    val activeVer = activeDoc.getLong("version") ?: 0
                                    if (activeVer > deletedVersion) {
                                        batch.update(activePolicyRef, "version", activeVer - 1)
                                    } else if (activeVer.toInt() == deletedVersion) {
                                        batch.update(activePolicyRef, "version", maxOf(1L, activeVer - 1))
                                    }
                                    
                                    batch.commit()
                                        .addOnSuccessListener { onSuccess() }
                                        .addOnFailureListener { onFailure(it) }
                                }.addOnFailureListener { onFailure(it) }
                                
                            }.addOnFailureListener { onFailure(it) }
                            
                    }.addOnFailureListener { onFailure(it) }
                } else {
                    onSuccess()
                }
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

    fun getFlaggedClaims(onSuccess: (List<Claim>) -> Unit, onFailure: (Exception) -> Unit) {
        claimsCollection.whereEqualTo("status", "FLAGGED")
            .get()
            .addOnSuccessListener { result ->
                val claims = result.documents.mapNotNull { safeMapToClaim(it) }
                onSuccess(claims)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun safeMapToClaim(doc: com.google.firebase.firestore.DocumentSnapshot?): Claim? {
        if (doc == null || !doc.exists()) return null
        return try {
            val data = doc.data ?: return null
            
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
                customPatientId = data["customPatientId"] as? String ?: "",
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
                coveredAmount = (data["coveredAmount"] as? Number)?.toDouble() ?: 0.0,
                patientLiability = (data["patientLiability"] as? Number)?.toDouble() ?: 0.0
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

    fun getUsersByName(query: String, onSuccess: (List<User>) -> Unit, onFailure: (Exception) -> Unit) {
        usersCollection
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
            .get()
            .addOnSuccessListener { result ->
                val users = result.toObjects(User::class.java)
                onSuccess(users)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getDuplicateClaims(patientId: String, hospital: String, amount: String, onResult: (Boolean) -> Unit) {
        claimsCollection
            .whereEqualTo("patientId", patientId)
            .whereEqualTo("hospital", hospital)
            .whereEqualTo("amount", amount)
            .get()
            .addOnSuccessListener { result ->
                onResult(!result.isEmpty)
            }
            .addOnFailureListener {
                onResult(false)
            }
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

    fun addClaimAppeal(claimId: String, note: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        claimsCollection.document(claimId)
            .update(
                mapOf(
                    "status" to "APPEAL_PENDING",
                    "appealNote" to note
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun saveFcmToken(uid: String, token: String) {
        usersCollection.document(uid).update("fcmToken", token)
    }
}
