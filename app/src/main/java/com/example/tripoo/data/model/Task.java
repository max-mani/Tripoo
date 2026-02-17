package com.example.tripoo.data.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class Task {
    public static final String CATEGORY_BOOKING = "Booking";
    public static final String CATEGORY_PACKING = "Packing";
    public static final String CATEGORY_GENERAL = "General";

    private String taskId;
    private String title;
    private String category;
    private String assignedTo;
    private boolean completed;
    private String createdBy;
    private Timestamp dueDate;

    public Task() {
        // Default constructor required for Firestore
    }

    public Task(String taskId, String title, String category, String assignedTo, 
                boolean completed, String createdBy, Timestamp dueDate) {
        this.taskId = taskId;
        this.title = title;
        this.category = category;
        this.assignedTo = assignedTo;
        this.completed = completed;
        this.createdBy = createdBy;
        this.dueDate = dueDate;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getDueDate() {
        return dueDate;
    }

    public void setDueDate(Timestamp dueDate) {
        this.dueDate = dueDate;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("category", category != null ? category : CATEGORY_GENERAL);
        map.put("assignedTo", assignedTo != null ? assignedTo : "");
        map.put("completed", completed);
        map.put("createdBy", createdBy != null ? createdBy : "");
        map.put("dueDate", dueDate != null ? dueDate : Timestamp.now());
        return map;
    }
}
