package com.manikandan.tripoo.ui.expense;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.Expense;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentExpenseBinding;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.ExpenseViewModel;
import com.manikandan.tripoo.viewmodel.GroupsViewModel;
import com.manikandan.tripoo.viewmodel.HomeViewModel;

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
        String initialTripId = getCurrentTripId();
        if (initialTripId != null && !initialTripId.isEmpty()) {
            groupsViewModel.loadTripAndMembers(initialTripId);
        } else {
            homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    String tripId = user.getLastActiveTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        groupsViewModel.loadTripAndMembers(tripId);
                    }
                }
            });
        }
        
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
                for (com.manikandan.tripoo.data.model.TripMember member : resource.getData()) {
                    userIdToNameMap.put(member.getUserId(), member.getName());
                }
                // Also add current user to the map if not already present
                if (currentUserRef[0] != null) {
                    User currentUser = currentUserRef[0];
                    if (!userIdToNameMap.containsKey(currentUser.getUid()) && 
                        currentUser.getName() != null && !currentUser.getName().isEmpty()) {
                        userIdToNameMap.put(currentUser.getUid(), currentUser.getName());
                    }
                }
                adapter.updateUserIdToNameMap(userIdToNameMap);
            }
        });
        
        binding.fabAddExpense.setOnClickListener(v -> {
            Resource<User> resource = homeViewModel.getUserLiveData().getValue();
            if (resource != null && resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = getCurrentTripId();
                if (tripId == null || tripId.isEmpty()) tripId = user.getLastActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    showAddExpenseBottomSheet(tripId, null);
                }
            }
        });

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }

        setActiveBottomNav("expenses");
        // Bottom nav
        if (binding.navHome != null) {
            binding.navHome.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.homeFragment, args);
                } else {
                    Navigation.findNavController(view).navigate(R.id.homeFragment);
                }
            });
        }
        if (binding.navExpenses != null) {
            binding.navExpenses.setOnClickListener(v -> {
                // already on Expenses
            });
        }
        if (binding.navTasks != null) {
            binding.navTasks.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.tasksFragment, args);
                }
            });
        }
        if (binding.navGroups != null) {
            binding.navGroups.setOnClickListener(v -> {
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.participantsFragment, args);
                }
            });
        }
        
        // Load expenses for current trip
        String tripIdForScreen = getCurrentTripId();
        if (tripIdForScreen != null && !tripIdForScreen.isEmpty()) {
            expenseViewModel.loadExpenses(tripIdForScreen);
        } else {
            homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    String tripId = user.getLastActiveTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        expenseViewModel.loadExpenses(tripId);
                    }
                }
            });
        }
        
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

    private String getCurrentTripId() {
        String argTripId = getArguments() != null ? getArguments().getString("tripId", "") : "";
        if (argTripId != null && !argTripId.isEmpty()) return argTripId;
        Resource<User> r = homeViewModel != null ? homeViewModel.getUserLiveData().getValue() : null;
        if (r != null && r.isSuccess() && r.getData() != null) {
            return r.getData().getLastActiveTripId();
        }
        return null;
    }

    private void setActiveBottomNav(String tab) {
        if (binding == null) return;
        int orange = ContextCompat.getColor(requireContext(), R.color.tripoo_orange);
        int grey = ContextCompat.getColor(requireContext(), R.color.tripoo_text_hint);

        binding.ivNavHome.setSelected("home".equals(tab));
        binding.ivNavExpenses.setSelected("expenses".equals(tab));
        binding.ivNavTasks.setSelected("tasks".equals(tab));
        binding.ivNavGroups.setSelected("groups".equals(tab));

        binding.tvNavHome.setTextColor("home".equals(tab) ? orange : grey);
        binding.tvNavExpenses.setTextColor("expenses".equals(tab) ? orange : grey);
        binding.tvNavTasks.setTextColor("tasks".equals(tab) ? orange : grey);
        binding.tvNavGroups.setTextColor("groups".equals(tab) ? orange : grey);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
