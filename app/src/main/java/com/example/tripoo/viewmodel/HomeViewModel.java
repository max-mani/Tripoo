package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.Trip;
import com.example.tripoo.data.model.User;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.TripRepository;
import com.example.tripoo.data.repository.UserRepository;
import com.example.tripoo.utils.FirebaseHelper;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.utils.TripCodeGenerator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

public class HomeViewModel extends AndroidViewModel {
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private TripRepository tripRepository;
    private MutableLiveData<Resource<User>> userLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<Trip>> tripLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> createTripLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> joinTripLiveData = new MutableLiveData<>();
    private MutableLiveData<Double> totalExpensesLiveData = new MutableLiveData<>();
    private ListenerRegistration tripListener;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
        tripRepository = new TripRepository();
        loadUser();
    }

    private void loadUser() {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            if (tripListener != null) {
                tripListener.remove();
                tripListener = null;
            }
            userLiveData.setValue(Resource.error("Logged out"));
            tripLiveData.setValue(Resource.error("Logged out"));
            return;
        }
        userRepository.getUser(firebaseUser.getUid())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        DocumentSnapshot doc = task.getResult();
                        User user = new User(
                                doc.getId(),
                                doc.getString("name"),
                                doc.getString("email"),
                                doc.getString("photoUrl"),
                                doc.getString("activeTripId")
                        );
                        userLiveData.setValue(Resource.success(user));

                        // Load trip if user has active trip
                        String activeTripId = user.getActiveTripId();
                        if (activeTripId != null && !activeTripId.isEmpty()) {
                            loadTrip(activeTripId);
                        }
                    } else {
                        userLiveData.setValue(Resource.error("Failed to load user"));
                    }
                });
    }

    /** Call when auth state may have changed (e.g. after login) to reload current user and trip. */
    public void refreshUser() {
        loadUser();
    }

    /** Call on sign-out so UI does not show previous user's data. */
    public void clearUser() {
        if (tripListener != null) {
            tripListener.remove();
            tripListener = null;
        }
        userLiveData.setValue(Resource.error("Logged out"));
        tripLiveData.setValue(Resource.error("Logged out"));
    }

    public void loadTrip(String tripId) {
        if (tripListener != null) {
            tripListener.remove();
        }
        
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
            } else {
                tripLiveData.setValue(Resource.error("Trip not found"));
            }
        });
    }

    public void createTrip(String place, Timestamp startDate, Timestamp endDate, double budget) {
        createTripLiveData.setValue(Resource.loading());
        
        TripCodeGenerator.generateUniqueTripCode(
                FirebaseHelper.getInstance().getFirestore(),
                new TripCodeGenerator.CodeGenerationCallback() {
                    @Override
                    public void onCodeGenerated(String tripCode) {
                        FirebaseUser firebaseUser = authRepository.getCurrentUser();
                        if (firebaseUser != null) {
                            Trip trip = new Trip(null, place, startDate, endDate, budget, tripCode, firebaseUser.getUid(), true);
                            tripRepository.createTrip(trip)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            String tripId = task.getResult().getId();
                                            // Get user name from User document
                                            userRepository.getUser(firebaseUser.getUid())
                                                    .addOnCompleteListener(userTask -> {
                                                        String userName = "User";
                                                        String userPhotoUrl = null;
                                                        if (userTask.isSuccessful() && userTask.getResult().exists()) {
                                                            DocumentSnapshot userDoc = userTask.getResult();
                                                            userName = userDoc.getString("name");
                                                            if (userName == null || userName.isEmpty()) {
                                                                userName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                                                            }
                                                            userPhotoUrl = userDoc.getString("photoUrl");
                                                        } else {
                                                            userName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                                                        }
                                                        if (userPhotoUrl == null && firebaseUser.getPhotoUrl() != null) {
                                                            userPhotoUrl = firebaseUser.getPhotoUrl().toString();
                                                        }
                                                        
                                                        // Add user as member
                                                        com.example.tripoo.data.model.TripMember member = 
                                                                new com.example.tripoo.data.model.TripMember(
                                                                        firebaseUser.getUid(),
                                                                        userName,
                                                                        firebaseUser.getEmail(),
                                                                        userPhotoUrl,
                                                                        true
                                                                );
                                                        tripRepository.addMemberToTrip(tripId, firebaseUser.getUid(), member)
                                                                .addOnCompleteListener(memberTask -> {
                                                                    if (memberTask.isSuccessful()) {
                                                                        // Update user's activeTripId
                                                                        userRepository.updateActiveTripId(firebaseUser.getUid(), tripId)
                                                                                .addOnCompleteListener(updateTask -> {
                                                                                    if (updateTask.isSuccessful()) {
                                                                                        createTripLiveData.setValue(Resource.success(tripCode));
                                                                                    } else {
                                                                                        createTripLiveData.setValue(Resource.error("Failed to update user"));
                                                                                    }
                                                                                });
                                                                    } else {
                                                                        createTripLiveData.setValue(Resource.error("Failed to add member"));
                                                                    }
                                                                });
                                                    });
                                        } else {
                                            createTripLiveData.setValue(Resource.error(
                                                    task.getException() != null ? task.getException().getMessage() : "Failed to create trip"));
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        createTripLiveData.setValue(Resource.error(error));
                    }
                });
    }

    public void joinTrip(String tripCode) {
        joinTripLiveData.setValue(Resource.loading());
        
        tripRepository.getTripByCode(tripCode)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        String tripId = doc.getId();
                        FirebaseUser firebaseUser = authRepository.getCurrentUser();
                        
                        if (firebaseUser != null) {
                            // Get user name from User document
                            userRepository.getUser(firebaseUser.getUid())
                                    .addOnCompleteListener(userTask -> {
                                        String userName = "User";
                                        String userPhotoUrl = null;
                                        if (userTask.isSuccessful() && userTask.getResult().exists()) {
                                            DocumentSnapshot userDoc = userTask.getResult();
                                            userName = userDoc.getString("name");
                                            if (userName == null || userName.isEmpty()) {
                                                userName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                                            }
                                            userPhotoUrl = userDoc.getString("photoUrl");
                                        } else {
                                            userName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                                        }
                                        if (userPhotoUrl == null && firebaseUser.getPhotoUrl() != null) {
                                            userPhotoUrl = firebaseUser.getPhotoUrl().toString();
                                        }
                                        
                                        // Add user as member
                                        com.example.tripoo.data.model.TripMember member = 
                                                new com.example.tripoo.data.model.TripMember(
                                                        firebaseUser.getUid(),
                                                        userName,
                                                        firebaseUser.getEmail(),
                                                        userPhotoUrl,
                                                        false
                                                );
                                        tripRepository.addMemberToTrip(tripId, firebaseUser.getUid(), member)
                                                .addOnCompleteListener(memberTask -> {
                                                    if (memberTask.isSuccessful()) {
                                                        // Update user's activeTripId
                                                        userRepository.updateActiveTripId(firebaseUser.getUid(), tripId)
                                                                .addOnCompleteListener(updateTask -> {
                                                                    if (updateTask.isSuccessful()) {
                                                                        joinTripLiveData.setValue(Resource.success(tripId));
                                                                    } else {
                                                                        joinTripLiveData.setValue(Resource.error("Failed to update user"));
                                                                    }
                                                                });
                                                    } else {
                                                        joinTripLiveData.setValue(Resource.error("Failed to join trip"));
                                                    }
                                                });
                                    });
                        }
                    } else {
                        String message;
                        if (!task.isSuccessful() && task.getException() != null) {
                            message = "Could not check trip code. Try again.";
                        } else {
                            message = "Invalid trip code";
                        }
                        joinTripLiveData.setValue(Resource.error(message));
                    }
                });
    }

    public void setTotalExpenses(double total) {
        totalExpensesLiveData.setValue(total);
    }

    public LiveData<Resource<User>> getUserLiveData() {
        return userLiveData;
    }

    public LiveData<Resource<Trip>> getTripLiveData() {
        return tripLiveData;
    }

    public LiveData<Resource<String>> getCreateTripLiveData() {
        return createTripLiveData;
    }

    public LiveData<Resource<String>> getJoinTripLiveData() {
        return joinTripLiveData;
    }

    public LiveData<Double> getTotalExpensesLiveData() {
        return totalExpensesLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (tripListener != null) {
            tripListener.remove();
        }
    }
}
