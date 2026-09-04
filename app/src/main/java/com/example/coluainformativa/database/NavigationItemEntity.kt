package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "navigation_items")
data class NavigationItemEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var label: String = "",
    @JvmField var iconName: String = "",
    @JvmField var targetSectionId: String = "",
    @JvmField var type: String = "BOTTOM_NAV", // BOTTOM_NAV, SIDEBAR, NAVBAR
    @JvmField var displayOrder: Int = 0,
    @JvmField var isVisible: Boolean = true
)
