package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.DialogHelper;
import com.example.coluainformativa.utils.DpiFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.security.SecureRandom;
import java.util.Locale;

import kotlin.Unit;

import android.widget.CheckBox;
import android.widget.ProgressBar;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilPhone, tilEmail, tilDpi, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etPhone, etDpi, etEmail, etPassword, etConfirmPassword;
    private TextInputEditText etEmailLogin, etPasswordLogin;
    
    private TextView tvRuleLength, tvRuleCases, tvRuleNumberSpecial, tvStepTitle;
    private Button btnLoginSubmit, btnRegisterSubmit, btnNextStep, btnPrevStep, btnGuestLogin, btnGuestReg1;
    private Button btnSuggestPassword, btnForgotPassword;
    
    private CheckBox cbTerms, cbRememberLogin;
    private MaterialButtonToggleGroup toggleLoginMode;
    private ColuaRepository repository;
    private FirebaseAuth mAuth;

    private boolean isRegisterMode = false;
    private int currentStep = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repository = new ColuaRepository(this);
        mAuth = FirebaseAuth.getInstance();

        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (pref.contains("user_name") && mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Referencias - Registrarse
        tilName = findViewById(R.id.til_name);
        tilPhone = findViewById(R.id.til_phone);
        tilEmail = findViewById(R.id.til_email);
        tilDpi = findViewById(R.id.til_dpi);
        tilPassword = findViewById(R.id.til_password);
        tilConfirmPassword = findViewById(R.id.til_confirm_password);

        etName = findViewById(R.id.et_name);
        etDpi = findViewById(R.id.et_dpi);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        cbTerms = findViewById(R.id.cb_terms);
        
        btnNextStep = findViewById(R.id.btn_next_step);
        btnPrevStep = findViewById(R.id.btn_prev_step);
        btnRegisterSubmit = findViewById(R.id.btn_register_submit);
        btnGuestReg1 = findViewById(R.id.btn_guest_reg1);
        btnSuggestPassword = findViewById(R.id.btn_suggest_password);
        tvStepTitle = findViewById(R.id.tv_step_title);

        tvRuleLength = findViewById(R.id.tv_rule_length);
        tvRuleCases = findViewById(R.id.tv_rule_cases);
        tvRuleNumberSpecial = findViewById(R.id.tv_rule_number_special);
        
        // Referencias - Iniciar Sesión
        etEmailLogin = findViewById(R.id.et_email_login);
        etPasswordLogin = findViewById(R.id.et_password_login);
        cbRememberLogin = findViewById(R.id.cb_remember_login);
        btnLoginSubmit = findViewById(R.id.btn_login_submit);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);
        btnGuestLogin = findViewById(R.id.btn_guest_login);

        toggleLoginMode = findViewById(R.id.toggle_login_mode);

        DpiFormatter.applyDpiFormatting(etDpi);

        if (toggleLoginMode != null) {
            toggleLoginMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btn_mode_register) {
                    setRegisterMode(true);
                } else if (checkedId == R.id.btn_mode_login) {
                    setRegisterMode(false);
                }
            });
        }

        if (etPassword != null) {
            etPassword.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updatePasswordRules(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnSuggestPassword != null) {
            btnSuggestPassword.setOnClickListener(v -> {
                String suggested = generateStrongPassword();
                if (etPassword != null) {
                    etPassword.setText(suggested);
                    if (etConfirmPassword != null) etConfirmPassword.setText(suggested);
                    Toast.makeText(this, "💡 Contraseña segura sugerida e ingresada", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnForgotPassword != null) {
            btnForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        // Acciones Login
        if (btnLoginSubmit != null) btnLoginSubmit.setOnClickListener(v -> handleAuthAction());
        if (btnGuestLogin != null) btnGuestLogin.setOnClickListener(v -> continueAsGuest());

        // Acciones Register
        if (btnNextStep != null) btnNextStep.setOnClickListener(v -> handleNextStep());
        if (btnPrevStep != null) btnPrevStep.setOnClickListener(v -> setRegisterStep(1));
        if (btnRegisterSubmit != null) btnRegisterSubmit.setOnClickListener(v -> handleAuthAction());
        if (btnGuestReg1 != null) btnGuestReg1.setOnClickListener(v -> continueAsGuest());

        if (toggleLoginMode != null) {
            toggleLoginMode.check(R.id.btn_mode_login);
        }
        setRegisterMode(false);
    }

    private void handleNextStep() {
        String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
        String dpi = etDpi != null && etDpi.getText() != null ? etDpi.getText().toString().trim() : "";
        String phone = etPhone != null && etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        
        if (name.isEmpty() || dpi.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Por favor completa Nombre, DPI y Teléfono para continuar", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!DpiFormatter.isValidDpi(dpi)) {
            Toast.makeText(this, "El DPI debe tener exactamente 13 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.length() != 8) {
            Toast.makeText(this, "El teléfono debe tener 8 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setRegisterStep(2);
    }

    private void setRegisterStep(int step) {
        currentStep = step;
        View step1 = findViewById(R.id.layout_step_1);
        View step2 = findViewById(R.id.layout_step_2);
        ProgressBar prog = findViewById(R.id.progress_register);
        
        if (step == 1) {
            if (step1 != null) step1.setVisibility(View.VISIBLE);
            if (step2 != null) step2.setVisibility(View.GONE);
            if (prog != null) prog.setProgress(1);
            if (tvStepTitle != null) tvStepTitle.setText("1/2: Datos del usuario");
        } else {
            if (step1 != null) step1.setVisibility(View.GONE);
            if (step2 != null) step2.setVisibility(View.VISIBLE);
            if (prog != null) prog.setProgress(2);
            if (tvStepTitle != null) tvStepTitle.setText("2/2: Seguridad");
        }
    }

    private void setRegisterMode(boolean isRegister) {
        this.isRegisterMode = isRegister;
        
        View layoutLogin = findViewById(R.id.layout_login);
        View layoutRegister = findViewById(R.id.layout_register);
        
        if (layoutLogin != null) layoutLogin.setVisibility(isRegister ? View.GONE : View.VISIBLE);
        if (layoutRegister != null) layoutRegister.setVisibility(isRegister ? View.VISIBLE : View.GONE);
        
        Button btnLoginToggle = findViewById(R.id.btn_mode_login);
        Button btnRegisterToggle = findViewById(R.id.btn_mode_register);

        if (isRegister) {
            if (btnLoginToggle != null) {
                btnLoginToggle.setTextColor(Color.parseColor("#64748B"));
                btnLoginToggle.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            }
            if (btnRegisterToggle != null) {
                btnRegisterToggle.setTextColor(Color.parseColor("#FFFFFF"));
                btnRegisterToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#475569")));
            }
            setRegisterStep(1);
        } else {
            if (btnLoginToggle != null) {
                btnLoginToggle.setTextColor(Color.parseColor("#1E293B"));
                btnLoginToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
            }
            if (btnRegisterToggle != null) {
                btnRegisterToggle.setTextColor(Color.parseColor("#64748B"));
                btnRegisterToggle.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            }
        }
    }

    private void updatePasswordRules(String password) {
        boolean len = password != null && password.length() >= 8;
        boolean cases = false;
        boolean numSpec = false;

        if (password != null) {
            boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
            for (char c : password.toCharArray()) {
                if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isDigit(c)) hasDigit = true;
                else if (!Character.isWhitespace(c)) hasSpecial = true;
            }
            cases = hasUpper && hasLower;
            numSpec = hasDigit && hasSpecial;
        }

        if (tvRuleLength != null) {
            tvRuleLength.setText(len ? "✓ Mínimo 8 caracteres" : "✖ Mínimo 8 caracteres");
            tvRuleLength.setTextColor(Color.parseColor(len ? "#16A34A" : "#94A3B8"));
        }

        if (tvRuleCases != null) {
            tvRuleCases.setText(cases ? "✓ Mayúsculas (A-Z) y minúsculas (a-z)" : "✖ Mayúsculas (A-Z) y minúsculas (a-z)");
            tvRuleCases.setTextColor(Color.parseColor(cases ? "#16A34A" : "#94A3B8"));
        }

        if (tvRuleNumberSpecial != null) {
            tvRuleNumberSpecial.setText(numSpec ? "✓ Números (0-9) y símbolos (!@#$%...)" : "✖ Números (0-9) y símbolos (!@#$%...)");
            tvRuleNumberSpecial.setTextColor(Color.parseColor(numSpec ? "#16A34A" : "#94A3B8"));
        }
    }

    private String generateStrongPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String specials = "!@#$%^&*()_+-=";

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("Colua");
        sb.append(specials.charAt(random.nextInt(specials.length())));
        sb.append(100 + random.nextInt(900));
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(specials.charAt(random.nextInt(specials.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));

        return sb.toString();
    }

    private boolean validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c)) hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void handleAuthAction() {
        String name = etName != null && etName.getText() != null ? etName.getText().toString().trim() : "";
        String dpi = etDpi != null && etDpi.getText() != null ? etDpi.getText().toString().trim() : "";
        String phone = etPhone != null && etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        
        String email, password, confirmPassword;

        if (isRegisterMode) {
            email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            password = etPassword != null && etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            confirmPassword = etConfirmPassword != null && etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cbTerms != null && !cbTerms.isChecked()) {
                Toast.makeText(this, "Debes aceptar los términos y condiciones", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!validatePasswordPolicy(password)) {
                Toast.makeText(this, "La contraseña no cumple las reglas de seguridad. Usa la sugerencia 💡", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            email = etEmailLogin != null && etEmailLogin.getText() != null ? etEmailLogin.getText().toString().trim() : "";
            password = etPasswordLogin != null && etPasswordLogin.getText() != null ? etPasswordLogin.getText().toString().trim() : "";

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa tu Correo Electrónico y Contraseña", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Ingresa un correo electrónico válido (ej. usuario@ejemplo.com)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(false);
        if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(false);

        String actionStr = isRegisterMode ? "Registrando usuario..." : "Verificando credenciales...";
        Toast.makeText(this, actionStr, Toast.LENGTH_SHORT).show();

        if (isRegisterMode) {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                            String uid = task.getResult().getUser().getUid();
                            repository.crearPerfilUsuarioFirestore(uid, name, phone, dpi, email, false, (success, userId, errorMsg) -> {
                                runOnUiThread(() -> {
                                    if (success) {
                                        Toast.makeText(LoginActivity.this, "¡Registro exitoso! ID: " + userId, Toast.LENGTH_SHORT).show();
                                        saveUserSession(userId, name, phone, email, "MEMBER");
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        finish();
                                    } else {
                                        if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(true);
                                        DialogHelper.showLightReportDialog(LoginActivity.this, "Error de Registro", "No pudimos guardar tu perfil:\n\n" + errorMsg, "Entendido", null, null);
                                    }
                                });
                                return Unit.INSTANCE;
                            });
                        } else {
                            if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(true);
                            String friendlyMsg = getFriendlyErrorMessage(task.getException());
                            DialogHelper.showLightReportDialog(LoginActivity.this, "No se pudo completar el registro", friendlyMsg, "Entendido", null, null);
                        }
                    });
        } else {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                            String uid = task.getResult().getUser().getUid();
                            repository.obtenerPerfilUsuarioFirestore(uid, email, (success, userMap, errorMsg) -> {
                                runOnUiThread(() -> {
                                    if (success && userMap != null) {
                                        String userId = userMap.get("userId");
                                        String storedName = userMap.get("nombre");
                                        String storedPhone = userMap.get("telefono");
                                        String storedRole = userMap.get("role");
                                        saveUserSession(userId, storedName, storedPhone, email, storedRole);
                                        
                                        Toast.makeText(LoginActivity.this, "¡Inicio de sesión correcto! ID: " + userId, Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        finish();
                                    } else {
                                        if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(true);
                                        DialogHelper.showLightReportDialog(LoginActivity.this, "Perfil no encontrado", "No se encontró un perfil asociado a esta cuenta en la base de datos.", "Entendido", null, null);
                                    }
                                });
                                return Unit.INSTANCE;
                            });
                        } else {
                            if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(true);
                            String friendlyMsg = getFriendlyErrorMessage(task.getException());
                            DialogHelper.showLightReportDialog(LoginActivity.this, "Error de Acceso", friendlyMsg, "Entendido", null, null);
                        }
                    });
        }
    }

    private String getFriendlyErrorMessage(Exception e) {
        if (e == null) return "Ocurrió un error inesperado. Por favor intenta de nuevo.";
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("sign-in provider is disabled") || msg.contains("OPERATION_NOT_ALLOWED")) {
            return "El servicio de registro e inicio de sesión no se encuentra disponible temporalmente. Por favor intenta más tarde o ingresa como invitado.";
        } else if (msg.contains("email-already-in-use") || msg.contains("already in use") || msg.contains("in use by another account")) {
            return "Este correo electrónico ya está registrado en el sistema. Por favor utiliza un correo diferente o inicia sesión con tu cuenta existente.";
        } else if (msg.contains("invalid-email")) {
            return "El formato del correo electrónico ingresado no es válido.";
        } else if (msg.contains("weak-password")) {
            return "La contraseña es muy débil. Asegúrate de cumplir con los requisitos de seguridad.";
        } else if (msg.contains("wrong-password") || msg.contains("invalid-credential") || msg.contains("user-not-found")) {
            return "Correo electrónico o contraseña incorrectos. Si aún no tienes cuenta, por favor regístrate.";
        } else if (msg.contains("network-request-failed")) {
            return "Sin conexión a internet. Verifica tu red e intenta nuevamente.";
        }
        return "No pudimos procesar tu solicitud en este momento. Por favor verifica tu conexión a internet o intenta más tarde.";
    }

    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        EditText etResetEmail = dialogView.findViewById(R.id.et_reset_email);
        Button btnSendReset = dialogView.findViewById(R.id.btn_send_reset_email);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        String currentEmail = etEmailLogin != null && etEmailLogin.getText() != null ? etEmailLogin.getText().toString().trim() : "";
        if (etResetEmail != null && !currentEmail.isEmpty()) {
            etResetEmail.setText(currentEmail);
        }

        if (btnSendReset != null) {
            btnSendReset.setOnClickListener(v -> {
                String email = etResetEmail != null && etResetEmail.getText() != null ? etResetEmail.getText().toString().trim() : "";
                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Ingresa un correo electrónico válido", Toast.LENGTH_SHORT).show();
                    return;
                }

                dialog.dismiss();
                Toast.makeText(this, "Enviando solicitud de recuperación...", Toast.LENGTH_SHORT).show();

                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            new AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("✓ Recuperación Enviada")
                                    .setMessage("Se ha procesado la solicitud de recuperación para el correo:\n\n" + email + "\n\nPor favor revisa tu bandeja de entrada o spam para restablecer tu contraseña.")
                                    .setPositiveButton("Entendido", null)
                                    .show();
                        });
            });
        }

        dialog.show();
    }

    private void continueAsGuest() {
        if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(false);
        if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(false);
        if (btnGuestLogin != null) btnGuestLogin.setEnabled(false);
        if (btnGuestReg1 != null) btnGuestReg1.setEnabled(false);
        Toast.makeText(this, "Ingresando como invitado...", Toast.LENGTH_SHORT).show();

        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                String uid = task.getResult().getUser().getUid();
                repository.crearPerfilUsuarioFirestore(uid, "Invitado", "", "", "", true, (success, userId, errorMsg) -> {
                    runOnUiThread(() -> {
                        if (success) {
                            saveUserSession(uid, "Invitado", "", "", "GUEST");
                            Toast.makeText(LoginActivity.this, "¡Ingreso exitoso!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(true);
                            if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(true);
                            if (btnGuestLogin != null) btnGuestLogin.setEnabled(true);
                            if (btnGuestReg1 != null) btnGuestReg1.setEnabled(true);
                            DialogHelper.showLightReportDialog(LoginActivity.this, "Acceso no disponible", "No pudimos conectar con los servicios en este momento. Por favor verifica tu conexión a internet o intenta más tarde.", "Reintentar", () -> continueAsGuest(), "Cancelar");
                        }
                    });
                    return Unit.INSTANCE;
                });
            } else {
                if (btnLoginSubmit != null) btnLoginSubmit.setEnabled(true);
                if (btnRegisterSubmit != null) btnRegisterSubmit.setEnabled(true);
                if (btnGuestLogin != null) btnGuestLogin.setEnabled(true);
                if (btnGuestReg1 != null) btnGuestReg1.setEnabled(true);
                DialogHelper.showLightReportDialog(LoginActivity.this, "Acceso no disponible", "No pudimos conectar con los servicios en este momento. Por favor verifica tu conexión a internet o intenta más tarde.", "Reintentar", () -> continueAsGuest(), "Cancelar");
            }
        });
    }

    private void saveUserSession(String id, String name, String phone, String email, String role) {
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        pref.edit()
                .putString("user_id", id)
                .putString("user_name", name)
                .putString("user_phone", phone)
                .putString("user_email", email)
                .putString("user_role", role)
                .apply();
    }
}
