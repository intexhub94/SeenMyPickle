package com.pbcam.keygenpbcam

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@IgnoreExtraProperties
data class LicenseRecord(
    val deviceId: String = "",
    val name: String = "",
    val key: String = "",
    val status: String = "active",
    val dateCreated: Long = 0L,
    val lastCheckIn: Long? = null,
    val expiryTime: Long = Long.MAX_VALUE
)

object AdminCloudManager {
    // Force explicit Southeast Asia URL to bypass regional discovery issues
    private const val DB_URL = "https://seemypickle-default-rtdb.asia-southeast1.firebasedatabase.app/"
    private val rootDatabase = FirebaseDatabase.getInstance(DB_URL)
    private val licensesRef = rootDatabase.getReference("licenses")
    private val auth = FirebaseAuth.getInstance()

    init {
        try {
            rootDatabase.setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Persistence might already be enabled
        }
        licensesRef.keepSynced(true)
    }

    fun isAuthenticated(): Boolean = auth.currentUser != null

    fun signIn(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // FORCE RESTART CONNECTION with a small delay to let token propagate
                    rootDatabase.goOffline()
                    // Use a handler to wait 2 seconds before going back online
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        rootDatabase.goOnline()
                        onResult(true, null)
                    }, 2000)
                } else {
                    onResult(false, task.exception?.message ?: "Unknown Error")
                }
            }
    }

    fun signOut() {
        auth.signOut()
    }

    fun observeRawData(): Flow<String> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rawValue = snapshot.value?.toString() ?: "Empty Node ({})"
                trySend("LICENSES_DUMP: $rawValue")
            }
            override fun onCancelled(error: DatabaseError) {
                trySend("PERMISSION_DENIED: ${error.message}. (Attempted path: /licenses)")
            }
        }
        // FIX: Only listen to /licenses, NOT the root /
        licensesRef.addValueEventListener(listener)
        awaitClose { licensesRef.removeEventListener(listener) }
    }

    fun observeConnectionState(): Flow<Boolean> = callbackFlow {
        val connectedRef = rootDatabase.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                trySend(connected)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(listener)
        awaitClose { connectedRef.removeEventListener(listener) }
    }

    fun registerLicense(deviceId: String, name: String, key: String, expiryTime: Long = Long.MAX_VALUE) {
        val cleanId = deviceId.replace("-", "").trim().uppercase()
        val record = LicenseRecord(
            deviceId = deviceId.uppercase(),
            name = name,
            key = key,
            status = "active",
            dateCreated = System.currentTimeMillis(),
            expiryTime = expiryTime
        )
        licensesRef.child(cleanId).setValue(record)
    }

    fun revokeLicense(deviceId: String) {
        val cleanId = deviceId.replace("-", "").trim().uppercase()
        licensesRef.child(cleanId).child("status").setValue("revoked")
    }
    
    fun deleteLicense(deviceId: String) {
        val cleanId = deviceId.replace("-", "").trim().uppercase()
        licensesRef.child(cleanId).removeValue()
    }
    
    fun reactivateLicense(deviceId: String) {
        val cleanId = deviceId.replace("-", "").trim().uppercase()
        licensesRef.child(cleanId).child("status").setValue("active")
    }

    fun observeLicenses(): Flow<List<LicenseRecord>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("AdminCloudManager", "ON_DATA_CHANGE: Node count = ${snapshot.childrenCount}")
                val list = mutableListOf<LicenseRecord>()
                snapshot.children.forEach { child ->
                    try {
                        val name = child.child("name").value?.toString() ?: ""
                        val key = child.child("key").value?.toString() ?: ""
                        val status = child.child("status").value?.toString() ?: "active"
                        val dateCreated = (child.child("dateCreated").value as? Number)?.toLong() ?: 0L
                        val lastCheckIn = (child.child("lastCheckIn").value as? Number)?.toLong()

                        list.add(LicenseRecord(child.key ?: "", name, key, status, dateCreated, lastCheckIn))
                    } catch (e: Exception) {
                        Log.e("AdminCloudManager", "Mapping failed: ${e.message}")
                    }
                }
                trySend(list.sortedByDescending { it.dateCreated })
            }
            override fun onCancelled(error: DatabaseError) {
                // Fix: Don't call error.toException() as it crashes the Flow's main thread
                // Instead, send an empty list or a specific signal
                Log.e("AdminCloudManager", "Firebase Permission Denied: ${error.message}")
                trySend(emptyList()) 
            }
        }
        licensesRef.addValueEventListener(listener)
        awaitClose { licensesRef.removeEventListener(listener) }
    }
}
