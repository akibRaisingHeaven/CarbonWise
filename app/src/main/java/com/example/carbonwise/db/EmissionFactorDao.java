package com.example.carbonwise.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.List;

@Dao
public interface EmissionFactorDao {
    @Query("SELECT factor FROM emission_factors WHERE subType = :subType LIMIT 1")
    Double getFactor(String subType);

    @Upsert
    void updateAll(List<EmissionFactor> factors);
}