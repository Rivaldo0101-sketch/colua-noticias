package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.DpiFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import kotlin.Unit;

import kotlin.Unit;

public class ProfileActivity extends AppCompatActivity {

    private ColuaRepository repository;

    // Elementos de la Vista
    private TextView tvPageTitle, tvPageSubtitle, tvReadName, tvReadDpi, tvReadPhone, tvLastUpdated, tvSyncBadge;
    private ImageButton btnToggleDpiMask;
    private LinearLayout layoutReadMode, layoutEditMode, layoutSavingProgress;
    private MaterialButton btnEditProfileMode, btnCancelProfileEdit, btnSaveProfile;

    // Formulario de Edición
    private TextInputLayout tilName, tilDpi, tilPhone;
    private EditText etName, etDpi, etPhone;

    // Estado del Perfil
    private String currentName = "";
    private String currentDpi = "";
    private String currentPhone = "";
    private String currentRole = "GUEST";
    private boolean isDpiMasked = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        repository = new ColuaRepository(this);

        // Referencias de Vistas
        tvPageTitle = findViewById(R.id.tv_profile_page_title);
        tvPageSubtitle = findViewById(R.id.tv_profile_page_subtitle);
        tvReadName = findViewById(R.id.tv_read_name);
        tvReadDpi = findViewById(R.id.tv_read_dpi);
        tvReadPhone = findViewById(R.id.tv_read_phone);
        tvLastUpdated = findViewById(R.id.tv_profile_last_updated);
        tvSyncBadge = findViewById(R.id.tv_profile_sync_badge);

        btnToggleDpiMask = findViewById(R.id.btn_toggle_dpi_mask);
        layoutReadMode = findViewById(R.id.layout_read_mode);
        layoutEditMode = findViewById(R.id.layout_edit_mode);
        layoutSavingProgress = findViewById(R.id.layout_saving_progress);

        btnEditProfileMode = findViewById(R.id.btn_edit_profile_mode);
        btnCancelProfileEdit = findViewById(R.id.btn_cancel_profile_edit);
        btnSaveProfile = findViewById(R.id.btn_save_profile);

        tilName = findViewById(R.id.til_profile_name);
        tilDpi = findViewById(R.id.til_profile_dpi);
        tilPhone = findViewById(R.id.til_profile_phone);

        etName = findViewById(R.id.et_profile_name);
        etDpi = findViewById(R.id.et_profile_dpi);
        etPhone = findViewById(R.id.et_profile_phone);

        DpiFormatter.applyDpiFormatting(etDpi);

