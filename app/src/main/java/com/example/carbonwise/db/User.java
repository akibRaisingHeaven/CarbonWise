package com.example.carbonwise.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String uid; // Firebase Auth UID
    public String displayName;
    public String dietPref; // beef, chicken, veg, vegan
    public String vehicleType; // petrol, diesel, electric, none
    public int householdSize;
    public String gridRegion;
    public double dailyBudget; // in kg CO2e
    public int cloudSyncEnabled; // 0 = false, 1 = true

    public User(@NonNull String uid, String displayName, String dietPref, String vehicleType,
                int householdSize, String gridRegion, double dailyBudget, int cloudSyncEnabled) {
        this.uid = uid;
        this.displayName = displayName;
        this.dietPref = dietPref;
        this.vehicleType = vehicleType;
        this.householdSize = householdSize;
        this.gridRegion = gridRegion;
        this.dailyBudget = dailyBudget;
        this.cloudSyncEnabled = cloudSyncEnabled;
    }
}