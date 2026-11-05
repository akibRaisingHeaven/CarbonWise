package com.example.carbonwise;

public class ActivityLog {
    private String id;
    private String category;     // e.g., "Transport", "Energy", "Food"
    private String title;        // e.g., "Car Commute", "AC Usage"
    private double co2Emitted;   // e.g., 2.5 (in kg)
    private String date;         // e.g., "2026-11-05"

    public ActivityLog() {} // Required for Firestore

    public ActivityLog(String id, String category, String title, double co2Emitted, String date) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.co2Emitted = co2Emitted;
        this.date = date;
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public double getCo2Emitted() { return co2Emitted; }
    public String getDate() { return date; }
}