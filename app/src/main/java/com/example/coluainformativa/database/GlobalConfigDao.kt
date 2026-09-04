package com.example.coluainformativa.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GlobalConfigDao {
    @Query("SELECT * FROM global_config WHERE `key` = :key LIMIT 1")
    fun getConfig(key: String): GlobalConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setConfig(config: GlobalConfigEntity)

    @Query("SELECT * FROM global_config")
    fun getAllConfigs(): List<GlobalConfigEntity>
}
