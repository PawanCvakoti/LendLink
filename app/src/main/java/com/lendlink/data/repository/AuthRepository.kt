package com.lendlink.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.lendlink.data.model.User
import com.lendlink.data.model.Wallet
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    val currentUid get() = auth.currentUser?.uid

    suspend fun checkUsernameExists(username: String): Boolean {
        if (username.isBlank()) return false
        return try {
            db.child("usernames/$username").get().await().exists()
        } catch (e: Exception) { false }
    }

    suspend fun checkEmailExists(email: String): Boolean {
        if (email.isBlank()) return false
        return try {
            auth.fetchSignInMethodsForEmail(email).await().signInMethods?.isNotEmpty() ?: false
        } catch (e: Exception) { false }
    }

    suspend fun checkPhoneExists(phone: String): Boolean {
        if (phone.isBlank()) return false
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        return try {
            db.child("phones/$cleanPhone").get().await().exists()
        } catch (e: Exception) { false }
    }

    suspend fun register(
        username: String, email: String, phone: String,
        password: String, role: String,
        latitude: Double, longitude: Double, locationAddress: String
    ): Result<User> = runCatching {
        if (checkUsernameExists(username)) throw Exception("Username already taken.")
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        if (checkPhoneExists(cleanPhone)) throw Exception("Phone number already used.")

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Registration failed.")

        val user = User(
            uid = uid, username = username, email = email, phone = phone,
            role = role, latitude = latitude, longitude = longitude,
            locationAddress = locationAddress,
            createdAt = System.currentTimeMillis()
        )
        
        val updates = hashMapOf<String, Any?>(
            "users/$uid" to user,
            "usernames/$username" to uid,
            "phones/$cleanPhone" to uid
        )
        db.updateChildren(updates).await()

        // Wallet setup
        val initialBalance = if (role == "borrower") 100_000L else 0L
        db.child("wallets/$uid").setValue(
            Wallet(userId = uid, balance = initialBalance, lastUpdated = System.currentTimeMillis())
        ).await()

        if (role == "lender") {
            listOf("Books", "Games", "Electronics", "Car", "Bike").forEachIndexed { i, cat ->
                val catId = "default_$i"
                db.child("categories/$uid/$catId").setValue(
                    mapOf("categoryId" to catId, "name" to cat, "lenderId" to uid, "createdAt" to System.currentTimeMillis())
                ).await()
            }
        }

        auth.signOut()
        user
    }

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val trimmedEmail = email.trim()
        
        // Attempt login first. If it fails, we check why.
        try {
            val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val uid = result.user?.uid ?: throw Exception("Login failed.")
            val snap = db.child("users/$uid").get().await()
            snap.getValue(User::class.java) ?: throw Exception("User data not found.")
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            throw Exception("Account not found — please register")
        } catch (e: Exception) {
            // Check if email exists in database as a fallback for custom error message
            if (!checkEmailExists(trimmedEmail)) {
                throw Exception("Account not found — please register")
            }
            throw e
        }
    }

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            db.child("users/$uid").get().await().getValue(User::class.java)
        } catch (_: Exception) { null }
    }

    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(User::class.java))
            }
            override fun onCancelled(error: DatabaseError) {
                // Safely close without throwing if it's a permission error (often happens during logout)
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    close()
                } else {
                    close(error.toException())
                }
            }
        }
        db.child("users/$uid").addValueEventListener(listener)
        awaitClose { db.child("users/$uid").removeEventListener(listener) }
    }

    suspend fun updateProfileImage(uid: String, imageBytes: ByteArray?): Result<String> = runCatching {
        if (imageBytes == null) throw Exception("No image data")
        val ref = storage.child("profile_images/$uid.jpg")
        ref.putBytes(imageBytes).await()
        val url = ref.downloadUrl.await().toString()
        db.child("users/$uid/profileImageUrl").setValue(url).await()
        url
    }

    fun logout() = auth.signOut()
}
