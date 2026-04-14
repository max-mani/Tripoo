package com.manikandan.tripoo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.data.model.User
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.ExpenseRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import com.manikandan.tripoo.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    data class ProfileStats(
        val tripCount: Int,
        val activeTripCount: Int,
        val friendsUnique: Int,
        val spentTotal: Double,
        val spentCompact: String,
        val spentFullInr: String,
    )

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val tripRepository = TripRepository()
    private val expenseRepository = ExpenseRepository()

    private val userLiveData = MutableLiveData<Resource<User>>()
    private val updateProfileLiveData = MutableLiveData<Resource<String>>()
    private val uploadImageLiveData = MutableLiveData<Resource<String>>()
    private val profileStatsLiveData = MutableLiveData<ProfileStats?>()
    /** Success: message; special [MSG_ACCOUNT_DELETED] triggers sign-out navigation. */
    private val accountMessageLiveData = MutableLiveData<Resource<String>?>()

    init {
        loadUser()
    }

    companion object {
        const val MSG_ACCOUNT_DELETED = "__ACCOUNT_DELETED__"

        fun formatInrCompact(amount: Double): String {
            if (amount <= 0) return "₹0"
            val lac = 100_000.0
            val cr = 10_000_000.0
            return when {
                amount >= cr -> String.format(Locale.US, "₹%.1fCr", amount / cr)
                amount >= lac -> String.format(Locale.US, "₹%.1fL", amount / lac)
                amount >= 1000 -> String.format(Locale.US, "₹%.1fk", amount / 1000)
                else -> String.format(Locale.US, "₹%.0f", amount)
            }
        }

        fun formatInrFull(amount: Double): String {
            val sym = DecimalFormatSymbols(Locale.forLanguageTag("en-IN"))
            val df = DecimalFormat("#,##,##0", sym)
            return "₹${df.format(kotlin.math.round(amount).toLong())}"
        }
    }

    private fun loadUser() {
        val firebaseUser = authRepository.getCurrentUser() ?: return
        userRepository.getUser(firebaseUser.uid) { user ->
            if (user != null) {
                userLiveData.value = Resource.success(user)
                loadProfileStats(firebaseUser.uid, user.tripIds)
            } else {
                userLiveData.value = Resource.error("Failed to load user")
                profileStatsLiveData.value = null
            }
        }
    }

    private fun loadProfileStats(uid: String, tripIds: List<String>) {
        viewModelScope.launch {
            if (tripIds.isEmpty()) {
                profileStatsLiveData.postValue(
                    ProfileStats(0, 0, 0, 0.0, "₹0", "₹0"),
                )
                return@launch
            }
            val stats = withContext(Dispatchers.IO) {
                val trips = tripRepository.getTripsForUser(tripIds)
                val active = trips.count { it.status == "active" }
                var spent = 0.0
                val others = mutableSetOf<String>()
                for (t in trips) {
                    spent += expenseRepository.getTotalExpenses(t.id)
                    for (mid in t.memberIds) {
                        if (mid != uid) others.add(mid)
                    }
                }
                ProfileStats(
                    tripCount = trips.size,
                    activeTripCount = active,
                    friendsUnique = others.size,
                    spentTotal = spent,
                    spentCompact = formatInrCompact(spent),
                    spentFullInr = formatInrFull(spent),
                )
            }
            profileStatsLiveData.postValue(stats)
        }
    }

    fun refreshUser() {
        loadUser()
    }

    fun clearUser() {
        userLiveData.value = Resource.error("Logged out")
    }

    fun updateProfile(name: String, photoUrl: String?) {
        updateProfileLiveData.value = Resource.loading()
        val firebaseUser = authRepository.getCurrentUser()
        if (firebaseUser != null) {
            userRepository.updateProfile(firebaseUser.uid, name, photoUrl) { err ->
                if (err == null) {
                    updateProfileLiveData.value = Resource.success("Profile updated successfully")
                    loadUser()
                } else {
                    updateProfileLiveData.value =
                        Resource.error(err.message ?: "Failed to update profile")
                }
            }
        }
    }

    fun uploadProfileImage(base64Image: String) {
        uploadImageLiveData.value = Resource.loading()
        val firebaseUser = authRepository.getCurrentUser()
        if (firebaseUser == null) {
            uploadImageLiveData.value = Resource.error("User not logged in")
            return
        }
        if (base64Image.isBlank()) {
            uploadImageLiveData.value = Resource.error("Image data is empty")
            return
        }
        uploadImageLiveData.value = Resource.success(base64Image)
    }

    fun savePhotoToFirestore(base64Photo: String) {
        val firebaseUser = authRepository.getCurrentUser() ?: return
        userRepository.getUser(firebaseUser.uid) { user ->
            if (user != null) {
                val currentName =
                    user.name.takeIf { it.isNotBlank() }
                        ?: (firebaseUser.displayName ?: "User")
                userRepository.updateProfile(firebaseUser.uid, currentName, base64Photo) { err ->
                    if (err == null) loadUser()
                }
            }
        }
    }

    fun updateEmail(newEmail: String) {
        viewModelScope.launch {
            try {
                val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                val trimmed = newEmail.trim()
                withContext(Dispatchers.IO) {
                    u.updateEmail(trimmed).await()
                    userRepository.updateDocumentEmail(u.uid, trimmed)
                }
                loadUser()
                accountMessageLiveData.postValue(Resource.success("Email updated"))
            } catch (e: Exception) {
                accountMessageLiveData.postValue(
                    Resource.error(e.message ?: "Could not update email. Try signing in again."),
                )
            }
        }
    }

    fun updatePhone(phone: String) {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUser()?.uid ?: return@launch
                withContext(Dispatchers.IO) {
                    userRepository.updatePhoneNumber(uid, phone)
                }
                loadUser()
                accountMessageLiveData.postValue(Resource.success("Phone number saved"))
            } catch (e: Exception) {
                accountMessageLiveData.postValue(Resource.error(e.message ?: "Failed to save phone"))
            }
        }
    }

    fun updatePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val u = FirebaseAuth.getInstance().currentUser ?: return@launch
                val email = u.email ?: run {
                    accountMessageLiveData.postValue(Resource.error("No email on this account"))
                    return@launch
                }
                val cred = EmailAuthProvider.getCredential(email, oldPassword)
                withContext(Dispatchers.IO) {
                    u.reauthenticate(cred).await()
                    u.updatePassword(newPassword).await()
                }
                accountMessageLiveData.postValue(Resource.success("Password updated"))
            } catch (e: Exception) {
                accountMessageLiveData.postValue(
                    Resource.error(e.message ?: "Could not update password"),
                )
            }
        }
    }

    fun savePreferredLanguage() {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUser()?.uid ?: return@launch
                withContext(Dispatchers.IO) {
                    userRepository.updatePreferences(uid, "English", null)
                }
                loadUser()
            } catch (_: Exception) {
            }
        }
    }

    fun savePreferredCurrency() {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUser()?.uid ?: return@launch
                withContext(Dispatchers.IO) {
                    userRepository.updatePreferences(uid, null, "INR (₹)")
                }
                loadUser()
            } catch (_: Exception) {
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUser()?.uid ?: return@launch
                withContext(Dispatchers.IO) {
                    tripRepository.removeAccountFromAllTrips(uid)
                    userRepository.deleteUserDocument(uid)
                }
                FirebaseAuth.getInstance().currentUser?.delete()?.await()
                accountMessageLiveData.postValue(Resource.success(MSG_ACCOUNT_DELETED))
            } catch (e: Exception) {
                accountMessageLiveData.postValue(
                    Resource.error(
                        e.message ?: "Could not delete account. Try signing in again, then retry.",
                    ),
                )
            }
        }
    }

    fun consumeAccountMessage() {
        accountMessageLiveData.value = null
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun getUserLiveData(): LiveData<Resource<User>> = userLiveData

    fun getUpdateProfileLiveData(): LiveData<Resource<String>> = updateProfileLiveData

    fun getUploadImageLiveData(): LiveData<Resource<String>> = uploadImageLiveData

    fun getProfileStatsLiveData(): LiveData<ProfileStats?> = profileStatsLiveData

    fun getAccountMessageLiveData(): LiveData<Resource<String>?> = accountMessageLiveData
}
