package com.example.tripoo.data.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Expense {
    private String expenseId;
    private String title;
    private double amount;
    private String paidBy;
    private List<String> splitWith;
    private String createdBy;
    private Timestamp timestamp;

    public Expense() {
        // Default constructor required for Firestore
        this.splitWith = new ArrayList<>();
    }

    public Expense(String expenseId, String title, double amount, String paidBy, 
                   List<String> splitWith, String createdBy, Timestamp timestamp) {
        this.expenseId = expenseId;
        this.title = title;
        this.amount = amount;
        this.paidBy = paidBy;
        this.splitWith = splitWith != null ? splitWith : new ArrayList<>();
        this.createdBy = createdBy;
        this.timestamp = timestamp;
    }

    public String getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(String expenseId) {
        this.expenseId = expenseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(String paidBy) {
        this.paidBy = paidBy;
    }

    public List<String> getSplitWith() {
        return splitWith;
    }

    public void setSplitWith(List<String> splitWith) {
        this.splitWith = splitWith != null ? splitWith : new ArrayList<>();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("amount", amount);
        map.put("paidBy", paidBy);
        map.put("splitWith", splitWith);
        map.put("createdBy", createdBy);
        map.put("timestamp", timestamp != null ? timestamp : Timestamp.now());
        return map;
    }
}
