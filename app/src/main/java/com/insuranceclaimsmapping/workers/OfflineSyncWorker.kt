package com.insuranceclaimsmapping.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseFirestore.getInstance()
            
            // Aggressively fetch claims to populate local Firestore cache
            db.collection("claims").get().await()
            
            // Aggressively fetch policies to populate local Firestore cache
            db.collection("policies").get().await()
            
            Log.d("OfflineSyncWorker", "Successfully cached claims and policies for offline use.")
            Result.success()
        } catch (e: Exception) {
            Log.w("OfflineSyncWorker", "Sync failed, will retry: ${e.message}")
            Result.retry()
        }
    }
}
