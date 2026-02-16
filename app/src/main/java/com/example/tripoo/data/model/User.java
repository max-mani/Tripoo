package com.example.tripoo.data.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    private String userId;
    private String name;
    private String email;
    private String photoUrl;
    private String activeTripId;

    public User() {
        // Default constructor required for Firestore
    }

    public User(String userId, String name, String email, String photoUrl, String activeTripId) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.photoUrl = photoUrl;
        this.activeTripId = activeTripId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getActiveTripId() {
        return activeTripId;
    }

    public void setActiveTripId(String activeTripId) {
        this.activeTripId = activeTripId;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("email", email);
        map.put("photoUrl", photoUrl != null ? photoUrl : "");
        map.put("activeTripId", activeTripId != null ? activeTripId : "");
        return map;
    }
}
