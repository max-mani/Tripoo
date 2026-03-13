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
import kotlin.Unit;

public class ProfileViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final MutableLiveData<Resource<User>> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> updateProfileLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> uploadImageLiveData = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
        loadUser();
    }

    private void loadUser() {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser != null) {
            userRepository.getUser(firebaseUser.getUid(), user -> {
                if (user != null) {
                    userLiveData.setValue(Resource.success(user));
                } else {
                    userLiveData.setValue(Resource.error("Failed to load user"));
                }
                return Unit.INSTANCE;
            });
        }
    }

    public void refreshUser() {
        loadUser();
    }

    public void clearUser() {
        userLiveData.setValue(Resource.error("Logged out"));
    }

    public void updateProfile(String name, String photoUrl) {
        updateProfileLiveData.setValue(Resource.loading());
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser != null) {
            userRepository.updateProfile(firebaseUser.getUid(), name, photoUrl, err -> {
                if (err == null) {
                    updateProfileLiveData.setValue(Resource.success("Profile updated successfully"));
                    loadUser();
                } else {
                    updateProfileLiveData.setValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Failed to update profile"));
                }
                return Unit.INSTANCE;
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

    public void savePhotoToFirestore(String base64Photo) {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) return;
        userRepository.getUser(firebaseUser.getUid(), user -> {
            if (user != null) {
                String currentName = user.getName() != null && !user.getName().isEmpty()
                        ? user.getName() : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User");
                userRepository.updateProfile(firebaseUser.getUid(), currentName, base64Photo, err -> {
                    if (err == null) loadUser();
                    return Unit.INSTANCE;
                });
            }
            return Unit.INSTANCE;
        });
    }

    public void signOut() {
        authRepository.signOut();
    }
}
