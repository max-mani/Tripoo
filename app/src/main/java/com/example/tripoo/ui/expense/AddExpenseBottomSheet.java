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
import com.example.tripoo.databinding.BottomSheetAddExpenseBinding;
import com.example.tripoo.viewmodel.ExpenseViewModel;
import com.example.tripoo.viewmodel.GroupsViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class AddExpenseBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetAddExpenseBinding binding;
    private ExpenseViewModel expenseViewModel;
    private GroupsViewModel groupsViewModel;
    private MemberCheckboxAdapter memberAdapter;
    private String tripId;
    private Expense expense;

    public static AddExpenseBottomSheet newInstance(String tripId, Expense expense) {
        AddExpenseBottomSheet fragment = new AddExpenseBottomSheet();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        fragment.expense = expense;
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
        
        if (getArguments() != null) {
            tripId = getArguments().getString("tripId");
        }
        
        expenseViewModel = new ViewModelProvider(this).get(ExpenseViewModel.class);
        groupsViewModel = new ViewModelProvider(requireActivity()).get(GroupsViewModel.class);
        
        memberAdapter = new MemberCheckboxAdapter(new ArrayList<>());
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembers.setAdapter(memberAdapter);
        
        if (tripId != null) {
            groupsViewModel.loadTripAndMembers(tripId);
        }
        
        groupsViewModel.getMembersLiveData().observe(getViewLifecycleOwner(), resource -> {
            if (resource.isSuccess() && resource.getData() != null) {
                memberAdapter.updateMembers(resource.getData());
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
                
                String paidBy = selectedMembers.get(0); // For now, use first selected
                
                if (expense != null) {
                    expenseViewModel.updateExpense(tripId, expense.getExpenseId(), title, amount, paidBy, selectedMembers);
                } else {
                    expenseViewModel.addExpense(tripId, title, amount, paidBy, selectedMembers);
                }
                
                expenseViewModel.getAddExpenseLiveData().observe(getViewLifecycleOwner(), resource -> {
                    if (resource.isSuccess()) {
                        dismiss();
                    } else if (resource.isError()) {
                        Toast.makeText(requireContext(), resource.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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
