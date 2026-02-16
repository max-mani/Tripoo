package com.example.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.User;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.UserRepository;
import com.example.tripoo.utils.Resource;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;

public class AuthViewModel extends AndroidViewModel {
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private MutableLiveData<Resource<FirebaseUser>> authResultLiveData = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
    }

    public void signInWithEmailPassword(String email, String password) {
        authResultLiveData.setValue(Resource.loading());
        authRepository.signInWithEmailPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        authResultLiveData.setValue(Resource.success(user));
                    } else {
                        authResultLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Login failed"));
                    }
                });
    }

    public void signUpWithEmailPassword(String name, String email, String password) {
        authResultLiveData.setValue(Resource.loading());
        authRepository.signUpWithEmailPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        if (firebaseUser != null) {
                            User user = new User(firebaseUser.getUid(), name, email, null, null);
                            userRepository.createUser(user)
                                    .addOnCompleteListener(createTask -> {
                                        if (createTask.isSuccessful()) {
                                            authResultLiveData.setValue(Resource.success(firebaseUser));
                                        } else {
                                            authResultLiveData.setValue(Resource.error(
                                                    createTask.getException() != null ? 
                                                            createTask.getException().getMessage() : "Failed to create user"));
                                        }
                                    });
                        }
                    } else {
                        authResultLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Sign up failed"));
                    }
                });
    }

    public void signInWithGoogle(GoogleSignInAccount account) {
        authResultLiveData.setValue(Resource.loading());
        authRepository.signInWithGoogle(account)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        if (firebaseUser != null) {
                            // Check if user exists, if not create
                            userRepository.getUser(firebaseUser.getUid())
                                    .addOnCompleteListener(getTask -> {
                                        if (getTask.isSuccessful() && getTask.getResult().exists()) {
                                            authResultLiveData.setValue(Resource.success(firebaseUser));
                                        } else {
                                            // Create new user
                                            User user = new User(
                                                    firebaseUser.getUid(),
                                                    firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User",
                                                    firebaseUser.getEmail(),
                                                    firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null,
                                                    null
                                            );
                                            userRepository.createUser(user)
                                                    .addOnCompleteListener(createTask -> {
                                                        if (createTask.isSuccessful()) {
                                                            authResultLiveData.setValue(Resource.success(firebaseUser));
                                                        } else {
                                                            authResultLiveData.setValue(Resource.error(
                                                                    createTask.getException() != null ? 
                                                                            createTask.getException().getMessage() : "Failed to create user"));
                                                        }
                                                    });
                                        }
                                    });
                        }
                    } else {
                        authResultLiveData.setValue(Resource.error(
                                task.getException() != null ? task.getException().getMessage() : "Google sign in failed"));
                    }
                });
    }

    public LiveData<Resource<FirebaseUser>> getAuthResultLiveData() {
        return authResultLiveData;
    }
}
