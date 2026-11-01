package com.example.carbonwise.db;

import androidx.room.Dao;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ApplianceDao {
    @Query("SELECT * FROM appliances")
    List<Appliance> getAll();
}