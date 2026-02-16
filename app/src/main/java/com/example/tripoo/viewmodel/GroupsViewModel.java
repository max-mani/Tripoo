package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.Trip;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.TripRepository;
import com.example.tripoo.utils.Resource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class GroupsViewModel extends AndroidViewModel {
    private TripRepository tripRepository;
    private AuthRepository authRepository;
    private MutableLiveData<Resource<Trip>> tripLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<List<TripMember>>> membersLiveData = new MutableLiveData<>();
    private ListenerRegistration tripListener;
    private ListenerRegistration membersListener;
    private String currentTripId;

    public GroupsViewModel(@NonNull Application application) {
        super(application);
        tripRepository = new TripRepository();
        authRepository = new AuthRepository();
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
                List<TripMember> members = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    TripMember member = doc.toObject(TripMember.class);
                    if (member != null) {
                        member.setUserId(doc.getId());
                        members.add(member);
                    }
                }
                membersLiveData.setValue(Resource.success(members));
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
