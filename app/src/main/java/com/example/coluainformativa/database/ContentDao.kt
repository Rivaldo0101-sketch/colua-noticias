package com.example.coluainformativa.database

import androidx.room.*

@Dao
interface ContentDao {
    
    // Categories
    @Query("SELECT * FROM categories WHERE sectionId = :sectionId ORDER BY displayOrder ASC")
    fun getCategoriesBySection(sectionId: String): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: CategoryEntity)

    // Items
    @Query("SELECT * FROM content_items WHERE sectionId = :sectionId ORDER BY displayOrder ASC")
    fun getItemsBySection(sectionId: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE sectionId = :sectionId AND isDraft = 0 AND isVisible = 1 AND publicationDate <= :currentTime ORDER BY displayOrder ASC")
    fun getPublishedItemsBySection(sectionId: String, currentTime: Long): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE categoryId = :categoryId ORDER BY displayOrder ASC")
    fun getItemsByCategory(categoryId: String): List<ContentItemEntity>

    @Query("SELECT * FROM content_items WHERE id = :id")
    fun getItemById(id: String): ContentItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ContentItemEntity)

    @Update
    fun updateItem(item: ContentItemEntity)

    @Query("SELECT * FROM content_items ORDER BY sectionId, displayOrder")
    fun getAllItems(): List<ContentItemEntity>

    @Query("DELETE FROM content_items WHERE id = :id")
    fun deleteItemById(id: String)

    @Query("DELETE FROM content_items WHERE sectionId = :sectionId")
    fun deleteItemsBySection(sectionId: String)

    // Blocks
    @Query("SELECT * FROM content_blocks WHERE contentItemId = :itemId ORDER BY displayOrder ASC")
    fun getBlocksByItem(itemId: String): List<ContentBlockEntity>

    @Query("SELECT * FROM content_blocks WHERE sectionId = :sectionId ORDER BY displayOrder ASC")
    fun getBlocksBySection(sectionId: String): List<ContentBlockEntity>

    @Query("SELECT * FROM content_blocks ORDER BY sectionId, contentItemId, displayOrder")
    fun getAllBlocks(): List<ContentBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlock(block: ContentBlockEntity)

    @Query("DELETE FROM content_blocks WHERE contentItemId = :itemId")
    fun deleteBlocksByItem(itemId: String)

    @Query("DELETE FROM content_blocks WHERE sectionId = :sectionId")
    fun deleteBlocksBySection(sectionId: String)
}
