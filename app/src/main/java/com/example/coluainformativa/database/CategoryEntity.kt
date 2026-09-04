package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class CategoryEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var sectionId: String = "",
    @JvmField var title: String = "",
    @JvmField var description: String = "",
    @JvmField var displayOrder: Int = 0,
    @JvmField var isVisible: Boolean = true
)
