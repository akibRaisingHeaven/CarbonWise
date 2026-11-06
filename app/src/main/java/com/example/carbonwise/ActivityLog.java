package com.example.carbonwise;

public class ActivityLog {
    private String id;
    private String category;
    private String title;
    private double co2Emitted;
    private String date;
    private long timestamp;

    public ActivityLog() {
        // Default constructor for Firestore deserialization
    }

    public ActivityLog(String id, String category, String title, double co2Emitted, String date, long timestamp) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.co2Emitted = co2Emitted;
        this.date = date;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getCo2Emitted() { return co2Emitted; }
    public void setCo2Emitted(double co2Emitted) { this.co2Emitted = co2Emitted; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}