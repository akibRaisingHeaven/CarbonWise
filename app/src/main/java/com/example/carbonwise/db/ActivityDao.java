package com.example.carbonwise.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ActivityDao {
    @Insert
    long insert(ActivityLog activity);

    @Update
    void update(ActivityLog activity);

    @Delete
    void delete(ActivityLog activity);

    @Query("SELECT * FROM activities WHERE uid = :uid ORDER BY timestamp DESC")
    List<ActivityLog> getAllForUser(String uid);

    @Query("SELECT * FROM activities WHERE uid = :uid AND synced = 0")
    List<ActivityLog> getUnsyncedForUser(String uid);

    @Query("SELECT SUM(co2e) FROM activities WHERE uid = :uid AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp")
    Double getDailyTotal(String uid, long startTimestamp, long endTimestamp);
}