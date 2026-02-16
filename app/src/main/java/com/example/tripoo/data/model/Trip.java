package com.example.tripoo.data.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class Trip {
    private String tripId;
    private String place;
    private Timestamp startDate;
    private Timestamp endDate;
    private double budget;
    private String tripCode;
    private String adminId;
    private boolean isActive;

    public Trip() {
        // Default constructor required for Firestore
    }

    public Trip(String tripId, String place, Timestamp startDate, Timestamp endDate, 
                double budget, String tripCode, String adminId, boolean isActive) {
        this.tripId = tripId;
        this.place = place;
        this.startDate = startDate;
        this.endDate = endDate;
        this.budget = budget;
        this.tripCode = tripCode;
        this.adminId = adminId;
        this.isActive = isActive;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    /**
     * Returns trip start time as epoch millis for countdown/timestamp use.
     * Firestore Timestamp is stored as seconds + nanoseconds.
     */
    public long getStartDateMillis() {
        if (startDate == null) return 0L;
        return startDate.getSeconds() * 1000L + startDate.getNanoseconds() / 1_000_000;
    }

    public void setStartDate(Timestamp startDate) {
        this.startDate = startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public void setEndDate(Timestamp endDate) {
        this.endDate = endDate;
    }

    public double getBudget() {
        return budget;
    }

    public void setBudget(double budget) {
        this.budget = budget;
    }

    public String getTripCode() {
        return tripCode;
    }

    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("place", place);
        map.put("startDate", startDate);
        map.put("endDate", endDate);
        map.put("budget", budget);
        map.put("tripCode", tripCode);
        map.put("adminId", adminId);
        map.put("isActive", isActive);
        return map;
    }
}
