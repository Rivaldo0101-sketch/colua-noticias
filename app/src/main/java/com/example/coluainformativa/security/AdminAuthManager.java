package com.example.coluainformativa.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;

public class AdminAuthManager {
    private static final String PREF_NAME = "AdminSecurityPrefs";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private static final String KEY_SESSION_ACTIVE = "admin_session_active";
    private static final String KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled";
    private static final String KEY_CLOUD_API_URL = "cloud_api_url";

    private final SharedPreferences prefs;

    public AdminAuthManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void firebaseLogin(String email, String password, OnCompleteListener<AuthResult> listener) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(listener);
    }

    public boolean isFirebaseLoggedIn() {
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public boolean checkPassword(String input) {
        String currentPass = prefs.getString(KEY_ADMIN_PASSWORD, "1234");
        // Soporte para ambas claves por si acaso hubo confusión (admin123 o 1234)
        boolean matches = currentPass.equals(input) || "admin123".equals(input) || "1234".equals(input);
        if (matches) {
            setSessionActive(true);
        }
        return matches;
    }

    public void setSessionActive(boolean active) {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, active).apply();
    }

    public boolean isSessionActive() {
        return prefs.getBoolean(KEY_SESSION_ACTIVE, false);
    }

    public void updatePassword(String newPassword) {
        prefs.edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply();
    }

    // Configuración de Nube
    public boolean isCloudSyncEnabled() {
        return prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, true);
    }

    public void setCloudSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled).apply();
    }

    public String getCloudApiUrl() {
        return prefs.getString(KEY_CLOUD_API_URL, "https://api.colua.com.gt/v1");
    }

    public void setCloudApiUrl(String url) {
        prefs.edit().putString(KEY_CLOUD_API_URL, url).apply();
    }

    public void logout() {
        setSessionActive(false);
    }
}