package com.manikandan.tripoo.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.manikandan.tripoo.data.model.Task;
import com.manikandan.tripoo.data.repository.AuthRepository;
import com.manikandan.tripoo.data.repository.TaskRepository;
import kotlin.Unit;
import com.manikandan.tripoo.utils.Resource;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskViewModel extends AndroidViewModel {
    private static final String TAG = "TaskViewModel";
    private final TaskRepository taskRepository;
    private final AuthRepository authRepository;
    private final MutableLiveData<Resource<Map<String, List<Task>>>> tasksLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> addTaskLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> updateTaskLiveData = new MutableLiveData<>();
    private com.google.firebase.firestore.ListenerRegistration tasksListener;
    private String currentTripId;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        taskRepository = new TaskRepository();
        authRepository = new AuthRepository();
    }

    private static Long timestampToLong(Timestamp t) {
        if (t == null) return null;
        return t.getSeconds() * 1000L + t.getNanoseconds() / 1_000_000;
    }

    public void loadTasks(String tripId) {
        currentTripId = tripId;
        tasksLiveData.setValue(Resource.loading());

        if (tasksListener != null) {
            tasksListener.remove();
        }

        tasksListener = taskRepository.listenToTasks(tripId, (tasks, e) -> {
            if (e != null) {
                tasksLiveData.setValue(Resource.error(e.getMessage()));
                return Unit.INSTANCE;
            }
            if (tasks != null) {
                Map<String, List<Task>> tasksByCategory = new HashMap<>();
                tasksByCategory.put(Task.CATEGORY_BOOKING, new ArrayList<>());
                tasksByCategory.put(Task.CATEGORY_PACKING, new ArrayList<>());
                tasksByCategory.put(Task.CATEGORY_GENERAL, new ArrayList<>());

                for (Task task : tasks) {
                    String category = task.getCategory();
                    if (category == null) category = Task.CATEGORY_GENERAL;
                    else if (!category.equalsIgnoreCase(Task.CATEGORY_BOOKING) && !category.equalsIgnoreCase(Task.CATEGORY_PACKING)) {
                        category = Task.CATEGORY_GENERAL;
                    }
                    List<Task> categoryTasks = tasksByCategory.get(category);
                    if (categoryTasks != null) {
                        categoryTasks.add(task);
                    }
                }

                Comparator<Task> dueDateComparator = (a, b) -> {
                    Long da = a.getDueDate();
                    Long db = b.getDueDate();
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return Long.compare(da, db);
                };
                for (List<Task> list : tasksByCategory.values()) {
                    Collections.sort(list, dueDateComparator);
                }

                Log.d(TAG, "Loaded " + tasks.size() + " tasks from Firestore");
                tasksLiveData.setValue(Resource.success(tasksByCategory));
            }
            return Unit.INSTANCE;
        });
    }

    public void addTask(String tripId, String title, String category, String assignedTo, Timestamp dueDate) {
        addTaskLiveData.setValue(Resource.loading());
        String currentUserId = authRepository.getCurrentUser() != null ? authRepository.getCurrentUser().getUid() : "everyone";
        Long dueDateLong = timestampToLong(dueDate);
        Task task = new Task("", title, category != null ? category : Task.CATEGORY_GENERAL, assignedTo != null ? assignedTo : "everyone", false, dueDateLong, "medium", null);
        taskRepository.addTask(tripId, task, err -> {
            if (err == null) {
                addTaskLiveData.setValue(Resource.success("Task added successfully"));
            } else {
                Log.e(TAG, "Failed to add task", err);
                addTaskLiveData.setValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Failed to add task"));
            }
            return Unit.INSTANCE;
        });
    }

    public void updateTask(String tripId, String taskId, String title, String category, String assignedTo, boolean completed, Timestamp dueDate) {
        updateTaskLiveData.setValue(Resource.loading());
        Long dueDateLong = timestampToLong(dueDate);
        Task task = new Task(taskId, title, category != null ? category : Task.CATEGORY_GENERAL, assignedTo != null ? assignedTo : "everyone", completed, dueDateLong, "medium", null);
        taskRepository.updateTask(tripId, taskId, task, err -> {
            if (err == null) {
                updateTaskLiveData.setValue(Resource.success("Task updated successfully"));
            } else {
                updateTaskLiveData.setValue(Resource.error(err.getMessage() != null ? err.getMessage() : "Failed to update task"));
            }
            return Unit.INSTANCE;
        });
    }

    public void toggleTaskCompletion(String tripId, String taskId, Task task) {
        boolean newCompleted = !task.getCompleted();
        taskRepository.updateTaskCompletion(tripId, taskId, newCompleted, err -> {
            if (err == null) {
                updateTaskLiveData.setValue(Resource.success("Task updated"));
            }
            return Unit.INSTANCE;
        });
    }

    public void deleteTask(String tripId, String taskId) {
        taskRepository.deleteTask(tripId, taskId, err -> Unit.INSTANCE);
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
