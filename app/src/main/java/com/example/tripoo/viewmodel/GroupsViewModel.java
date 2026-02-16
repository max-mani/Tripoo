package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.Trip;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.data.model.User;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.TripRepository;
import com.example.tripoo.data.repository.UserRepository;
import com.example.tripoo.utils.Resource;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class GroupsViewModel extends AndroidViewModel {
    private TripRepository tripRepository;
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private MutableLiveData<Resource<Trip>> tripLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<List<TripMember>>> membersLiveData = new MutableLiveData<>();
    private ListenerRegistration tripListener;
    private ListenerRegistration membersListener;
    private String currentTripId;

    public GroupsViewModel(@NonNull Application application) {
        super(application);
        tripRepository = new TripRepository();
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
    }

    public void loadTripAndMembers(String tripId) {
        currentTripId = tripId;
        
        // Load trip
        tripListener = tripRepository.listenToTrip(tripId, (snapshot, e) -> {
            if (e != null) {
                tripLiveData.setValue(Resource.error(e.getMessage()));
                return;
            }
            
            if (snapshot != null && snapshot.exists()) {
                Trip trip = snapshot.toObject(Trip.class);
                if (trip != null) {
                    trip.setTripId(snapshot.getId());
                    tripLiveData.setValue(Resource.success(trip));
                }
            }
        });
        
        // Load members
        membersLiveData.setValue(Resource.loading());
        membersListener = tripRepository.listenToTripMembers(tripId, (snapshot, e) -> {
            if (e != null) {
                membersLiveData.setValue(Resource.error(e.getMessage()));
                return;
            }
            
            if (snapshot != null) {
                final List<TripMember> members = new ArrayList<>();
                FirebaseUser currentUser = authRepository.getCurrentUser();
                final String currentUserId = currentUser != null ? currentUser.getUid() : null;
                final boolean[] currentUserInList = {false};
                final TripMember[] currentUserMember = {null};
                
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    TripMember member = doc.toObject(TripMember.class);
                    if (member != null) {
                        member.setUserId(doc.getId());
                        members.add(member);
                        // Check if current user is in the list
                        if (currentUserId != null && doc.getId().equals(currentUserId)) {
                            currentUserInList[0] = true;
                            currentUserMember[0] = member;
                        }
                    }
                }
                
                // Always update current user's name from User document and ensure they're in the list
                if (currentUserId != null) {
                    userRepository.getUser(currentUserId)
                            .addOnCompleteListener(userTask -> {
                                String userName = "User";
                                String userEmail = currentUser.getEmail();
                                String userPhotoUrl = null;
                                
                                if (userTask.isSuccessful() && userTask.getResult().exists()) {
                                    DocumentSnapshot userDoc = userTask.getResult();
                                    userName = userDoc.getString("name");
                                    if (userName == null || userName.isEmpty()) {
                                        userName = currentUser.getDisplayName() != null ? 
                                                currentUser.getDisplayName() : "User";
                                    }
                                    userPhotoUrl = userDoc.getString("photoUrl");
                                } else {
                                    userName = currentUser.getDisplayName() != null ? 
                                            currentUser.getDisplayName() : "User";
                                }
                                
                                if (userPhotoUrl == null && currentUser.getPhotoUrl() != null) {
                                    userPhotoUrl = currentUser.getPhotoUrl().toString();
                                }
                                
                                // Update existing member or add new one
                                if (currentUserInList[0] && currentUserMember[0] != null) {
                                    // Update the existing member's name and photo
                                    currentUserMember[0].setName(userName);
                                    if (userPhotoUrl != null) {
                                        currentUserMember[0].setPhotoUrl(userPhotoUrl);
                                    }
                                } else {
                                    // Add current user to members list
                                    TripMember newMember = new TripMember(
                                            currentUserId,
                                            userName,
                                            userEmail,
                                            userPhotoUrl,
                                            false
                                    );
                                    members.add(newMember);
                                }
                                
                                membersLiveData.setValue(Resource.success(members));
                            });
                } else {
                    membersLiveData.setValue(Resource.success(members));
                }
            }
        });
    }

    public LiveData<Resource<Trip>> getTripLiveData() {
        return tripLiveData;
    }

    public LiveData<Resource<List<TripMember>>> getMembersLiveData() {
        return membersLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (tripListener != null) {
            tripListener.remove();
        }
        if (membersListener != null) {
            membersListener.remove();
        }
    }
}
