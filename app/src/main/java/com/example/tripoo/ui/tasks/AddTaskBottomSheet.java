package com.example.tripoo.ui.tasks;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Task;
import com.example.tripoo.databinding.BottomSheetAddTaskBinding;
import com.example.tripoo.utils.DateFormatter;
import com.example.tripoo.viewmodel.GroupsViewModel;
import com.example.tripoo.viewmodel.TaskViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;

import java.util.Calendar;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetAddTaskBinding binding;
    private TaskViewModel taskViewModel;
    private GroupsViewModel groupsViewModel;
    private String tripId;
    private Task task;
    private Calendar dueCalendar = Calendar.getInstance();

    public static AddTaskBottomSheet newInstance(String tripId, Task task) {
        AddTaskBottomSheet fragment = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        fragment.task = task;
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = BottomSheetAddTaskBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
        }
        
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        groupsViewModel = new ViewModelProvider(requireActivity()).get(GroupsViewModel.class);
        
        binding.etCategory.setOnClickListener(v -> showCategoryDialog());
        binding.etAssignedTo.setOnClickListener(v -> showMemberDialog());
        binding.etDueDate.setOnClickListener(v -> showDatePicker());
        
        if (task != null) {
            binding.etTitle.setText(task.getTitle());
            binding.etCategory.setText(task.getCategory());
            binding.etAssignedTo.setText(task.getAssignedTo());
            if (task.getDueDate() != null) {
                binding.etDueDate.setText(DateFormatter.formatDate(task.getDueDate()));
            }
        }
        
        binding.btnSaveTask.setOnClickListener(v -> {
            String title = binding.etTitle.getText().toString().trim();
            String category = binding.etCategory.getText().toString().trim();
            String assignedTo = binding.etAssignedTo.getText().toString().trim();
            
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(category)) {
                Toast.makeText(requireContext(), "Please fill required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Timestamp dueDate = new Timestamp(dueCalendar.getTime());
            
            if (task != null) {
                taskViewModel.updateTask(tripId, task.getTaskId(), title, category, assignedTo, task.isCompleted(), dueDate);
            } else {
                taskViewModel.addTask(tripId, title, category, assignedTo, dueDate);
            }
            
            taskViewModel.getAddTaskLiveData().observe(getViewLifecycleOwner(), resource -> {
                if (resource.isSuccess()) {
                    dismiss();
                } else if (resource.isError()) {
                    Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showCategoryDialog() {
        // Simple category selection - can be enhanced with MaterialDialog
        String[] categories = {Task.CATEGORY_BOOKING, Task.CATEGORY_PACKING, Task.CATEGORY_GENERAL};
        // For now, just set first category
        binding.etCategory.setText(categories[0]);
    }

    private void showMemberDialog() {
        // Member selection dialog - simplified for now
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    dueCalendar.set(year, month, dayOfMonth);
                    String dateStr = DateFormatter.formatDate(new Timestamp(dueCalendar.getTime()));
                    binding.etDueDate.setText(dateStr);
                },
                dueCalendar.get(Calendar.YEAR),
                dueCalendar.get(Calendar.MONTH),
                dueCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
