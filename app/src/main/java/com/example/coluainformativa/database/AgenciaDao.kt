package com.example.coluainformativa.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.List

@Dao
interface AgenciaDao {
    @Query("SELECT * FROM agencias WHERE departamento = :depto")
    fun getAgenciasByDepto(depto: String): MutableList<AgenciaEntity>

    @Query("SELECT * FROM agencias ORDER BY departamento")
    fun getAllAgencias(): MutableList<AgenciaEntity>

    @Query("SELECT * FROM agencias WHERE nombre LIKE '%' || :query || '%' OR departamento LIKE '%' || :query || '%'")
    fun searchAgencias(query: String): MutableList<AgenciaEntity>

    @Query("SELECT DISTINCT departamento FROM agencias")
    fun getAllDepartments(): MutableList<String>

    @Query("SELECT DISTINCT tipo FROM agencias")
    fun getAllTypes(): MutableList<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg agencias: AgenciaEntity)

    @Delete
    fun delete(agencia: AgenciaEntity)

    @Query("SELECT * FROM agencias WHERE id = :id")
    fun getAgenciaById(id: String): AgenciaEntity?

    @Query("DELETE FROM agencias")
    fun deleteAll()
}
