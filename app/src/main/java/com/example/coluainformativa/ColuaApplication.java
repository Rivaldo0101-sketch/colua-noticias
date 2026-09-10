package com.example.coluainformativa;

import android.app.Application;
import com.example.coluainformativa.database.DataSeeder;

public class ColuaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DataSeeder.resetUsersAndForceLogin(this);
        DataSeeder.seedIfEmpty(this);
    }
}
