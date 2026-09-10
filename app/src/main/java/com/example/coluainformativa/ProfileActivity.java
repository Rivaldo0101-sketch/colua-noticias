package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import kotlin.Unit;

import kotlin.Unit;

public class ProfileActivity extends AppCompatActivity {

    private ColuaRepository repository;

    // Elementos de la Vista
    private TextView tvPageTitle, tvPageSubtitle, tvReadName, tvReadDpi, tvReadPhone, tvReadEmail, tvLastUpdated, tvSyncBadge;
    private ImageButton btnToggleDpiMask;
    private LinearLayout layoutReadMode, layoutEditMode, layoutSavingProgress;
    private MaterialButton btnEditProfileMode, btnCancelProfileEdit, btnSaveProfile;

    // Formulario de Edición
    private TextInputLayout tilName, tilDpi, tilPhone, tilEmail, tilOldPassword, tilNewPassword, tilConfirmPassword;
    private EditText etName, etDpi, etPhone, etEmail, etOldPassword, etNewPassword, etConfirmPassword;

    // Estado del Perfil
    private String currentName = "";
    private String currentDpi = "";
    private String currentPhone = "";
    private String currentEmail = "";
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
        tvReadEmail = findViewById(R.id.tv_read_email);
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
        tilEmail = findViewById(R.id.til_profile_email);
        tilOldPassword = findViewById(R.id.til_profile_old_password);
        tilNewPassword = findViewById(R.id.til_profile_new_password);
        tilConfirmPassword = findViewById(R.id.til_profile_confirm_password);

        etName = findViewById(R.id.et_profile_name);
        etDpi = findViewById(R.id.et_profile_dpi);
        etPhone = findViewById(R.id.et_profile_phone);
        etEmail = findViewById(R.id.et_profile_email);
        etOldPassword = findViewById(R.id.et_profile_old_password);
        etNewPassword = findViewById(R.id.et_profile_new_password);
        etConfirmPassword = findViewById(R.id.et_profile_confirm_password);

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
        currentEmail = pref.getString("user_email", "");
        currentRole = pref.getString("user_role", "GUEST");
        long lastUpdate = pref.getLong("last_profile_update", 0L);

        // Actualizar UI de Lectura
        tvReadName.setText(currentName.isEmpty() ? "Invitado" : currentName);
        tvReadPhone.setText(currentPhone.isEmpty() ? "No registrado" : currentPhone);
        tvReadEmail.setText(currentEmail.isEmpty() ? "No registrado" : currentEmail);
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
        etEmail.setText(currentEmail);
        
        etOldPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
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
        tilEmail.setError(null);
        tilOldPassword.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        etName.setText(currentName);
        etDpi.setText(currentDpi);
        etPhone.setText(currentPhone);
        etEmail.setText(currentEmail);
        etOldPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");

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
        tilEmail.setError(null);
        tilOldPassword.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);
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
                tilEmail.setError(null);
                tilOldPassword.setError(null);
                tilNewPassword.setError(null);
                tilConfirmPassword.setError(null);
                checkIfDataChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etName.addTextChangedListener(watcher);
        etDpi.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etOldPassword.addTextChangedListener(watcher);
        etNewPassword.addTextChangedListener(watcher);
        etConfirmPassword.addTextChangedListener(watcher);
    }

    private void checkIfDataChanged() {
        String nameInput = etName.getText().toString().trim();
        String dpiInput = etDpi.getText().toString().trim();
        String phoneInput = etPhone.getText().toString().trim();
        String emailInput = etEmail.getText().toString().trim();
        String oldPwdInput = etOldPassword.getText().toString().trim();
        String newPwdInput = etNewPassword.getText().toString().trim();

        boolean isChanged = !nameInput.equals(currentName) || !dpiInput.equals(currentDpi) || !phoneInput.equals(currentPhone) || !emailInput.equals(currentEmail) || !oldPwdInput.isEmpty() || !newPwdInput.isEmpty();
        btnSaveProfile.setEnabled(isChanged);
        btnSaveProfile.setAlpha(isChanged ? 1.0f : 0.5f);
    }

    private boolean validateInputs() {
        String name = etName.getText().toString().trim();
        String dpi = etDpi.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String newPwd = etNewPassword.getText().toString().trim();
        String confirmPwd = etConfirmPassword.getText().toString().trim();
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
            tilDpi.setError("El DPI debe tener exactamente 13 dígitos");
            isValid = false;
        }

        if (phone.isEmpty()) {
            tilPhone.setError("Ingresa tu número de teléfono");
            isValid = false;
        } else if (!phone.matches("\\d{8}")) {
            tilPhone.setError("El teléfono debe tener exactamente 8 dígitos");
            isValid = false;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Ingresa un correo electrónico válido");
            isValid = false;
        }

        if (!newPwd.isEmpty()) {
            if (!newPwd.equals(confirmPwd)) {
                tilConfirmPassword.setError("Las contraseñas no coinciden");
                isValid = false;
            } else if (newPwd.length() < 8) {
                tilNewPassword.setError("Mínimo 8 caracteres");
                isValid = false;
            }
        }

        return isValid;
    }

    private void saveProfileChanges() {
        if (!validateInputs()) return;

        String newName = etName.getText().toString().trim();
        String newDpi = etDpi.getText().toString().trim();
        String newPhone = etPhone.getText().toString().trim();
        String newEmail = etEmail.getText().toString().trim();
        String oldPwd = etOldPassword.getText().toString().trim();
        String newPwd = etNewPassword.getText().toString().trim();
        long now = System.currentTimeMillis();

        btnSaveProfile.setEnabled(false);
        btnCancelProfileEdit.setEnabled(false);
        layoutSavingProgress.setVisibility(View.VISIBLE);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Runnable updateFirestoreAndPrefs = () -> {
                SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                pref.edit()
                        .putString("user_name", newName)
                        .putString("user_dpi", newDpi)
                        .putString("user_phone", newPhone)
                        .putString("user_email", newEmail)
                        .putLong("last_profile_update", now)
                        .apply();

                repository.actualizarPerfil(newName, newPhone, newDpi, (success, errorMsg) -> {
                    runOnUiThread(() -> {
                        layoutSavingProgress.setVisibility(View.GONE);
                        btnSaveProfile.setEnabled(true);
                        btnCancelProfileEdit.setEnabled(true);

                        if (success) {
                            currentName = newName;
                            currentDpi = newDpi;
                            currentPhone = newPhone;
                            currentEmail = newEmail;
                            loadProfileData();
                            cancelEditMode();
                            Toast.makeText(ProfileActivity.this, "Datos y seguridad actualizados", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = errorMsg != null ? errorMsg : "Error desconocido";
                            new AlertDialog.Builder(ProfileActivity.this)
                                    .setTitle("Error al Guardar Perfil")
                                    .setMessage("No se pudieron guardar los cambios en la nube:\n\n" + msg)
                                    .setPositiveButton("Reintentar", (dialog, which) -> saveProfileChanges())
                                    .setNegativeButton("Cancelar", null)
                                    .show();
                        }
                    });
                    return Unit.INSTANCE;
                });
            };

            Runnable handleEmailAndUpdate = () -> {
                if (!newEmail.equals(user.getEmail()) && !newEmail.isEmpty()) {
                    user.updateEmail(newEmail).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            updateFirestoreAndPrefs.run();
                        } else {
                            layoutSavingProgress.setVisibility(View.GONE);
                            btnSaveProfile.setEnabled(true);
                            btnCancelProfileEdit.setEnabled(true);
                            Toast.makeText(ProfileActivity.this, "Error actualizando correo: " + (task.getException() != null ? task.getException().getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    updateFirestoreAndPrefs.run();
                }
            };

            if (!newPwd.isEmpty()) {
                if (!oldPwd.isEmpty() && user.getEmail() != null) {
                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPwd);
                    user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
                        if (reauthTask.isSuccessful()) {
                            user.updatePassword(newPwd).addOnCompleteListener(pwdTask -> {
                                if (pwdTask.isSuccessful()) {
                                    handleEmailAndUpdate.run();
                                } else {
                                    layoutSavingProgress.setVisibility(View.GONE);
                                    btnSaveProfile.setEnabled(true);
                                    btnCancelProfileEdit.setEnabled(true);
                                    Toast.makeText(ProfileActivity.this, "Error actualizando contraseña: " + (pwdTask.getException() != null ? pwdTask.getException().getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            layoutSavingProgress.setVisibility(View.GONE);
                            btnSaveProfile.setEnabled(true);
                            btnCancelProfileEdit.setEnabled(true);
                            Toast.makeText(ProfileActivity.this, "La contraseña antigua es incorrecta", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    user.updatePassword(newPwd).addOnCompleteListener(pwdTask -> {
                        if (pwdTask.isSuccessful()) {
                            handleEmailAndUpdate.run();
                        } else {
                            layoutSavingProgress.setVisibility(View.GONE);
                            btnSaveProfile.setEnabled(true);
                            btnCancelProfileEdit.setEnabled(true);
                            Toast.makeText(ProfileActivity.this, "Error configurando contraseña: " + (pwdTask.getException() != null ? pwdTask.getException().getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } else {
                handleEmailAndUpdate.run();
            }
        } else {
            layoutSavingProgress.setVisibility(View.GONE);
            btnSaveProfile.setEnabled(true);
            btnCancelProfileEdit.setEnabled(true);
            Toast.makeText(ProfileActivity.this, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
        }
    }
}
