package com.example.coluainformativa.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NavigationDao {
    @Query("SELECT * FROM navigation_items WHERE isVisible = 1 AND (type = :type OR (type = 'SIDEBAR' AND :type = 'SIDE_MENU') OR (type = 'SIDE_MENU' AND :type = 'SIDEBAR') OR (type = 'NAVBAR' AND :type = 'TOP_ACTION') OR (type = 'TOP_ACTION' AND :type = 'NAVBAR')) ORDER BY displayOrder ASC")
    fun getVisibleItemsByType(type: String): List<NavigationItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: NavigationItemEntity)

    @Query("SELECT * FROM navigation_items")
    fun getAllItems(): List<NavigationItemEntity>

    @Query("DELETE FROM navigation_items WHERE targetSectionId = :sectionId")
    fun deleteByTargetSection(sectionId: String)

    @Query("DELETE FROM navigation_items WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM navigation_items")
    fun deleteAll()
}
