package com.example.coluainformativa.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agencias")
data class AgenciaEntity @JvmOverloads constructor(
    @PrimaryKey
    @JvmField var id: String = "",
    @JvmField var nombre: String = "",
    @JvmField var departamento: String = "",
    @JvmField var direccion: String = "",
    @JvmField var telefono: String = "",
    @JvmField var colorHex: String = "#173789",
    @JvmField var tipo: String = "AGENCIA", // AGENCIA, AGENTE, CAJERO
    @JvmField var mapUrl: String = "",
    @JvmField var isVisible: Boolean = true,
    @JvmField var updatedAt: Long = System.currentTimeMillis()
)
