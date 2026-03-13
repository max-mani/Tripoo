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
        
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = user.getLastActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    groupsViewModel.loadTripAndMembers(tripId);
                }
            }
        });
        
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
