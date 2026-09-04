package com.example.coluainformativa;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.AgenciaEntity;
import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.repository.ColuaRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

public class AdminAgenciaEditActivity extends AppCompatActivity {

    public static final String EXTRA_AGENCIA_ID = "extra_agencia_id";

    private ColuaRepository repository;
    private String agenciaId = null;
    private AgenciaEntity agencia;
    
    private EditText etNombre, etDepto, etDireccion, etTelefono, etMapUrl;
    private ChipGroup cgTipo;
    private MaterialSwitch switchVisible;
    private MaterialButton btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_agencia_edit);

        repository = new ColuaRepository(this);
        agenciaId = getIntent().getStringExtra(EXTRA_AGENCIA_ID);

        etNombre = findViewById(R.id.et_agencia_nombre);
        etDepto = findViewById(R.id.et_agencia_depto);
        etDireccion = findViewById(R.id.et_agencia_direccion);
        etTelefono = findViewById(R.id.et_agencia_telefono);
        etMapUrl = findViewById(R.id.et_agencia_map_url);
        cgTipo = findViewById(R.id.cg_agencia_tipo);
        switchVisible = findViewById(R.id.switch_agencia_visible);
        btnDelete = findViewById(R.id.btn_delete_agencia);

        findViewById(R.id.btn_back_agencia_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_agencia).setOnClickListener(v -> saveAgencia());
        btnDelete.setOnClickListener(v -> confirmDelete());

        if (agenciaId != null && !agenciaId.isEmpty()) {
            btnDelete.setVisibility(android.view.View.VISIBLE);
            loadAgencia();
        }
    }

    private void confirmDelete() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setHint("Clave de Administrador");
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = 60;
        et.setLayoutParams(params);
        container.addView(et);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirmar Eliminación")
                .setMessage("Ingrese su clave para eliminar esta agencia:")
                .setView(container)
                .setPositiveButton("ELIMINAR", (d, w) -> {
                    if (repository.checkAdminPassword(et.getText().toString())) {
                        new Thread(() -> {
                            repository.deleteAgencia(agencia);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Agencia eliminada", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }).start();
                    } else {
                        Toast.makeText(this, "Clave incorrecta", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void loadAgencia() {
        new Thread(() -> {
            agencia = repository.getAgenciaById(agenciaId);
            runOnUiThread(() -> {
                if (agencia != null) {
                    etNombre.setText(agencia.nombre);
                    etDepto.setText(agencia.departamento);
                    etDireccion.setText(agencia.direccion);
                    etTelefono.setText(agencia.telefono);
                    etMapUrl.setText(agencia.mapUrl);
                    switchVisible.setChecked(agencia.isVisible);
                    
                    if ("AGENTE".equals(agencia.tipo)) {
                        ((Chip)findViewById(R.id.chip_tipo_agente)).setChecked(true);
                    } else if ("CAJERO".equals(agencia.tipo)) {
                        ((Chip)findViewById(R.id.chip_tipo_cajero)).setChecked(true);
                    } else {
                        ((Chip)findViewById(R.id.chip_tipo_agencia)).setChecked(true);
                    }
                }
            });
        }).start();
    }

    private void saveAgencia() {
        String nombre = etNombre.getText().toString();
        String depto = etDepto.getText().toString();
        String dir = etDireccion.getText().toString();
        String tel = etTelefono.getText().toString();
        String map = etMapUrl.getText().toString();
        
        if (nombre.isEmpty()) {
            Toast.makeText(this, "El nombre es obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }

        String tipo = "AGENCIA";
        int checkedId = cgTipo.getCheckedChipId();
        if (checkedId == R.id.chip_tipo_agente) tipo = "AGENTE";
        else if (checkedId == R.id.chip_tipo_cajero) tipo = "CAJERO";

        if (agencia == null) {
            agencia = new AgenciaEntity(java.util.UUID.randomUUID().toString(), nombre, depto, dir, tel, "#173789", tipo, map, true, System.currentTimeMillis());
        } else {
            agencia.nombre = nombre;
            agencia.departamento = depto;
            agencia.direccion = dir;
            agencia.telefono = tel;
            agencia.tipo = tipo;
            agencia.mapUrl = map;
        }
        
        agencia.isVisible = switchVisible.isChecked();
        agencia.updatedAt = System.currentTimeMillis();

        new Thread(() -> {
            repository.insertAgencias(agencia);
            runOnUiThread(() -> {
                Toast.makeText(this, "Agencia guardada exitosamente", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
