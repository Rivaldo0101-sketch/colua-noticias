package com.example.coluainformativa.database

import androidx.room.*

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE deletedAt IS NULL ORDER BY displayOrder ASC")
    fun getAllSections(): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE isVisible = 1 AND deletedAt IS NULL ORDER BY displayOrder ASC")
    fun getVisibleSections(): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :id AND deletedAt IS NULL")
    fun getSectionById(id: String): SectionEntity?

    @Query("SELECT * FROM sections WHERE deletedAt IS NOT NULL")
    fun getDeletedSections(): List<SectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(section: SectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(sections: List<SectionEntity>)

    @Update
    fun update(section: SectionEntity)

    @Query("DELETE FROM sections WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM sections")
    fun deleteAll()
}
