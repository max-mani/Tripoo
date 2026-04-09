package com.manikandan.tripoo.ui.groups;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.Trip;
import com.manikandan.tripoo.data.model.TripMember;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentGroupsBinding;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.GroupsViewModel;
import com.manikandan.tripoo.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment {
    private FragmentGroupsBinding binding;
    private GroupsViewModel groupsViewModel;
    private HomeViewModel homeViewModel;
    private MemberAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        groupsViewModel = new ViewModelProvider(this).get(GroupsViewModel.class);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        
        adapter = new MemberAdapter(new ArrayList<>());
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(adapter);
        binding.swipeRefreshGroups.setOnRefreshListener(() -> {
            String tripId = getCurrentTripId();
            if (tripId != null && !tripId.isEmpty()) {
                groupsViewModel.loadTripAndMembers(tripId);
            }
            binding.swipeRefreshGroups.postDelayed(() -> binding.swipeRefreshGroups.setRefreshing(false), 500);
        });
        
        binding.btnCopyCode.setOnClickListener(v -> {
            String tripCode = binding.tvTripCode.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Trip Code", tripCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Trip code copied!", Toast.LENGTH_SHORT).show();
        });

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        }
        if (binding.btnLeaveTrip != null) {
            binding.btnLeaveTrip.setOnClickListener(v ->
                    Toast.makeText(requireContext(), "Leave trip dialog", Toast.LENGTH_SHORT).show());
        }

        setActiveBottomNav("groups");
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
                String tripId = getCurrentTripId();
                if (tripId != null) {
                    Bundle args = new Bundle();
                    args.putString("tripId", tripId);
                    Navigation.findNavController(view).navigate(R.id.expensesFragment, args);
                }
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
                // already on Groups
            });
        }
        
        String tripIdForScreen = getCurrentTripId();
        if (tripIdForScreen != null && !tripIdForScreen.isEmpty()) {
            groupsViewModel.loadTripAndMembers(tripIdForScreen);
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
        
        groupsViewModel.getTripLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                Trip trip = resource.getData();
                binding.tvTripCode.setText(trip.getJoinCode());
            }
        });
        
        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                adapter.updateMembers(resource.getData());
            }
        });
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
