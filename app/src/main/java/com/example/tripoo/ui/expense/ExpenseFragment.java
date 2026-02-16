package com.example.tripoo.ui.expense;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Expense;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.FragmentExpenseBinding;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.ExpenseViewModel;
import com.example.tripoo.viewmodel.GroupsViewModel;
import com.example.tripoo.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

public class ExpenseFragment extends Fragment {
    private FragmentExpenseBinding binding;
    private ExpenseViewModel expenseViewModel;
    private HomeViewModel homeViewModel;
    private ExpenseAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentExpenseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        GroupsViewModel groupsViewModel = new ViewModelProvider(requireActivity()).get(GroupsViewModel.class);
        
        adapter = new ExpenseAdapter(new ArrayList<>(), new java.util.HashMap<>(), expense -> {
            // Handle expense click for editing
        });
        
        binding.rvExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExpenses.setAdapter(adapter);
        
        // Load members to map user IDs to names
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = user.getActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    groupsViewModel.loadTripAndMembers(tripId);
                }
            }
        });
        
        // Observe user data to get current user info
        final User[] currentUserRef = new User[1];
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), userResource -> {
            if (userResource.isSuccess() && userResource.getData() != null) {
                currentUserRef[0] = userResource.getData();
            }
        });
        
        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                java.util.Map<String, String> userIdToNameMap = new java.util.HashMap<>();
                for (com.example.tripoo.data.model.TripMember member : resource.getData()) {
                    userIdToNameMap.put(member.getUserId(), member.getName());
                }
                // Also add current user to the map if not already present
                if (currentUserRef[0] != null) {
                    User currentUser = currentUserRef[0];
                    if (!userIdToNameMap.containsKey(currentUser.getUserId()) && 
                        currentUser.getName() != null && !currentUser.getName().isEmpty()) {
                        userIdToNameMap.put(currentUser.getUserId(), currentUser.getName());
                    }
                }
                adapter.updateUserIdToNameMap(userIdToNameMap);
            }
        });
        
        binding.fabAddExpense.setOnClickListener(v -> {
            Resource<User> resource = homeViewModel.getUserLiveData().getValue();
            if (resource != null && resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = user.getActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    showAddExpenseBottomSheet(tripId, null);
                }
            }
        });
        
        // Load expenses when user has active trip
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = user.getActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    expenseViewModel.loadExpenses(tripId);
                }
            }
        });
        
        expenseViewModel.getExpensesLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                adapter.updateExpenses(resource.getData());
            }
        });
        
        expenseViewModel.getYouOweLiveData().observe(getViewLifecycleOwner(), amount -> {
            if (amount != null) {
                binding.tvYouOwe.setText("₹ " + String.format("%.0f", amount));
            }
        });
        
        expenseViewModel.getYouAreOwedLiveData().observe(getViewLifecycleOwner(), amount -> {
            if (amount != null) {
                binding.tvYouAreOwed.setText("₹ " + String.format("%.0f", amount));
            }
        });
    }

    private void showAddExpenseBottomSheet(String tripId, Expense expense) {
        AddExpenseBottomSheet bottomSheet = AddExpenseBottomSheet.newInstance(tripId, expense);
        bottomSheet.show(getParentFragmentManager(), "AddExpenseBottomSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
