package com.manikandan.tripoo.ui.splash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.manikandan.tripoo.R;
import com.manikandan.tripoo.databinding.FragmentSplashBinding;
import com.manikandan.tripoo.notifications.NotificationConstants;
import com.manikandan.tripoo.viewmodel.SplashNavigationState;
import com.manikandan.tripoo.viewmodel.SplashViewModelKt;

public class SplashFragment extends Fragment {
    private FragmentSplashBinding binding;
    private SplashViewModelKt viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSplashBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(SplashViewModelKt.class);

        viewModel.getNavigation().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state == SplashNavigationState.TO_AUTH) {
                Navigation.findNavController(view).navigate(R.id.action_splash_to_auth);
            } else if (state == SplashNavigationState.TO_DASHBOARD) {
                String openTripId = requireActivity().getIntent().getStringExtra(NotificationConstants.EXTRA_OPEN_TRIP_ID);
                if (openTripId != null && !openTripId.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("tripId", openTripId);
                    Navigation.findNavController(view).navigate(R.id.action_splash_to_home, args);
                    requireActivity().getIntent().removeExtra(NotificationConstants.EXTRA_OPEN_TRIP_ID);
                } else {
                    Navigation.findNavController(view).navigate(R.id.action_splash_to_dashboard);
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
