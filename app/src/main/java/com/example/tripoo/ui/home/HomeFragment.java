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

        if (binding.qaExpenses != null) {
            binding.qaExpenses.setOnClickListener(v -> {
                String tripId = getTripIdFromUser();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        if (binding.qaTasks != null) {
            binding.qaTasks.setOnClickListener(v -> {
                String tripId = getTripIdFromUser();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_tasks, args);
                }
            });
        }
        if (binding.qaGroups != null) {
            binding.qaGroups.setOnClickListener(v -> {
                String tripId = getTripIdFromUser();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_participants, args);
                }
            });
        }
        if (binding.btnBudgetDetails != null) {
            binding.btnBudgetDetails.setOnClickListener(v -> {
                String tripId = getTripIdFromUser();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String activeTripId = user.getLastActiveTripId();
                
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
                binding.tvTripPlace.setText(trip.getDestination());
                
                String dates = DateFormatter.formatDate(trip.getStartDate()) + " - " + 
                              DateFormatter.formatDate(trip.getEndDate());
                binding.tvTripDates.setText(dates);
                
                // Check if trip has started
                long now = System.currentTimeMillis();
                if (trip.getStartDate() > now) {
                    // Trip hasn't started yet - show countdown (Days | Hours | Minutes | Seconds)
                    binding.tripCountdownView.setVisibility(View.VISIBLE);
                    binding.llBudgetProgress.setVisibility(View.GONE);
                    binding.tripCountdownView.bindTrip(trip);
                } else {
                    // Trip has started - show budget progress
                    binding.tripCountdownView.stop();
                    binding.tripCountdownView.setVisibility(View.GONE);
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
                Navigation.findNavController(view).navigate(R.id.action_create_to_home);
            }
        });
        
        viewModel.getJoinTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess()) {
                Navigation.findNavController(view).navigate(R.id.action_join_to_home);
            }
        });
    }

    private String getTripIdFromUser() {
        Resource<User> r = viewModel.getUserLiveData().getValue();
        if (r != null && r.isSuccess() && r.getData() != null) {
            return r.getData().getLastActiveTripId();
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
