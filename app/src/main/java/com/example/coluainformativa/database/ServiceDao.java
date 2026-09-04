package com.example.coluainformativa.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ServiceDao {
    @Query("SELECT * FROM services ORDER BY orden ASC")
    List<ServiceEntity> getAllServices();

    @Insert
    void insertAll(ServiceEntity... services);

    @Update
    void update(ServiceEntity service);

    @Query("DELETE FROM services")
    void deleteAll();
}