        // Handlers de Eventos
        findViewById(R.id.btn_back_profile).setOnClickListener(v -> finish());
        btnToggleDpiMask.setOnClickListener(v -> toggleDpiMask());
        btnEditProfileMode.setOnClickListener(v -> enterEditMode());
        btnCancelProfileEdit.setOnClickListener(v -> cancelEditMode());
        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());

        setupChangeDetection();
        loadProfileData();
    }

    private void loadProfileData() {
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentName = pref.getString("user_name", "Asociado COLUA");
        String storedDpi = pref.getString("user_dpi", "");
        String userId = pref.getString("user_id", "");
        if (storedDpi.isEmpty() || storedDpi.equals(userId) || storedDpi.startsWith("user")) {
            currentDpi = "";
        } else {
            currentDpi = storedDpi;
        }
        currentPhone = pref.getString("user_phone", "");
        currentRole = pref.getString("user_role", "GUEST");
        long lastUpdate = pref.getLong("last_profile_update", 0L);

        // Actualizar UI de Lectura
        tvReadName.setText(currentName.isEmpty() ? "Invitado" : currentName);
        tvReadPhone.setText(currentPhone.isEmpty() ? "No registrado" : currentPhone);
        updateDpiDisplay();

        if (lastUpdate > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            tvLastUpdated.setText("Última actualización: " + sdf.format(new Date(lastUpdate)));
        } else {
            tvLastUpdated.setText("Última actualización: Reciente");
        }

        // Cargar en campos editables
        etName.setText(currentName);
        etDpi.setText(currentDpi);
        etPhone.setText(currentPhone);
    }

    private void updateDpiDisplay() {
        if (currentDpi == null || currentDpi.isEmpty() || "GUEST".equalsIgnoreCase(currentDpi)) {
            tvReadDpi.setText("Invitado (Sin DPI)");
            btnToggleDpiMask.setVisibility(View.GONE);
            return;
        }

        btnToggleDpiMask.setVisibility(View.VISIBLE);
        if (isDpiMasked) {
            if (currentDpi.length() > 4) {
                String visibleSuffix = currentDpi.substring(currentDpi.length() - 4);
                String maskedPrefix = "********".substring(0, Math.min(8, currentDpi.length() - 4));
                tvReadDpi.setText(maskedPrefix + visibleSuffix);
            } else {
                tvReadDpi.setText("****" + currentDpi);
            }
        } else {
            tvReadDpi.setText(currentDpi);
        }
    }

    private void toggleDpiMask() {
        isDpiMasked = !isDpiMasked;
        updateDpiDisplay();
    }

    private void enterEditMode() {
        tvPageTitle.setText("Editar mi perfil");
        tvPageSubtitle.setText("Modifica tus datos");
        layoutReadMode.setVisibility(View.GONE);
        layoutEditMode.setVisibility(View.VISIBLE);

        tilName.setError(null);
        tilDpi.setError(null);
        tilPhone.setError(null);

        etName.setText(currentName);
        etDpi.setText(currentDpi);
        etPhone.setText(currentPhone);

        checkIfDataChanged();
    }

    private void cancelEditMode() {
        tvPageTitle.setText("Mi perfil");
        tvPageSubtitle.setText("Mantén tus datos actualizados");
        layoutEditMode.setVisibility(View.GONE);
        layoutReadMode.setVisibility(View.VISIBLE);
        tilName.setError(null);
        tilDpi.setError(null);
        tilPhone.setError(null);
    }

    private void setupChangeDetection() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilName.setError(null);
                tilDpi.setError(null);
                tilPhone.setError(null);
                checkIfDataChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(watcher);
        etDpi.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
    }

    private void checkIfDataChanged() {
        String nameInput = etName.getText().toString().trim();
        String dpiInput = etDpi.getText().toString().trim();
        String phoneInput = etPhone.getText().toString().trim();

        boolean isChanged = !nameInput.equals(currentName) || !dpiInput.equals(currentDpi) || !phoneInput.equals(currentPhone);
        btnSaveProfile.setEnabled(isChanged);
        btnSaveProfile.setAlpha(isChanged ? 1.0f : 0.5f);
    }

    private boolean validateInputs() {
        String name = etName.getText().toString().trim();
        String dpi = etDpi.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        boolean isValid = true;

        if (name.isEmpty()) {
            tilName.setError("Ingresa tu nombre completo");
            isValid = false;
        } else if (name.length() < 3) {
            tilName.setError("El nombre debe tener al menos 3 caracteres");
            isValid = false;
        }

        if (dpi.isEmpty()) {
            tilDpi.setError("Ingresa tu DPI");
            isValid = false;
        } else if (!DpiFormatter.isValidDpi(dpi)) {
            tilDpi.setError("El DPI debe tener exactamente 13 dígitos (XXXX XXXXX XXXX)");
            isValid = false;
        }

        if (phone.isEmpty()) {
            tilPhone.setError("Ingresa tu número de teléfono");
            isValid = false;
        } else if (!phone.matches("\\d{8}")) {
            tilPhone.setError("El teléfono de Guatemala debe tener exactamente 8 dígitos");
            isValid = false;
        }

        return isValid;
    }

    private void saveProfileChanges() {
        if (!validateInputs()) return;

        String newName = etName.getText().toString().trim();
        String newDpi = etDpi.getText().toString().trim();
        String newPhone = etPhone.getText().toString().trim();
        long now = System.currentTimeMillis();

        // Deshabilitar botones y mostrar la pantalla de carga
        btnSaveProfile.setEnabled(false);
        btnCancelProfileEdit.setEnabled(false);
        layoutSavingProgress.setVisibility(View.VISIBLE);

        // Actualizar SharedPreferences
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        pref.edit()
                .putString("user_name", newName)
                .putString("user_dpi", newDpi)
                .putString("user_phone", newPhone)
                .putLong("last_profile_update", now)
                .apply();

        // Directamente actualizar el perfil del usuario activo en Firestore
        repository.actualizarPerfil(newName, newPhone, newDpi, (success, errorMsg) -> {
            runOnUiThread(() -> {
                // Ocultar carga y habilitar botones en ambos casos (éxito o error)
                layoutSavingProgress.setVisibility(View.GONE);
                btnSaveProfile.setEnabled(true);
                btnCancelProfileEdit.setEnabled(true);

                if (success) {
                    currentName = newName;
                    currentDpi = newDpi;
                    currentPhone = newPhone;
                    loadProfileData();
                    cancelEditMode();
                    Toast.makeText(ProfileActivity.this, "Datos actualizados", Toast.LENGTH_SHORT).show();
                } else {
                    String msg = errorMsg != null ? errorMsg : "Error desconocido";
                    new AlertDialog.Builder(ProfileActivity.this)
                            .setTitle("Error al Guardar Perfil")
                            .setMessage("No se pudieron guardar los cambios en la nube:\n\n" + msg + "\n\nPor favor verifique su conexión e intente de nuevo.")
                            .setPositiveButton("Reintentar", (dialog, which) -> saveProfileChanges())
                            .setNegativeButton("Cancelar", null)
                            .show();
                }
            });
            return Unit.INSTANCE;
        });
    }
}
