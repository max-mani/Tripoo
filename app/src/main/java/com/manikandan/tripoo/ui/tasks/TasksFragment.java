package com.manikandan.tripoo.ui.tasks;

import android.os.Bundle;
import android.widget.Toast;
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
import com.manikandan.tripoo.data.model.Task;
import com.manikandan.tripoo.data.model.User;
import com.manikandan.tripoo.databinding.FragmentTasksBinding;
import com.manikandan.tripoo.utils.Resource;
import com.manikandan.tripoo.viewmodel.HomeViewModel;
import com.manikandan.tripoo.viewmodel.TaskViewModel;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TasksFragment extends Fragment {
    private FragmentTasksBinding binding;
    private TaskViewModel viewModel;
    private HomeViewModel homeViewModel;
    private TaskAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        
        adapter = new TaskAdapter(new HashMap<>(), task -> {
            // Handle task click
        });
        
        binding.rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTasks.setAdapter(adapter);
        
        binding.fabAddTask.setOnClickListener(v -> {
            Resource<User> resource = homeViewModel.getUserLiveData().getValue();
            if (resource != null && resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = getCurrentTripId();
                if (tripId == null || tripId.isEmpty()) tripId = user.getLastActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    showAddTaskBottomSheet(tripId, null);
                } else {
                    Toast.makeText(requireContext(), "Select or create a trip first", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Select or create a trip first", Toast.LENGTH_SHORT).show();
            }
        });
        
        String tripIdForScreen = getCurrentTripId();
        if (tripIdForScreen != null && !tripIdForScreen.isEmpty()) {
            viewModel.loadTasks(tripIdForScreen);
        } else {
            homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource.isSuccess() && resource.getData() != null) {
                    User user = resource.getData();
                    String tripId = user.getLastActiveTripId();
                    if (tripId != null && !tripId.isEmpty()) {
                        viewModel.loadTasks(tripId);
                    }
                }
            });
        }
        
        viewModel.getTasksLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.getData() != null) {
                Map<String, List<Task>> tasksByCategory = resource.getData();
                adapter.updateTasks(tasksByCategory);
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), "Failed to load tasks: " + resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        setActiveBottomNav("tasks");
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
                // already on Tasks
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
    }

    private void showAddTaskBottomSheet(String tripId, Task task) {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance(tripId, task);
        bottomSheet.show(getParentFragmentManager(), "AddTaskBottomSheet");
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
