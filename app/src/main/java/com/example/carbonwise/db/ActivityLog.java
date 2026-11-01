package com.example.carbonwise.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "activities")
public class ActivityLog {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String uid; // FK to users table
    public String category; // TRANSPORT, ELECTRICITY, MEAL
    public String subType; // CAR, BUS, AC, BEEF, etc.
    public double value1; // distance, kWh, or servings
    public double value2; // passengers (for transport)
    public double co2e; // calculated kg CO2e
    public long timestamp; // epoch millis
    public int synced; // 0 = not synced, 1 = synced
    public String notes;

    public ActivityLog(String uid, String category, String subType, double value1,
                       double value2, double co2e, long timestamp, int synced, String notes) {
        this.uid = uid;
        this.category = category;
        this.subType = subType;
        this.value1 = value1;
        this.value2 = value2;
        this.co2e = co2e;
        this.timestamp = timestamp;
        this.synced = synced;
        this.notes = notes;
    }
}