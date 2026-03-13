package com.manikandan.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.TripRepository;
import com.manikandan.tripoo.data.repository.UserRepository;
import com.manikandan.tripoo.utils.Resource;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.List;

public class GroupsViewModel extends AndroidViewModel {
    private final TripRepository tripRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final MutableLiveData<Resource<Trip>> tripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<TripMember>>> membersLiveData = new MutableLiveData<>();
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

        tripListener = tripRepository.listenToTrip(tripId, (trip, e) -> {
            if (e != null) {
                tripLiveData.setValue(Resource.error(e.getMessage()));
                return Unit.INSTANCE;
            }
            if (trip != null) {
                tripLiveData.setValue(Resource.success(trip));
            }
            return Unit.INSTANCE;
        });

        membersLiveData.setValue(Resource.loading());
        membersListener = tripRepository.listenToTripMembers(tripId, (members, e) -> {
            if (e != null) {
                membersLiveData.setValue(Resource.error(e.getMessage()));
                return Unit.INSTANCE;
            }
            if (members != null) {
                FirebaseUser currentUser = authRepository.getCurrentUser();
                String currentUserId = currentUser != null ? currentUser.getUid() : null;
                if (currentUserId != null) {
                    userRepository.getUser(currentUserId, user -> {
                        String userName = "User";
                        String userPhotoUrl = null;
                        if (user != null) {
                            userName = user.getName() != null && !user.getName().isEmpty() ? user.getName() : (currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User");
                            userPhotoUrl = user.getPhotoUrl();
                        } else if (currentUser.getDisplayName() != null) {
                            userName = currentUser.getDisplayName();
                        }
                        if (userPhotoUrl == null && currentUser.getPhotoUrl() != null) {
                            userPhotoUrl = currentUser.getPhotoUrl().toString();
                        }
                        boolean currentUserInList = false;
                        List<TripMember> result = new ArrayList<>(members);
                        for (TripMember m : members) {
                            if (currentUserId.equals(m.getUserId())) {
                                currentUserInList = true;
                                break;
                            }
                        }
                        if (!currentUserInList) {
                            result.add(new TripMember(
                                    currentUserId,
                                    userName,
                                    currentUser.getEmail() != null ? currentUser.getEmail() : "",
                                    userPhotoUrl,
                                    false
                            ));
                        }
                        membersLiveData.setValue(Resource.success(result));
                        return Unit.INSTANCE;
                    });
                } else {
                    membersLiveData.setValue(Resource.success(members));
                }
            }
            return Unit.INSTANCE;
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
