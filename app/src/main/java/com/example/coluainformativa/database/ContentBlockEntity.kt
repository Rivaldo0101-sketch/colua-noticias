package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = ContentItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["contentItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contentItemId")]
)
data class ContentBlockEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var contentItemId: String? = null, // Puede ser null si es un bloque de sección
    @JvmField var type: String = "TEXT", // TEXT, IMAGE, BUTTON, etc.
    @JvmField var content: String = "",
    @JvmField var displayOrder: Int = 0,
    @JvmField var mediaPath: String? = null,
    @JvmField var title: String? = null,
    @JvmField var buttonText: String? = null,
    @JvmField var buttonAction: String? = null,
    @JvmField var sectionId: String? = null, // Para bloques vinculados directamente a una sección
    @JvmField var backgroundColor: String? = null,
    @JvmField var textColor: String? = null,
    @JvmField var fontSize: String? = "NORMAL",
    @JvmField var fontWeight: String? = "NORMAL",
    @JvmField var alignment: String? = "LEFT"
)
