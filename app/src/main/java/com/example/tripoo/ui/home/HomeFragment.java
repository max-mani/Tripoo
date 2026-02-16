package com.example.tripoo.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Trip;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.FragmentHomeBinding;
import com.example.tripoo.utils.DateFormatter;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.google.firebase.Timestamp;

import java.util.Calendar;
import java.util.Date;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        
        binding.btnCreateTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_create_trip));
        binding.btnJoinTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_join_trip));
        
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String activeTripId = user.getActiveTripId();
                
                if (activeTripId != null && !activeTripId.isEmpty()) {
                    viewModel.loadTrip(activeTripId);
                    binding.llNoTrip.setVisibility(View.GONE);
                    binding.llInTrip.setVisibility(View.VISIBLE);
                } else {
                    binding.llNoTrip.setVisibility(View.VISIBLE);
                    binding.llInTrip.setVisibility(View.GONE);
                }
            }
        });
        
        viewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Trip trip = resource.getData();
                binding.tvTripPlace.setText(trip.getPlace());
                
                String dates = DateFormatter.formatDate(trip.getStartDate()) + " - " + 
                              DateFormatter.formatDate(trip.getEndDate());
                binding.tvTripDates.setText(dates);
                
                // Check if trip has started
                Timestamp now = Timestamp.now();
                if (trip.getStartDate() != null && trip.getStartDate().compareTo(now) > 0) {
                    // Trip hasn't started yet - show countdown
                    binding.tvCountdown.setVisibility(View.VISIBLE);
                    binding.llBudgetProgress.setVisibility(View.GONE);
                    
                    long daysUntil = (trip.getStartDate().getSeconds() - now.getSeconds()) / (24 * 60 * 60);
                    binding.tvCountdown.setText(daysUntil + " days until trip starts");
                } else {
                    // Trip has started - show budget progress
                    binding.tvCountdown.setVisibility(View.GONE);
                    binding.llBudgetProgress.setVisibility(View.VISIBLE);
                    
                    viewModel.getTotalExpensesLiveData().observe(getViewLifecycleOwner(), total -> {
                        if (total != null) {
                            double spent = total;
                            double budget = trip.getBudget();
                            binding.tvBudgetSpent.setText("₹ " + String.format("%.0f", spent) + " / ₹ " + String.format("%.0f", budget));
                            int progress = (int) ((spent / budget) * 100);
                            binding.progressBudget.setProgress(Math.min(progress, 100));
                        }
                    });
                }
            }
        });
        
        viewModel.getCreateTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                // Show trip code dialog or navigate
                Navigation.findNavController(view).navigate(R.id.action_create_trip_to_home);
            }
        });
        
        viewModel.getJoinTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                Navigation.findNavController(view).navigate(R.id.action_join_trip_to_home);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
