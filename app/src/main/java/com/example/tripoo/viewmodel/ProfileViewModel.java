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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.UUID;

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

    public void uploadProfileImage(byte[] imageData) {
        uploadImageLiveData.setValue(Resource.loading());
        
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser == null) {
            uploadImageLiveData.setValue(Resource.error("User not logged in"));
            return;
        }
        
        String fileName = "profile_images/" + firebaseUser.getUid() + "_" + UUID.randomUUID().toString() + ".jpg";
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child(fileName);
        
        storageRef.putBytes(imageData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        storageRef.getDownloadUrl()
                                .addOnCompleteListener(urlTask -> {
                                    if (urlTask.isSuccessful()) {
                                        String downloadUrl = urlTask.getResult().toString();
                                        uploadImageLiveData.setValue(Resource.success(downloadUrl));
                                    } else {
                                        uploadImageLiveData.setValue(Resource.error("Failed to get download URL"));
                                    }
                                });
                    } else {
                        uploadImageLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Failed to upload image"));
                    }
                });
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

    public void signOut() {
        authRepository.signOut();
    }
}
