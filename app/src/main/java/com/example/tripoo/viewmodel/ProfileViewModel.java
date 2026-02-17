package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.User;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;
import com.example.tripoo.utils.Resource;
import com.google.firebase.firestore.DocumentSnapshot;

public class ProfileViewModel extends AndroidViewModel {
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private MutableLiveData<Resource<User>> userLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> updateProfileLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> uploadImageLiveData = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
        loadUser();
    }

    private void loadUser() {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser != null) {
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
                        } else {
                            userLiveData.setValue(Resource.error("Failed to load user"));
                        }
                    });
        }
    }

    /** Call when auth state may have changed (e.g. after login) to reload current user. */
    public void refreshUser() {
        loadUser();
    }

    /** Call on sign-out so UI does not show previous user's data. */
    public void clearUser() {
        userLiveData.setValue(Resource.error("Logged out"));
    }

    public void updateProfile(String name, String photoUrl) {
        updateProfileLiveData.setValue(Resource.loading());
        
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser != null) {
            userRepository.updateProfile(firebaseUser.getUid(), name, photoUrl)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            updateProfileLiveData.setValue(Resource.success("Profile updated successfully"));
                            loadUser(); // Reload user data
                        } else {
                            updateProfileLiveData.setValue(Resource.error(
                                    task.getException() != null ? task.getException().getMessage() : "Failed to update profile"));
                        }
                    });
        }
    }

    public void uploadProfileImage(String base64Image) {
        uploadImageLiveData.setValue(Resource.loading());
        
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            uploadImageLiveData.setValue(Resource.error("User not logged in"));
            return;
        }
        
        if (base64Image == null || base64Image.isEmpty()) {
            uploadImageLiveData.setValue(Resource.error("Image data is empty"));
            return;
        }
        
        // Store base64 string directly - it will be saved to Firestore
        uploadImageLiveData.setValue(Resource.success(base64Image));
    }

    public LiveData<Resource<User>> getUserLiveData() {
        return userLiveData;
    }

    public LiveData<Resource<String>> getUpdateProfileLiveData() {
        return updateProfileLiveData;
    }

    public LiveData<Resource<String>> getUploadImageLiveData() {
        return uploadImageLiveData;
    }

    /**
     * Save only the photo (base64) to Firestore, keeping existing name.
     * Used when user selects a new photo so we don't require them to tap Update Profile.
     */
    public void savePhotoToFirestore(String base64Photo) {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            return;
        }
        userRepository.getUser(firebaseUser.getUid())
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String currentName = doc.getString("name");
                        if (currentName == null || currentName.isEmpty()) {
                            currentName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                        }
                        userRepository.updateProfile(firebaseUser.getUid(), currentName, base64Photo)
                                .addOnSuccessListener(aVoid -> {
                                    loadUser();
                                });
                    }
                });
    }

    public void signOut() {
        authRepository.signOut();
    }
}
