package com.example.tripoo.ui.expense;

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
import com.example.tripoo.R;
import com.example.tripoo.data.model.Expense;
import com.example.tripoo.data.model.TripMember;
import com.example.tripoo.data.model.User;
import com.example.tripoo.databinding.BottomSheetAddExpenseBinding;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.viewmodel.ExpenseViewModel;
import com.example.tripoo.viewmodel.GroupsViewModel;
import com.example.tripoo.viewmodel.HomeViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class AddExpenseBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetAddExpenseBinding binding;
    private ExpenseViewModel expenseViewModel;
    private GroupsViewModel groupsViewModel;
    private HomeViewModel homeViewModel;
    private MemberCheckboxAdapter memberAdapter;
    private String tripId;
    private Expense expense;
    private String currentUserId;

    public static AddExpenseBottomSheet newInstance(String tripId, Expense expense) {
        AddExpenseBottomSheet fragment = new AddExpenseBottomSheet();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        if (expense != null) {
            args.putString("expenseId", expense.getExpenseId());
            args.putString("title", expense.getTitle());
            args.putDouble("amount", expense.getAmount());
            args.putString("paidBy", expense.getPaidBy());
            args.putStringArrayList("splitWith", expense.getSplitWith() != null ? new ArrayList<>(expense.getSplitWith()) : null);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = BottomSheetAddExpenseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        Bundle args = getArguments();
        if (args != null) {
            tripId = args.getString("tripId");
            String expenseId = args.getString("expenseId");
            if (expenseId != null) {
                expense = new Expense(
                        expenseId,
                        args.getString("title"),
                        args.getDouble("amount", 0),
                        args.getString("paidBy"),
                        args.getStringArrayList("splitWith"),
                        null,
                        null
                );
            } else {
                expense = null;
            }
        }

        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);
        groupsViewModel = new ViewModelProvider(requireActivity()).get(GroupsViewModel.class);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        
        AuthRepository authRepository = new AuthRepository();
        currentUserId = authRepository.getCurrentUser() != null ? authRepository.getCurrentUser().getUid() : null;
        
        memberAdapter = new MemberCheckboxAdapter(new ArrayList<>());
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(memberAdapter);
        
        if (tripId != null) {
            groupsViewModel.loadTripAndMembers(tripId);
        }
        
        // Get current user name and set as default paidBy
        homeViewModel.getUserLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                User user = resource.getData();
                // User name will be shown in the member list, no need to set it separately
            }
        });
        
        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                memberAdapter.updateMembers(resource.getData());
                // Auto-select current user if adding new expense
                if (expense == null && currentUserId != null) {
                    memberAdapter.selectMember(currentUserId);
                }
            }
        });

        expenseViewModel.getAddExpenseLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        expenseViewModel.getUpdateExpenseLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                dismiss();
            } else if (resource != null && resource.isError()) {
                Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        if (expense != null) {
            binding.etTitle.setText(expense.getTitle());
            binding.etAmount.setText(String.valueOf(expense.getAmount()));
        }
        
        binding.btnSaveExpense.setOnClickListener(v -> {
            String title = binding.etTitle.getText().toString().trim();
            String amountStr = binding.etAmount.getText().toString().trim();
            
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(amountStr)) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double amount = Double.parseDouble(amountStr);
                List<String> selectedMembers = memberAdapter.getSelectedMembers();
                
                if (selectedMembers.isEmpty()) {
                    Toast.makeText(requireContext(), "Please select at least one member", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Use current user as paidBy if they're selected, otherwise use first selected
                String paidBy = currentUserId != null && selectedMembers.contains(currentUserId) 
                        ? currentUserId 
                        : selectedMembers.get(0);
                
                if (expense != null) {
                    expenseViewModel.updateExpense(tripId, expense.getExpenseId(), title, amount, paidBy, selectedMembers);
                } else {
                    expenseViewModel.addExpense(tripId, title, amount, paidBy, selectedMembers);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
