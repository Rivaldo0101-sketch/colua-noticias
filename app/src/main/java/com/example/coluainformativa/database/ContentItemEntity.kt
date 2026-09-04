package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_items",
    foreignKeys = [
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("sectionId"), Index("categoryId")]
)
data class ContentItemEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var sectionId: String = "",
    @JvmField var title: String = "",
    @JvmField var subtitle: String = "",
    @JvmField var shortDescription: String = "",
    @JvmField var accentColor: String = "",
    @JvmField var displayOrder: Int = 0,
    @JvmField var categoryId: String? = null,
    @JvmField var description: String = "",
    @JvmField var imagePath: String = "",
    @JvmField var iconName: String = "",
    @JvmField var targetSectionId: String = "",
    @JvmField var isVisible: Boolean = true,
    @JvmField var isDraft: Boolean = true,
    @JvmField var publicationDate: Long = System.currentTimeMillis(),
    @JvmField var eventDate: Long = 0,
    @JvmField var createdAt: Long = System.currentTimeMillis(),
    @JvmField var updatedAt: Long = System.currentTimeMillis()
)
