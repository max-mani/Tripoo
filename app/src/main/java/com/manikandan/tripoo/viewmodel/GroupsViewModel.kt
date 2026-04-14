package com.manikandan.tripoo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.manikandan.tripoo.data.model.LeaveTripResult
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import com.manikandan.tripoo.utils.Resource
import com.manikandan.tripoo.utils.UserAvatarIdentity
import com.google.firebase.firestore.ListenerRegistration
import java.lang.Runnable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class GroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val tripRepository = TripRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val tripLiveData = MutableLiveData<Resource<Trip>>()
    private val membersLiveData = MutableLiveData<Resource<List<TripMember>>>()
    private val leaveTripResultData = MutableLiveData<LeaveTripResult?>()
    private val leaveTripErrorData = MutableLiveData<String?>()
    private val adminMutationErrorData = MutableLiveData<String?>()

    private var tripListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getTripLiveData(): LiveData<Resource<Trip>> = tripLiveData
    fun getMembersLiveData(): LiveData<Resource<List<TripMember>>> = membersLiveData
    fun getLeaveTripResult(): LiveData<LeaveTripResult?> = leaveTripResultData

    fun clearLeaveResult() {
        leaveTripResultData.value = null
    }
    fun getLeaveTripError(): LiveData<String?> = leaveTripErrorData
    fun getAdminMutationError(): LiveData<String?> = adminMutationErrorData

    fun loadTripAndMembers(tripId: String) {
        tripListener?.remove()
        membersListener?.remove()

        tripListener = tripRepository.listenToTrip(tripId) { trip, e ->
            if (e != null) {
                tripLiveData.value = Resource.error(e.message)
            } else if (trip != null) {
                tripLiveData.value = Resource.success(trip)
            }
        }

        membersLiveData.value = Resource.loading()
        membersListener = tripRepository.listenToTripMembers(tripId) { members, e ->
            if (e != null) {
                membersLiveData.postValue(Resource.error(e.message))
                return@listenToTripMembers
            }
            vmScope.launch {
                val merged = appendCurrentUserIfMissing(tripId, members)
                val organiserId = withContext(Dispatchers.IO) { tripRepository.getTrip(tripId)?.adminId }
                    ?: tripLiveData.value?.takeIf { it.isSuccess }?.getData()?.adminId
                membersLiveData.postValue(Resource.success(sortMembersForDisplay(merged, organiserId)))
            }
        }
    }

    /** Trip organiser first, then co-organisers ([TripMember.isAdmin] and not organiser), then everyone else. */
    private fun sortMembersForDisplay(members: List<TripMember>, organiserId: String?): List<TripMember> {
        val oid = organiserId?.takeIf { it.isNotEmpty() } ?: return members
        return members.sortedWith(
            compareBy<TripMember> { m ->
                when {
                    m.userId == oid -> 0
                    m.isAdmin -> 1
                    else -> 2
                }
            }.thenBy { m -> m.name.lowercase(Locale.getDefault()) }
        )
    }

    private suspend fun appendCurrentUserIfMissing(tripId: String, members: List<TripMember>): List<TripMember> {
        val currentUser = authRepository.getCurrentUser() ?: return members
        val uid = currentUser.uid
        if (members.any { it.userId == uid }) return members

        val user = withContext(Dispatchers.IO) { userRepository.getUser(uid) }
        val userName = user?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: currentUser.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "User"
        var userPhotoUrl = user?.photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (userPhotoUrl == null && currentUser.photoUrl != null) {
            userPhotoUrl = currentUser.photoUrl.toString()
        }
        val (letter, color) = if (userPhotoUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) { userRepository.ensureAvatarIdentityFields(uid) }
        } else {
            Pair(
                user?.avatarLetter?.trim()?.takeIf { it.isNotEmpty() }
                    ?: UserAvatarIdentity.letterFromName(userName),
                user?.avatarColorHex?.trim()?.takeIf { it.isNotEmpty() }
                    ?: UserAvatarIdentity.bgForSeed(uid)
            )
        }
        val trip = withContext(Dispatchers.IO) { tripRepository.getTrip(tripId) }
        val isOrganiser = trip?.adminId == uid
        return members + TripMember(
            userId = uid,
            name = userName,
            email = currentUser.email ?: "",
            photoUrl = userPhotoUrl,
            isAdmin = isOrganiser,
            avatarLetter = letter,
            avatarColorHex = color
        )
    }

    fun leaveTrip(tripId: String) {
        val uid = authRepository.getCurrentUser()?.uid ?: return
        leaveTripErrorData.value = null
        tripRepository.leaveTripAsync(tripId, uid) { result ->
            leaveTripResultData.postValue(result)
        }
    }

    fun transferAdminAndLeave(tripId: String, newAdminUserId: String) {
        val uid = authRepository.getCurrentUser()?.uid ?: return
        tripRepository.transferAdminAndLeave(tripId, uid, newAdminUserId) { err ->
            if (err != null) {
                leaveTripErrorData.postValue(err.message)
            } else {
                leaveTripResultData.postValue(LeaveTripResult.Success)
            }
        }
    }

    fun setMemberAdminRole(tripId: String, targetUserId: String, asAdmin: Boolean, tripCreatorId: String) {
        val acting = authRepository.getCurrentUser()?.uid ?: return
        tripRepository.setMemberAdminRole(
            tripId,
            targetUserId,
            asAdmin,
            acting,
            tripCreatorId
        ) { err ->
            if (err != null) {
                adminMutationErrorData.postValue(err.message ?: "Update failed")
            }
        }
    }

    fun removeMemberFromTrip(tripId: String, targetUserId: String, tripOrganiserId: String) {
        val acting = authRepository.getCurrentUser()?.uid ?: return
        tripRepository.removeMemberFromTrip(tripId, targetUserId, acting, tripOrganiserId) { err ->
            if (err != null) {
                adminMutationErrorData.postValue(err.message ?: "Remove failed")
            }
        }
    }

    fun removeTripFromCurrentUser(tripId: String, done: Runnable) {
        val uid = authRepository.getCurrentUser()?.uid ?: return done.run()
        userRepository.removeTripFromUser(uid, tripId) { _ -> done.run() }
    }

    override fun onCleared() {
        super.onCleared()
        vmScope.cancel()
        tripListener?.remove()
        membersListener?.remove()
    }
}
