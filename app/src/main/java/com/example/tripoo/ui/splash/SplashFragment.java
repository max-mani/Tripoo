package com.example.tripoo.ui.splash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.tripoo.R;
import com.example.tripoo.databinding.FragmentSplashBinding;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.SplashViewModel;
import com.google.firebase.auth.FirebaseUser;

public class SplashFragment extends Fragment {
    private FragmentSplashBinding binding;
    private SplashViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);
        
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                // User is logged in, navigate to home
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Navigation.findNavController(view).navigate(R.id.action_splash_to_home);
                }, 1500);
            } else {
                // User is not logged in, navigate to auth
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Navigation.findNavController(view).navigate(R.id.action_splash_to_auth);
                }, 1500);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
