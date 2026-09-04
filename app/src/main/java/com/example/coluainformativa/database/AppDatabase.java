package com.example.coluainformativa.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {ServiceEntity.class, UserEntity.class, AgenciaEntity.class, 
        SectionEntity.class, CategoryEntity.class, ContentItemEntity.class, ContentBlockEntity.class,
        NavigationItemEntity.class, GlobalConfigEntity.class}, version = 16)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ServiceDao serviceDao();
    public abstract UserDao userDao();
    public abstract AgenciaDao agenciaDao();
    public abstract SectionDao sectionDao();
    public abstract ContentDao contentDao();
    public abstract NavigationDao navigationDao();
    public abstract GlobalConfigDao globalConfigDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "colua_database")
                            .fallbackToDestructiveMigration() // Permitido en esta fase de refactor inicial
                            .allowMainThreadQueries() 
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}