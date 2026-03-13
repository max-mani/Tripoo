package com.example.tripoo.ui.tasks;

import android.os.Bundle;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Task;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.FragmentTasksBinding;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.example.tripoo.viewmodel.TaskViewModel;

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
                String tripId = user.getLastActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    showAddTaskBottomSheet(tripId, null);
                } else {
                    Toast.makeText(requireContext(), "Select or create a trip first", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Select or create a trip first", Toast.LENGTH_SHORT).show();
            }
        });
        
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                String tripId = user.getLastActiveTripId();
                if (tripId != null && !tripId.isEmpty()) {
                    viewModel.loadTasks(tripId);
                }
            }
        });
        
        viewModel.getTasksLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.getData() != null) {
                Map<String, List<Task>> tasksByCategory = resource.getData();
                adapter.updateTasks(tasksByCategory);
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), "Failed to load tasks: " + resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddTaskBottomSheet(String tripId, Task task) {
        AddTaskBottomSheet bottomSheet = AddTaskBottomSheet.newInstance(tripId, task);
        bottomSheet.show(getParentFragmentManager(), "AddTaskBottomSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
