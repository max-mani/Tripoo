package com.example.tripoo.ui.tasks;

import android.app.AlertDialog;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Task;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.data.model.User;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.databinding.BottomSheetAddTaskBinding;
import com.example.tripoo.utils.DateFormatter;
import com.example.tripoo.utils.Resource;
import com.example.tripoo.viewmodel.GroupsViewModel;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.example.tripoo.viewmodel.TaskViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetAddTaskBinding binding;
    private TaskViewModel taskViewModel;
    private GroupsViewModel groupsViewModel;
    private HomeViewModel homeViewModel;
    private String tripId;
    private Task task;
    private Calendar dueCalendar = Calendar.getInstance();
    private List<TripMember> members = new ArrayList<>();

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
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        
        // Load members for assigned to dialog
        if (tripId != null) {
            groupsViewModel.loadTripAndMembers(tripId);
        }
        
        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                members = resource.getData();
                // Ensure current user is included in members list
                AuthRepository authRepository = new AuthRepository();
                String currentUserId = authRepository.getCurrentUser() != null ? 
                        authRepository.getCurrentUser().getUid() : null;
                
                if (currentUserId != null) {
                    // Check if current user is already in members list
                    boolean userExists = false;
                    for (TripMember member : members) {
                        if (member.getUserId().equals(currentUserId)) {
                            userExists = true;
                            break;
                        }
                    }
                    
                    // If not in list, add current user
                    if (!userExists) {
                        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), userResource -> {
                            if (userResource.isSuccess() && userResource.getData() != null) {
                                User currentUser = userResource.getData();
                                TripMember currentUserMember = new TripMember(
                                        currentUser.getUserId(),
                                        currentUser.getName() != null ? currentUser.getName() : "User",
                                        currentUser.getEmail(),
                                        currentUser.getPhotoUrl(),
                                        false
                                );
                                members.add(currentUserMember);
                            }
                        });
                    }
                }
            }
        });
        
        // Category field is now editable - remove onClick listener that prevents typing
        // Keep dialog as optional via long press or button if needed
        binding.etCategory.setOnLongClickListener(v -> {
            showCategoryDialog();
            return true;
        });
        binding.etAssignedTo.setOnClickListener(v -> showMemberDialog());
        binding.etDueDate.setOnClickListener(v -> showDatePicker());

        taskViewModel.getAddTaskLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        taskViewModel.getUpdateTaskLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        if (task != null) {
            binding.etTitle.setText(task.getTitle());
            binding.etCategory.setText(task.getCategory());
            binding.etAssignedTo.setText(task.getAssignedTo());
            if (task.getDueDate() != null) {
                binding.etDueDate.setText(DateFormatter.formatDate(task.getDueDate()));
                dueCalendar.setTime(task.getDueDate().toDate());
            }
        }
        // Don't set default category - let user type or select from dialog
        
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
        });
    }

    private void showCategoryDialog() {
        String[] categories = {Task.CATEGORY_BOOKING, Task.CATEGORY_PACKING, Task.CATEGORY_GENERAL};
        int currentSelection = 0;
        String currentCategory = binding.etCategory.getText().toString().trim();
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(currentCategory)) {
                currentSelection = i;
                break;
            }
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Select Category")
                .setSingleChoiceItems(categories, currentSelection, (dialog, which) -> {
                    binding.etCategory.setText(categories[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMemberDialog() {
        if (members.isEmpty()) {
            Toast.makeText(requireContext(), "Loading members...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] memberNames = new String[members.size()];
        for (int i = 0; i < members.size(); i++) {
            memberNames[i] = members.get(i).getName();
        }
        
        String currentAssignedTo = binding.etAssignedTo.getText().toString().trim();
        int currentSelection = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getName().equals(currentAssignedTo)) {
                currentSelection = i;
                break;
            }
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Assign To")
                .setSingleChoiceItems(memberNames, currentSelection, (dialog, which) -> {
                    binding.etAssignedTo.setText(members.get(which).getName());
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
