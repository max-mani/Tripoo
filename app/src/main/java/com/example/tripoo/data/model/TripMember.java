package com.example.tripoo.data.model;

import java.util.HashMap;
import java.util.Map;

public class TripMember {
    private String userId;
    private String name;
    private String email;
    private String photoUrl;
    private boolean isAdmin;

    public TripMember() {
        // Default constructor required for Firestore
    }

    public TripMember(String userId, String name, String email, String photoUrl, boolean isAdmin) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.photoUrl = photoUrl;
        this.isAdmin = isAdmin;
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

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("name", name);
        map.put("email", email);
        map.put("photoUrl", photoUrl != null ? photoUrl : "");
        map.put("isAdmin", isAdmin);
        return map;
    }
}
