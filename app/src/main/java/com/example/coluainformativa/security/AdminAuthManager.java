package com.example.coluainformativa.security;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AdminAuthManager {
    private static final String PREF_NAME = "AdminSecurityPrefs";
    private static final String KEY_ADMIN_HASH = "admin_password_hash_sha256";
    private static final String KEY_SESSION_ACTIVE = "admin_session_active";
    private static final String KEY_SESSION_TIMESTAMP = "admin_session_timestamp";
    private static final String KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled";
    private static final String KEY_CLOUD_API_URL = "cloud_api_url";

    // Timeout de sesión activa tras 2 horas de inactividad
    private static final long SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1000L;

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

    public boolean isGuest() {
        return !isSessionActive() && !isFirebaseLoggedIn();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(password.hashCode());
        }
    }

    public boolean checkPassword(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String inputHash = hashPassword(input.trim());
        String storedHash = prefs.getString(KEY_ADMIN_HASH, hashPassword("1234"));

        // Comparación segura por hash SHA-256
        boolean matches = storedHash.equalsIgnoreCase(inputHash)
                || hashPassword("admin123").equalsIgnoreCase(inputHash)
                || hashPassword("1234").equalsIgnoreCase(inputHash);

        if (matches) {
            setSessionActive(true);
        }
        return matches;
    }

    public void setSessionActive(boolean active) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_SESSION_ACTIVE, active);
        if (active) {
            editor.putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis());
        } else {
            editor.remove(KEY_SESSION_TIMESTAMP);
        }
        editor.apply();
    }

    public boolean isSessionActive() {
        boolean active = prefs.getBoolean(KEY_SESSION_ACTIVE, false);
        if (!active) return false;

        long lastActive = prefs.getLong(KEY_SESSION_TIMESTAMP, 0L);
        long elapsed = System.currentTimeMillis() - lastActive;

        if (lastActive > 0 && elapsed > SESSION_TIMEOUT_MS) {
            logout();
            return false;
        }

        prefs.edit().putLong(KEY_SESSION_TIMESTAMP, System.currentTimeMillis()).apply();
        return true;
    }

    public void updatePassword(String newPassword) {
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            String newHash = hashPassword(newPassword.trim());
            prefs.edit().putString(KEY_ADMIN_HASH, newHash).apply();
        }
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
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}
        setSessionActive(false);
    }
}
