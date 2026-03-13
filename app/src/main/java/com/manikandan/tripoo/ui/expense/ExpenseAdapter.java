package com.manikandan.tripoo.ui.expense;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.manikandan.tripoo.R;
import com.manikandan.tripoo.data.model.Expense;
import com.manikandan.tripoo.databinding.ItemExpenseBinding;
import com.manikandan.tripoo.utils.DateFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {
    private List<Expense> expenses;
    private Map<String, String> userIdToNameMap;
    private OnExpenseClickListener listener;

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
    }

    public ExpenseAdapter(List<Expense> expenses, Map<String, String> userIdToNameMap, OnExpenseClickListener listener) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
        this.userIdToNameMap = userIdToNameMap != null ? userIdToNameMap : new HashMap<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExpenseBinding binding = ItemExpenseBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ExpenseViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position);
        holder.bind(expense, userIdToNameMap);
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    public void updateExpenses(List<Expense> newExpenses) {
        this.expenses = newExpenses != null ? newExpenses : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void updateUserIdToNameMap(Map<String, String> userIdToNameMap) {
        this.userIdToNameMap = userIdToNameMap != null ? userIdToNameMap : new HashMap<>();
        notifyDataSetChanged();
    }

    class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private ItemExpenseBinding binding;

        public ExpenseViewHolder(@NonNull ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Expense expense, Map<String, String> userIdToNameMap) {
            binding.tvExpenseTitle.setText(expense.getTitle());
            binding.tvExpenseAmount.setText("₹ " + String.format("%.0f", expense.getAmount()));
            
            // Map user ID to name - use "Unknown" instead of showing user ID
            String paidByName = userIdToNameMap.getOrDefault(expense.getPaidBy(), "Unknown");
            if (paidByName == null || paidByName.isEmpty() || paidByName.equals(expense.getPaidBy())) {
                paidByName = "Unknown";
            }
            binding.tvPaidBy.setText("Paid by: " + paidByName);
            
            if (expense.getSplitWith() != null && !expense.getSplitWith().isEmpty()) {
                List<String> splitWithNames = new ArrayList<>();
                for (String userId : expense.getSplitWith()) {
                    String name = userIdToNameMap.getOrDefault(userId, "Unknown");
                    if (name == null || name.isEmpty() || name.equals(userId)) {
                        name = "Unknown";
                    }
                    splitWithNames.add(name);
                }
                String splitWith = String.join(", ", splitWithNames);
                binding.tvSplitWith.setText("Split with: " + splitWith);
            }
            
            binding.tvTimestamp.setText(DateFormatter.formatDateTime(expense.getTimestamp()));
            
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpenseClick(expense);
                }
            });
        }
    }
}
