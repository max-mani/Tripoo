package com.manikandan.tripoo.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentHomeBinding;
import com.manikandan.tripoo.utils.DateFormatter;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;

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

        // If tripId was passed from create/join, load that trip
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) {
            viewModel.loadTrip(argTripId);
            binding.llNoTrip.setVisibility(View.GONE);
            binding.llInTrip.setVisibility(View.VISIBLE);
        }
        
        binding.btnCreateTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_create_trip));
        binding.btnJoinTrip.setOnClickListener(v -> 
                Navigation.findNavController(view).navigate(R.id.action_home_to_join_trip));

        if (binding.qaExpenses != null) {
            binding.qaExpenses.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        if (binding.qaTasks != null) {
            binding.qaTasks.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_tasks, args);
                }
            });
        }
        if (binding.qaGroups != null) {
            binding.qaGroups.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_participants, args);
                }
            });
        }
        if (binding.btnBudgetDetails != null) {
            binding.btnBudgetDetails.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.action_home_to_expenses, args);
                }
            });
        }
        
        final boolean hasArgTripId = argTripId != null && !argTripId.isEmpty();
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (hasArgTripId) return; // tripId from create/join takes precedence
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
        
        // Create/Join navigation is handled by CreateTripFragment and JoinTripFragment
    }

    private String getCurrentTripId() {
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) return argTripId;
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
