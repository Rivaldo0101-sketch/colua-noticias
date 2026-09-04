package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_config")
data class GlobalConfigEntity(
    @PrimaryKey
    @JvmField var key: String = "",
    @JvmField var value: String = ""
)
