package com.manikandan.tripoo.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.utils.Resource;
import com.google.firebase.auth.FirebaseUser;

public class SplashViewModel extends AndroidViewModel {
    private AuthRepository authRepository;
    private MutableLiveData<Resource<FirebaseUser>> userLiveData = new MutableLiveData<>();

    public SplashViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository();
        checkAuthState();
    }

    private void checkAuthState() {
        FirebaseUser user = authRepository.getCurrentUser();
        if (user != null) {
            userLiveData.setValue(Resource.success(user));
        } else {
            userLiveData.setValue(Resource.error("No user logged in"));
        }
    }

    public LiveData<Resource<FirebaseUser>> getUserLiveData() {
        return userLiveData;
    }
}
