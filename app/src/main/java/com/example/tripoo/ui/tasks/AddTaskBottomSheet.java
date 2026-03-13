package com.example.tripoo.ui.tasks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
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

import android.widget.ArrayAdapter;

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
    private String selectedPriority = "medium";

    public static AddTaskBottomSheet newInstance(String tripId, Task task) {
        AddTaskBottomSheet fragment = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        fragment.task = task;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.Theme_Tripoo_BottomSheet);
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
        
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
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
                                        currentUser.getUid(),
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
        
        if (binding.btnClose != null) {
            binding.btnClose.setOnClickListener(v -> dismiss());
        }

        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.task_categories, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCategory.setAdapter(categoryAdapter);

        if (binding.assignContainer != null) {
            binding.assignContainer.setOnClickListener(v -> showMemberDialog());
        } else if (binding.etAssignedTo != null) {
            binding.etAssignedTo.setOnClickListener(v -> showMemberDialog());
        }
        if (binding.dateContainer != null) {
            binding.dateContainer.setOnClickListener(v -> showDatePicker());
        } else if (binding.etDueDate != null) {
            binding.etDueDate.setOnClickListener(v -> showDatePicker());
        }

        View[] prioViews = {binding.prioLow, binding.prioMedium, binding.prioHigh};
        String[] prios = {"low", "medium", "high"};
        for (int i = 0; i < prioViews.length; i++) {
            final int idx = i;
            prioViews[i].setOnClickListener(v -> {
                selectedPriority = prios[idx];
                for (int j = 0; j < prioViews.length; j++) {
                    prioViews[j].setBackgroundResource(j == idx ? R.drawable.bg_chip_on : R.drawable.bg_input_outline);
                }
            });
        }

        taskViewModel.getAddTaskLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                String msg = resource.getMessage();
                Toast.makeText(requireContext(), "Failed: " + (msg != null ? msg : "Unknown error"), Toast.LENGTH_LONG).show();
            }
        });
        taskViewModel.getUpdateTaskLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        binding.etAssignedTo.setText("Everyone");
        if (task != null) {
            binding.etTitle.setText(task.getTitle());
            String cat = task.getCategory();
            if (cat != null) {
                String[] cats = getResources().getStringArray(R.array.task_categories);
                for (int i = 0; i < cats.length; i++) {
                    if (cats[i].equalsIgnoreCase(cat)) {
                        binding.spinnerCategory.setSelection(i);
                        break;
                    }
                }
            }
            binding.etAssignedTo.setText(task.getAssignedTo());
            if (task.getDueDate() != null) {
                binding.etDueDate.setText(DateFormatter.formatDate(task.getDueDate()));
                dueCalendar.setTimeInMillis(task.getDueDate());
            }
        }
        // Don't set default category - let user type or select from dialog
        
        binding.btnSaveTask.setOnClickListener(v -> {
            if (tripId == null || tripId.isEmpty()) {
                Toast.makeText(requireContext(), "No trip selected", Toast.LENGTH_SHORT).show();
                return;
            }
            String title = binding.etTitle.getText().toString().trim();
            String catStr = binding.spinnerCategory.getSelectedItem() != null ? binding.spinnerCategory.getSelectedItem().toString() : "General";
            String category = "general";
            if ("Bookings".equalsIgnoreCase(catStr)) category = Task.CATEGORY_BOOKING;
            else if ("Packing".equalsIgnoreCase(catStr)) category = Task.CATEGORY_PACKING;
            else if ("Documents".equalsIgnoreCase(catStr) || "Other".equalsIgnoreCase(catStr)) category = catStr.toLowerCase();
            String assignedTo = binding.etAssignedTo.getText() != null ? binding.etAssignedTo.getText().toString().trim() : "Everyone";
            
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(requireContext(), "Please enter task name", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Timestamp dueDate = new Timestamp(dueCalendar.getTime());
            
            if (task != null) {
                taskViewModel.updateTask(tripId, task.getId(), title, category, assignedTo, task.getCompleted(), dueDate);
            } else {
                taskViewModel.addTask(tripId, title, category, assignedTo, dueDate);
            }
        });
    }

    private void showMemberDialog() {
        List<String> options = new ArrayList<>();
        options.add("Everyone");
        for (TripMember m : members) {
            options.add(m.getName());
        }
        String[] memberNames = options.toArray(new String[0]);
        
        String currentAssignedTo = binding.etAssignedTo.getText() != null ? binding.etAssignedTo.getText().toString().trim() : "Everyone";
        int currentSelection = 0;
        for (int i = 0; i < memberNames.length; i++) {
            if (memberNames[i].equals(currentAssignedTo)) {
                currentSelection = i;
                break;
            }
        }
        
        new AlertDialog.Builder(requireContext())
                .setTitle("Assign To")
                .setSingleChoiceItems(memberNames, currentSelection, (dialog, which) -> {
                    binding.etAssignedTo.setText(memberNames[which]);
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
