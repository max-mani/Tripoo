package com.manikandan.tripoo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun signOut() = auth.signOut()

    fun signInWithEmailPassword(email: String, password: String, callback: (FirebaseUser?, Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { signIn(email, password) }
            result.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it) }
            )
        }
    }

    fun signUpWithEmailPassword(email: String, password: String, callback: (FirebaseUser?, Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { signUp(email, password) }
            result.fold(
                onSuccess = { callback(it, null) },
                onFailure = { callback(null, it) }
            )
        }
    }
}
