package com.example.tripoo.ui.expense;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Expense;
import com.example.tripoo.databinding.ItemExpenseBinding;
import com.example.tripoo.utils.DateFormatter;

import java.util.ArrayList;
import java.util.List;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {
    private List<Expense> expenses;
    private OnExpenseClickListener listener;

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
    }

    public ExpenseAdapter(List<Expense> expenses, OnExpenseClickListener listener) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
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
        holder.bind(expense);
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    public void updateExpenses(List<Expense> newExpenses) {
        this.expenses = newExpenses != null ? newExpenses : new ArrayList<>();
        notifyDataSetChanged();
    }

    class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private ItemExpenseBinding binding;

        public ExpenseViewHolder(@NonNull ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Expense expense) {
            binding.tvExpenseTitle.setText(expense.getTitle());
            binding.tvExpenseAmount.setText("₹ " + String.format("%.0f", expense.getAmount()));
            binding.tvPaidBy.setText("Paid by: " + expense.getPaidBy());
            
            if (expense.getSplitWith() != null && !expense.getSplitWith().isEmpty()) {
                String splitWith = String.join(", ", expense.getSplitWith());
                binding.tvSplitWith.setText("Split with: " + splitWith);
            }
            
            if (expense.getTimestamp() != null) {
                binding.tvTimestamp.setText(DateFormatter.formatDateTime(expense.getTimestamp()));
            }
            
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpenseClick(expense);
                }
            });
        }
    }
}
