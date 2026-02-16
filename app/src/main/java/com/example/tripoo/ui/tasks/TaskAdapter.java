package com.example.tripoo.ui.tasks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tripoo.R;
import com.example.tripoo.data.model.Task;
import com.example.tripoo.databinding.ItemTaskBinding;
import com.example.tripoo.utils.DateFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK = 1;
    
    private List<Object> items;
    private OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    public TaskAdapter(Map<String, List<Task>> tasksByCategory, OnTaskClickListener listener) {
        this.listener = listener;
        buildItemsList(tasksByCategory);
    }

    private void buildItemsList(Map<String, List<Task>> tasksByCategory) {
        items = new ArrayList<>();
        if (tasksByCategory != null) {
            for (Map.Entry<String, List<Task>> entry : tasksByCategory.entrySet()) {
                String category = entry.getKey();
                List<Task> tasks = entry.getValue();
                
                if (tasks != null && !tasks.isEmpty()) {
                    items.add(category); // Header
                    items.addAll(tasks); // Tasks
                }
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView textView = new TextView(parent.getContext());
            textView.setPadding(16, 16, 16, 8);
            textView.setTextSize(18);
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
            return new HeaderViewHolder(textView);
        } else {
            ItemTaskBinding binding = ItemTaskBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new TaskViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof TaskViewHolder) {
            ((TaskViewHolder) holder).bind((Task) items.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateTasks(Map<String, List<Task>> tasksByCategory) {
        buildItemsList(tasksByCategory);
        notifyDataSetChanged();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;

        public HeaderViewHolder(TextView textView) {
            super(textView);
            this.textView = textView;
        }

        public void bind(String category) {
            textView.setText(category);
        }
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private ItemTaskBinding binding;

        public TaskViewHolder(@NonNull ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Task task) {
            binding.tvTaskTitle.setText(task.getTitle());
            binding.cbCompleted.setChecked(task.isCompleted());
            
            if (task.getAssignedTo() != null && !task.getAssignedTo().isEmpty()) {
                binding.tvAssignedTo.setText("Assigned to: " + task.getAssignedTo());
            } else {
                binding.tvAssignedTo.setText("Unassigned");
            }
            
            if (task.getDueDate() != null) {
                binding.tvDueDate.setText("Due: " + DateFormatter.formatDate(task.getDueDate()));
            }
            
            binding.cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    // Handle completion toggle
                }
            });
            
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskClick(task);
                }
            });
        }
    }
}
