package com.example.tripoo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tripoo.data.repository.AuthRepository
import com.example.tripoo.data.repository.UserRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class SplashNavigationState {
    TO_AUTH,
    TO_DASHBOARD
}

class SplashViewModelKt(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val _navigation = MutableLiveData<SplashNavigationState>()
    val navigation: LiveData<SplashNavigationState> = _navigation

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        checkAuthAndMigrate()
    }

    private fun checkAuthAndMigrate() {
        viewModelScope.launch {
            // Splash delay to show the brand animation
            delay(1500)

            val currentUser = authRepository.getCurrentUser()
            if (currentUser == null) {
                _navigation.postValue(SplashNavigationState.TO_AUTH)
                return@launch
            }

            try {
                val uid = currentUser.uid
                val firestore = FirebaseFirestore.getInstance()

                firestore.runTransaction { tx ->
                    val docRef = firestore.collection("users").document(uid)
                    val snapshot = tx.get(docRef)
                    if (!snapshot.exists()) {
                        return@runTransaction null
                    }

                    val activeTripId = snapshot.getString("activeTripId")
                    @Suppress("UNCHECKED_CAST")
                    val tripIds = (snapshot.get("tripIds") as? List<String>) ?: emptyList()

                    if (activeTripId != null && tripIds.isEmpty()) {
                        val updates = hashMapOf<String, Any?>(
                            "tripIds" to listOf(activeTripId),
                            "lastActiveTripId" to activeTripId,
                            "activeTripId" to FieldValue.delete()
                        )
                        @Suppress("UNCHECKED_CAST")
                        tx.update(docRef, updates as Map<String, Any>)
                    }
                    null
                }.await()

                // After migration (or if not needed), go to dashboard
                _navigation.postValue(SplashNavigationState.TO_DASHBOARD)
            } catch (e: Exception) {
                _error.postValue(e.message)
                // Fail open to dashboard so user is not stuck on splash
                _navigation.postValue(SplashNavigationState.TO_DASHBOARD)
            }
        }
    }
}

