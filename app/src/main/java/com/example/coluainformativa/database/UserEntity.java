package com.example.coluainformativa.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String identifier; // No. Cuenta o UID
    public String dpi; // Documento Personal de Identificación
    public String name;
    public String phone;
    public String role; // "GUEST", "MEMBER", "ADMIN"

    public UserEntity(String identifier, String dpi, String name, String phone, String role) {
        this.identifier = identifier;
        this.dpi = dpi;
        this.name = name;
        this.phone = phone;
        this.role = role;
    }
}