package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.DpiFormatter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import kotlin.Unit;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etName, etPhone, etDpi;
    private Button btnLogin, btnGuest;
    private ColuaRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        repository = new ColuaRepository(this);
        
        // Asegurar autenticación anónima para permitir escritura en Firestore
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                .addOnFailureListener(e -> Log.e("FIREBASE_AUTH", "Auth anónima fallida en inicio: " + e.getMessage()));
        }
        
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (pref.contains("user_name")) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etName = findViewById(R.id.et_name);
        etDpi = findViewById(R.id.et_dpi);
        etPhone = findViewById(R.id.et_phone);
        
        DpiFormatter.applyDpiFormatting(etDpi);

        btnLogin = findViewById(R.id.btn_login);
        btnGuest = findViewById(R.id.btn_guest);

        btnLogin.setOnClickListener(v -> login());
        btnGuest.setOnClickListener(v -> continueAsGuest());
    }

    private void login() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String dpi = etDpi.getText() != null ? etDpi.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        if (name.isEmpty() || dpi.isEmpty()) {
            Toast.makeText(this, "Nombre y DPI son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!DpiFormatter.isValidDpi(dpi)) {
            Toast.makeText(this, "El DPI de Guatemala debe tener exactamente 13 dígitos (XXXX XXXXX XXXX)", Toast.LENGTH_LONG).show();
            return;
        }

        // Desactivar botones para evitar dobles registros
        btnLogin.setEnabled(false);
        btnGuest.setEnabled(false);
        Toast.makeText(this, "Registrando usuario en la nube...", Toast.LENGTH_SHORT).show();
        Log.i("LOGIN_FLOW", "Iniciando registro para: " + name + ", DPI: " + dpi);

        repository.registrarUsuarioReal(name, phone, dpi, false, (success, userId, errorMsg) -> {
            runOnUiThread(() -> {
                if (success && !userId.isEmpty()) {
                    Log.i("LOGIN_FLOW", "Registro exitoso en Firestore. UserId asignado: " + userId);
                    saveUserSession(userId, name, phone, "MEMBER");
                    Toast.makeText(this, "¡Registro exitoso (" + userId + ")!", Toast.LENGTH_SHORT).show();
                    
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    Log.e("LOGIN_FLOW", "Error en registro Firestore: " + errorMsg);
                    btnLogin.setEnabled(true);
                    btnGuest.setEnabled(true);

                    String msg = errorMsg != null ? errorMsg : "Error desconocido";
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Error de Registro")
                            .setMessage("No se pudo registrar el usuario en Firestore:\n\n" + msg + "\n\nPor favor verifique su conexión e intente de nuevo.")
                            .setPositiveButton("Reintentar", (dialog, which) -> login())
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
            return Unit.INSTANCE;
        });
    }

    private void continueAsGuest() {
        btnLogin.setEnabled(false);
        btnGuest.setEnabled(false);
        Toast.makeText(this, "Registrando invitado...", Toast.LENGTH_SHORT).show();
        Log.i("LOGIN_FLOW", "Iniciando registro de invitado");

        repository.registrarUsuarioReal("Invitado", "", "", true, (success, userId, errorMsg) -> {
            runOnUiThread(() -> {
                if (success && !userId.isEmpty()) {
                    Log.i("LOGIN_FLOW", "Invitado registrado exitosamente. UserId: " + userId);
                    saveUserSession(userId, "Invitado", "", "GUEST");
                    Toast.makeText(this, "¡Ingreso como invitado exitoso!", Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    Log.e("LOGIN_FLOW", "Error registrando invitado: " + errorMsg);
                    btnLogin.setEnabled(true);
                    btnGuest.setEnabled(true);

                    String msg = errorMsg != null ? errorMsg : "Error desconocido";
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Error de Ingreso")
                            .setMessage("No se pudo registrar como invitado:\n\n" + msg)
                            .setPositiveButton("Reintentar", (dialog, which) -> continueAsGuest())
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
            return Unit.INSTANCE;
        });
    }

    private void saveUserSession(String id, String name, String phone, String role) {
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        pref.edit()
                .putString("user_id", id)
                .putString("user_name", name)
                .putString("user_phone", phone)
                .putString("user_role", role)
                .apply();
    }
}
