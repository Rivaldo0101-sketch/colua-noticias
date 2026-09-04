package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sections")
data class SectionEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var title: String = "",
    @JvmField var slug: String = "",
    @JvmField var description: String = "",
    @JvmField var iconName: String = "",
    @JvmField var accentColor: String = "#173789",
    @JvmField var displayOrder: Int = 0,
    @JvmField var isVisible: Boolean = true,
    @JvmField var isPublished: Boolean = true,
    @JvmField var createdAt: Long = System.currentTimeMillis(),
    @JvmField var updatedAt: Long = System.currentTimeMillis(),
    @JvmField var deletedAt: Long? = null,
    @JvmField var syncStatus: String = "SYNCED",
    @JvmField var version: Int = 1,
    @JvmField var templateType: String = "GRID"
)
