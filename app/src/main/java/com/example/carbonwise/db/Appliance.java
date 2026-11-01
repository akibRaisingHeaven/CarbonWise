package com.example.carbonwise.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "appliances")
public class Appliance {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double defaultWatts;

    public Appliance(String name, double defaultWatts) {
        this.name = name;
        this.defaultWatts = defaultWatts;
    }
}