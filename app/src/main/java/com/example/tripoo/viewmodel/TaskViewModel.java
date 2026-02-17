package com.example.tripoo.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.tripoo.data.model.Task;
import com.example.tripoo.data.repository.AuthRepository;
import com.example.tripoo.data.repository.TaskRepository;
import com.example.tripoo.utils.Resource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskViewModel extends AndroidViewModel {
    private static final String TAG = "TaskViewModel";
    private TaskRepository taskRepository;
    private AuthRepository authRepository;
    private MutableLiveData<Resource<Map<String, List<Task>>>> tasksLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> addTaskLiveData = new MutableLiveData<>();
    private MutableLiveData<Resource<String>> updateTaskLiveData = new MutableLiveData<>();
    private ListenerRegistration tasksListener;
    private String currentTripId;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        taskRepository = new TaskRepository();
        authRepository = new AuthRepository();
    }

    public void loadTasks(String tripId) {
        currentTripId = tripId;
        tasksLiveData.setValue(Resource.loading());
        
        if (tasksListener != null) {
            tasksListener.remove();
        }
        
        tasksListener = taskRepository.listenToTasks(tripId, (snapshot, e) -> {
            if (e != null) {
                tasksLiveData.setValue(Resource.error(e.getMessage()));
                return;
            }
            
            if (snapshot != null) {
                Map<String, List<Task>> tasksByCategory = new HashMap<>();
                tasksByCategory.put(Task.CATEGORY_BOOKING, new ArrayList<>());
                tasksByCategory.put(Task.CATEGORY_PACKING, new ArrayList<>());
                tasksByCategory.put(Task.CATEGORY_GENERAL, new ArrayList<>());
                
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Task task = doc.toObject(Task.class);
                    if (task != null) {
                        task.setTaskId(doc.getId());
                        String category = task.getCategory();
                        if (category == null) category = Task.CATEGORY_GENERAL;
                        else if (category.equalsIgnoreCase(Task.CATEGORY_BOOKING)) category = Task.CATEGORY_BOOKING;
                        else if (category.equalsIgnoreCase(Task.CATEGORY_PACKING)) category = Task.CATEGORY_PACKING;
                        else if (!tasksByCategory.containsKey(category)) category = Task.CATEGORY_GENERAL;
                        List<Task> categoryTasks = tasksByCategory.get(category);
                        if (categoryTasks != null) {
                            categoryTasks.add(task);
                        }
                    }
                }
                
                // Sort each category's tasks by dueDate (nulls last)
                Comparator<Task> dueDateComparator = (a, b) -> {
                    Timestamp da = a.getDueDate();
                    Timestamp db = b.getDueDate();
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return da.compareTo(db);
                };
                for (List<Task> list : tasksByCategory.values()) {
                    Collections.sort(list, dueDateComparator);
                }
                
                Log.d(TAG, "Loaded " + snapshot.getDocuments().size() + " tasks from Firestore");
                tasksLiveData.setValue(Resource.success(tasksByCategory));
            }
        });
    }

    public void addTask(String tripId, String title, String category, String assignedTo, Timestamp dueDate) {
        addTaskLiveData.setValue(Resource.loading());
        
        String currentUserId = authRepository.getCurrentUser() != null ? 
                authRepository.getCurrentUser().getUid() : null;
        
        Task task = new Task(null, title, category, assignedTo, false, currentUserId, dueDate);
        taskRepository.addTask(tripId, task)
                .addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        addTaskLiveData.setValue(Resource.success("Task added successfully"));
                    } else {
                        Exception ex = task1.getException();
                        String msg = ex != null ? ex.getMessage() : "Failed to add task";
                        Log.e(TAG, "Failed to add task: " + msg, ex);
                        addTaskLiveData.setValue(Resource.error(msg));
                    }
                });
    }

    public void updateTask(String tripId, String taskId, String title, String category, String assignedTo, boolean completed, Timestamp dueDate) {
        updateTaskLiveData.setValue(Resource.loading());
        
        String currentUserId = authRepository.getCurrentUser() != null ? 
                authRepository.getCurrentUser().getUid() : null;
        
        Task task = new Task(taskId, title, category, assignedTo, completed, currentUserId, dueDate);
        taskRepository.updateTask(tripId, taskId, task)
                .addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        updateTaskLiveData.setValue(Resource.success("Task updated successfully"));
                    } else {
                        updateTaskLiveData.setValue(Resource.error(
                                task1.getException() != null ? task1.getException().getMessage() : "Failed to update task"));
                    }
                });
    }

    public void toggleTaskCompletion(String tripId, String taskId, Task task) {
        task.setCompleted(!task.isCompleted());
        updateTask(tripId, taskId, task.getTitle(), task.getCategory(), task.getAssignedTo(), 
                task.isCompleted(), task.getDueDate());
    }

    public void deleteTask(String tripId, String taskId) {
        taskRepository.deleteTask(tripId, taskId)
                .addOnCompleteListener(task -> {
                    // Task will be removed from list via listener
                });
    }

    public LiveData<Resource<Map<String, List<Task>>>> getTasksLiveData() {
        return tasksLiveData;
    }

    public LiveData<Resource<String>> getAddTaskLiveData() {
        return addTaskLiveData;
    }

    public LiveData<Resource<String>> getUpdateTaskLiveData() {
        return updateTaskLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (tasksListener != null) {
            tasksListener.remove();
        }
    }
}
