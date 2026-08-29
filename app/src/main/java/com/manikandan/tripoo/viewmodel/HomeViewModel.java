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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import kotlin.Unit;

public class HomeViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final MutableLiveData<Resource<User>> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<Trip>> tripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> createTripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> joinTripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> totalExpensesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> deleteTripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> updateTripLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> canManageTripLiveData = new MutableLiveData<>(false);
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
            canManageTripLiveData.setValue(false);
            userLiveData.setValue(Resource.error("Logged out"));
            tripLiveData.setValue(Resource.error("Logged out"));
            return;
        }
        userRepository.getUser(firebaseUser.getUid(), user -> {
            if (user != null) {
                userLiveData.setValue(Resource.success(user));
            } else {
                userLiveData.setValue(Resource.error("Failed to load user"));
            }
            return Unit.INSTANCE;
        });
    }

    public void refreshUser() {
        loadUser();
    }

    public void clearUser() {
        if (tripListener != null) {
            tripListener.remove();
            tripListener = null;
        }
        canManageTripLiveData.setValue(false);
        userLiveData.setValue(Resource.error("Logged out"));
        tripLiveData.setValue(Resource.error("Logged out"));
    }

    public void loadTrip(String tripId) {
        if (tripListener != null) {
            tripListener.remove();
        }
        canManageTripLiveData.setValue(false);
        tripListener = tripRepository.listenToTrip(tripId, (trip, e) -> {
            if (e != null) {
                tripLiveData.setValue(Resource.error(e.getMessage()));
                canManageTripLiveData.postValue(false);
                return Unit.INSTANCE;
            }
            if (trip != null) {
                tripLiveData.setValue(Resource.success(trip));
                FirebaseUser u = authRepository.getCurrentUser();
                if (u != null) {
                    tripRepository.canUserManageTripAsLeader(tripId, u.getUid(), can -> {
                        canManageTripLiveData.postValue(can);
                        return Unit.INSTANCE;
                    });
                } else {
                    canManageTripLiveData.postValue(false);
                }
            } else {
                tripLiveData.setValue(Resource.error("Trip not found"));
                canManageTripLiveData.postValue(false);
            }
            return Unit.INSTANCE;
        });
    }

    private static long timestampToMillis(Timestamp t) {
        if (t == null) return 0L;
        return t.getSeconds() * 1000L + t.getNanoseconds() / 1_000_000;
    }

    public void createTrip(String name, String destination, String description, Timestamp startDate, Timestamp endDate, double budget) {
        createTrip(name, destination, description, startDate, endDate, budget, Trip.TYPE_TRIP);
    }

    public void createTrip(String name, String destination, String description, Timestamp startDate, Timestamp endDate, double budget, String type) {
        createTripLiveData.setValue(Resource.loading());
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            createTripLiveData.setValue(Resource.error("Not logged in"));
            return;
        }
        String gatheringType = (type != null && !type.isEmpty()) ? type : Trip.TYPE_TRIP;
        Trip trip = new Trip(
                "",
                name != null ? name.trim() : "",
                destination != null ? destination.trim() : "",
                description != null ? description.trim() : "",
                timestampToMillis(startDate),
                timestampToMillis(endDate),
                budget,
                firebaseUser.getUid(),
                "",
                java.util.Collections.emptyList(),
                "upcoming",
                gatheringType
        );
        userRepository.getUser(firebaseUser.getUid(), user -> {
            String userName = "User";
            String userPhotoUrl = null;
            if (user != null) {
                userName = user.getName() != null && !user.getName().isEmpty() ? user.getName() : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User");
                userPhotoUrl = user.getPhotoUrl();
            } else if (firebaseUser.getDisplayName() != null) {
                userName = firebaseUser.getDisplayName();
            }
            if (userPhotoUrl == null && firebaseUser.getPhotoUrl() != null) {
                userPhotoUrl = firebaseUser.getPhotoUrl().toString();
            }
            TripMember member = new TripMember(
                    firebaseUser.getUid(),
                    userName,
                    firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "",
                    userPhotoUrl,
                    true,
                    null,
                    null
            );
            tripRepository.createTrip(trip, member, tripId -> {
                if (tripId != null) {
                    userRepository.addTripToUser(firebaseUser.getUid(), tripId, err -> {
                        if (err == null) {
                            createTripLiveData.setValue(Resource.success(tripId));
                        } else {
                            createTripLiveData.setValue(Resource.error("Failed to update user"));
                        }
                        return Unit.INSTANCE;
                    });
                } else {
                    createTripLiveData.setValue(Resource.error("Failed to create trip"));
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    public void joinTrip(String tripCode) {
        joinTripLiveData.setValue(Resource.loading());
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            joinTripLiveData.setValue(Resource.error("Not logged in"));
            return;
        }
        userRepository.getUser(firebaseUser.getUid(), user -> {
            String userName = "User";
            String userPhotoUrl = null;
            if (user != null) {
                userName = user.getName() != null && !user.getName().isEmpty() ? user.getName() : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User");
                userPhotoUrl = user.getPhotoUrl();
            } else if (firebaseUser.getDisplayName() != null) {
                userName = firebaseUser.getDisplayName();
            }
            if (userPhotoUrl == null && firebaseUser.getPhotoUrl() != null) {
                userPhotoUrl = firebaseUser.getPhotoUrl().toString();
            }
            TripMember member = new TripMember(
                    firebaseUser.getUid(),
                    userName,
                    firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "",
                    userPhotoUrl,
                    false,
                    null,
                    null
            );
            tripRepository.joinTrip(tripCode, member, tripId -> {
                if (tripId != null) {
                    userRepository.addTripToUser(firebaseUser.getUid(), tripId, err -> {
                        if (err == null) {
                            joinTripLiveData.setValue(Resource.success(tripId));
                        } else {
                            joinTripLiveData.setValue(Resource.error("Failed to update user"));
                        }
                        return Unit.INSTANCE;
                    });
                } else {
                    joinTripLiveData.setValue(Resource.error("Invalid trip code"));
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
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

    public LiveData<Resource<Void>> getDeleteTripLiveData() {
        return deleteTripLiveData;
    }

    /** Clears delete result so other screens do not react to a stale success/error. */
    public void acknowledgeDeleteTripResult() {
        deleteTripLiveData.setValue(null);
    }

    public LiveData<Resource<Void>> getUpdateTripLiveData() {
        return updateTripLiveData;
    }

    public LiveData<Boolean> getCanManageTripLiveData() {
        return canManageTripLiveData;
    }

    public void updateTrip(String tripId, String name, String destination, String description, Timestamp startDate, Timestamp endDate, double budget) {
        updateTripLiveData.setValue(Resource.loading());
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            updateTripLiveData.setValue(Resource.error("Not logged in"));
            return;
        }
        Resource<Trip> tripRes = tripLiveData.getValue();
        Trip trip = (tripRes != null && tripRes.isSuccess()) ? tripRes.getData() : null;
        if (trip == null) {
            updateTripLiveData.setValue(Resource.error("Trip not loaded"));
            return;
        }
        if (trip.getId() == null || !trip.getId().equals(tripId)) {
            updateTripLiveData.setValue(Resource.error("Trip mismatch"));
            return;
        }

        String uid = firebaseUser.getUid();
        tripRepository.canUserManageTripAsLeader(tripId, uid, can -> {
            if (!can) {
                updateTripLiveData.postValue(Resource.error("Only the organiser or a co-organiser can edit this trip"));
                return Unit.INSTANCE;
            }
            long startMs = timestampToMillis(startDate);
            long endMs = timestampToMillis(endDate);
            tripRepository.updateTripDetails(
                    tripId,
                    name != null ? name.trim() : "",
                    destination != null ? destination.trim() : "",
                    description != null ? description.trim() : "",
                    startMs,
                    endMs,
                    budget,
                    err -> {
                        if (err != null) {
                            updateTripLiveData.postValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Update failed"));
                        } else {
                            updateTripLiveData.postValue(Resource.success(null));
                        }
                        return Unit.INSTANCE;
                    }
            );
            return Unit.INSTANCE;
        });
    }

    public void deleteTrip(String tripId) {
        deleteTripLiveData.setValue(Resource.loading());
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            deleteTripLiveData.setValue(Resource.error("Not logged in"));
            return;
        }
        Resource<Trip> tripRes = tripLiveData.getValue();
        Trip trip = (tripRes != null && tripRes.isSuccess()) ? tripRes.getData() : null;
        if (trip == null) {
            deleteTripLiveData.setValue(Resource.error("Trip not loaded"));
            return;
        }

        String organiserUid = trip.getAdminId();
        if (organiserUid == null || organiserUid.isEmpty()) {
            deleteTripLiveData.setValue(Resource.error("Trip not loaded"));
            return;
        }

        String uid = firebaseUser.getUid();
        if (!uid.equals(organiserUid)) {
            deleteTripLiveData.setValue(Resource.error("Only the trip organiser can delete this trip"));
            return;
        }
        tripRepository.deleteTripAsAdmin(tripId, organiserUid, err -> {
            if (err != null) {
                deleteTripLiveData.postValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Delete failed"));
                return Unit.INSTANCE;
            }
            userRepository.removeTripFromUser(uid, tripId, ignored -> {
                deleteTripLiveData.postValue(Resource.success(null));
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (tripListener != null) {
            tripListener.remove();
        }
    }
}
