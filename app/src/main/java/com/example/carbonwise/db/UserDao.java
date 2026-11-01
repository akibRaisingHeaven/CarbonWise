package com.example.carbonwise.db;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

@Dao
public interface UserDao {
    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    User getUser(String uid);

    @Upsert
    void upsert(User user);
}