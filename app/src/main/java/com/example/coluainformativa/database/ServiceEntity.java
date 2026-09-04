package com.example.coluainformativa.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "services")
public class ServiceEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String titulo;
    public String descripcion;
    public String footer;
    public String colorHex;
    public int orden;

    public ServiceEntity(String titulo, String descripcion, String footer, String colorHex, int orden) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.footer = footer;
        this.colorHex = colorHex;
        this.orden = orden;
    }
}