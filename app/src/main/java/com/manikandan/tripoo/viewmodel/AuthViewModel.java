package com.manikandan.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.utils.UserAvatarIdentity;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.UserRepository;
import com.manikandan.tripoo.utils.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;

    // Legacy result wrapper (still used by existing fragments)
    private final MutableLiveData<Resource<FirebaseUser>> authResultLiveData = new MutableLiveData<>();

    // Phase 2 style state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> navigateToDashboard = new MutableLiveData<>(false);

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        userRepository = new UserRepository();
    }

    // --- Phase 2 API: sign-in with simple flags ---

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearErrorMessage() {
        errorMessage.setValue(null);
    }

    public LiveData<Boolean> getNavigateToDashboard() {
        return navigateToDashboard;
    }

    public void clearNavigateToDashboard() {
        navigateToDashboard.setValue(false);
    }

    public void signIn(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            errorMessage.setValue("Please fill in all fields");
            return;
        }
        isLoading.setValue(true);
        authRepository.signInWithEmailPassword(email.trim(), password, (user, err) -> {
            isLoading.setValue(false);
            if (err == null && user != null) {
                navigateToDashboard.setValue(true);
            } else {
                errorMessage.setValue(err != null && err.getMessage() != null ? err.getMessage() : "Sign in failed");
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    public void sendPasswordReset(String email) {
        if (email == null || email.trim().isEmpty()) {
            errorMessage.setValue("Enter your email first");
            return;
        }
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(unused -> errorMessage.setValue("Reset email sent!"))
                .addOnFailureListener(e -> errorMessage.setValue(e.getMessage()));
    }

    // --- Legacy API used by existing flows (kept for compatibility) ---

    public void signInWithEmailPassword(String email, String password) {
        authResultLiveData.setValue(Resource.loading());
        authRepository.signInWithEmailPassword(email, password, (user, err) -> {
            if (err == null && user != null) {
                authResultLiveData.setValue(Resource.success(user));
            } else {
                authResultLiveData.setValue(Resource.error(err != null ? err.getMessage() : "Login failed"));
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    public void signUpWithEmailPassword(String name, String email, String password) {
        authResultLiveData.setValue(Resource.loading());
        authRepository.signUpWithEmailPassword(email, password, (firebaseUser, err) -> {
            if (err == null && firebaseUser != null) {
                String avLetter = UserAvatarIdentity.INSTANCE.letterFromName(name);
                String avColor = UserAvatarIdentity.INSTANCE.bgForSeed(firebaseUser.getUid());
                User user = new User(
                        firebaseUser.getUid(),
                        name,
                        email != null ? email : "",
                        null,
                        null,
                        null,
                        firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null,
                        Collections.emptyList(),
                        null,
                        avLetter,
                        avColor
                );
                userRepository.createOrUpdateUser(user, createErr -> {
                    if (createErr == null) {
                        authResultLiveData.setValue(Resource.success(firebaseUser));
                    } else {
                        authResultLiveData.setValue(Resource.error(createErr.getMessage() != null ? createErr.getMessage() : "Failed to create user"));
                    }
                    return kotlin.Unit.INSTANCE;
                });
            } else {
                authResultLiveData.setValue(Resource.error(err != null ? err.getMessage() : "Sign up failed"));
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    // Phase 2 sign-up API with validation and navigation flag
    public void signUp(String name, String email, String password, String confirmPassword) {
        signUp(name, email, password, confirmPassword, null);
    }

    // Phase 2 sign-up API with optional base64 avatar saved in photoUrl
    public void signUp(String name, String email, String password, String confirmPassword, String avatarBase64) {
        if (name == null || name.trim().isEmpty()) {
            errorMessage.setValue("Name is required");
            return;
        }
        if (email == null || email.trim().isEmpty()) {
            errorMessage.setValue("Email is required");
            return;
        }
        if (password == null || password.length() < 8) {
            errorMessage.setValue("Password must be at least 8 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            errorMessage.setValue("Passwords do not match");
            return;
        }

        isLoading.setValue(true);
        authRepository.signUpWithEmailPassword(email.trim(), password, (firebaseUser, err) -> {
            if (err == null && firebaseUser != null) {
                String photo = (avatarBase64 != null && !avatarBase64.trim().isEmpty())
                        ? avatarBase64.trim()
                        : (firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null);
                String avLetter = UserAvatarIdentity.INSTANCE.letterFromName(name.trim());
                String avColor = UserAvatarIdentity.INSTANCE.bgForSeed(firebaseUser.getUid());
                User user = new User(
                        firebaseUser.getUid(),
                        name.trim(),
                        email.trim(),
                        null,
                        null,
                        null,
                        photo,
                        Collections.emptyList(),
                        null,
                        avLetter,
                        avColor
                );
                userRepository.createOrUpdateUser(user, createErr -> {
                    isLoading.setValue(false);
                    if (createErr == null) {
                        navigateToDashboard.setValue(true);
                    } else {
                        errorMessage.setValue(createErr.getMessage() != null ? createErr.getMessage() : "Failed to save profile");
                    }
                    return kotlin.Unit.INSTANCE;
                });
            } else {
                isLoading.setValue(false);
                errorMessage.setValue(err != null && err.getMessage() != null ? err.getMessage() : "Sign up failed");
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    public LiveData<Resource<FirebaseUser>> getAuthResultLiveData() {
        return authResultLiveData;
    }
}
