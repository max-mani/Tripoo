package com.example.tripoo.data.repository;

import android.util.Log;
import com.example.tripoo.data.model.Trip;
import com.example.tripoo.utils.FirebaseHelper;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

public class TripRepository {
    private static final String TAG = "TripRepository";
    private FirebaseFirestore firestore;
    private static final String COLLECTION_TRIPS = "trips";
    private static final String COLLECTION_MEMBERS = "members";

    public TripRepository() {
        firestore = FirebaseHelper.getInstance().getFirestore();
    }

    public Task<DocumentReference> createTrip(Trip trip) {
        return firestore.collection(COLLECTION_TRIPS)
                .add(trip.toMap())
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Trip created with ID: " + documentReference.getId());
                });
    }

    public Task<QuerySnapshot> getTripByCode(String tripCode) {
        return firestore.collection(COLLECTION_TRIPS)
                .whereEqualTo("tripCode", tripCode)
                .whereEqualTo("isActive", true)
                .get();
    }

    public Task<DocumentSnapshot> getTrip(String tripId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .get();
    }

    public ListenerRegistration listenToTrip(String tripId, com.google.firebase.firestore.EventListener<DocumentSnapshot> listener) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .addSnapshotListener(listener);
    }

    public Task<Void> addMemberToTrip(String tripId, String userId, com.example.tripoo.data.model.TripMember member) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_MEMBERS)
                .document(userId)
                .set(member.toMap());
    }

    public Task<QuerySnapshot> getTripMembers(String tripId) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_MEMBERS)
                .get();
    }

    public ListenerRegistration listenToTripMembers(String tripId, com.google.firebase.firestore.EventListener<QuerySnapshot> listener) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .collection(COLLECTION_MEMBERS)
                .addSnapshotListener(listener);
    }

    public Task<Void> updateTrip(String tripId, Trip trip) {
        return firestore.collection(COLLECTION_TRIPS)
                .document(tripId)
                .update(trip.toMap());
    }
}
