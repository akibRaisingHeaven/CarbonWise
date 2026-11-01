package com.example.carbonwise.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "emission_factors")
public class EmissionFactor {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String category;
    public String subType;
    public double factor; // kg CO2e per unit
    public String unit; // per km, per kWh, per meal
    public long updatedAt;

    public EmissionFactor(String category, String subType, double factor, String unit, long updatedAt) {
        this.category = category;
        this.subType = subType;
        this.factor = factor;
        this.unit = unit;
        this.updatedAt = updatedAt;
    }
}