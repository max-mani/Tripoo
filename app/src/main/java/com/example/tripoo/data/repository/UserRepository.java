package com.example.tripoo.data.repository;

import android.util.Log;
import com.example.tripoo.data.model.User;
import com.example.tripoo.utils.FirebaseHelper;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private FirebaseFirestore firestore;
    private static final String COLLECTION_USERS = "users";

    public UserRepository() {
        firestore = FirebaseHelper.getInstance().getFirestore();
    }

    public Task<Void> createUser(User user) {
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUserId())
                .set(user.toMap());
    }

    public Task<DocumentSnapshot> getUser(String userId) {
        return firestore.collection(COLLECTION_USERS)
                .document(userId)
                .get();
    }

    public Task<Void> updateUser(User user) {
        return firestore.collection(COLLECTION_USERS)
                .document(user.getUserId())
                .update(user.toMap());
    }

    public Task<Void> updateActiveTripId(String userId, String tripId) {
        return firestore.collection(COLLECTION_USERS)
                .document(userId)
                .update("activeTripId", tripId);
    }

    public Task<Void> updateProfile(String userId, String name, String photoUrl) {
        return firestore.collection(COLLECTION_USERS)
                .document(userId)
                .update("name", name, "photoUrl", photoUrl);
    }
}
