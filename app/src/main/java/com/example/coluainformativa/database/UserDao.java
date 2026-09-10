package com.example.coluainformativa.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM users WHERE identifier = :identifier LIMIT 1")
    UserEntity getUserByIdentifier(String identifier);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity getUserByEmail(String email);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserEntity user);

    @Query("SELECT * FROM users ORDER BY id DESC")
    List<UserEntity> getAllUsers();

    @Query("SELECT COUNT(*) FROM users")
    int getUserCount();

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")
    int getAdminCount();

    @Query("DELETE FROM users")
    void deleteAll();
}
