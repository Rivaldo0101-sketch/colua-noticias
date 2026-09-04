package com.example.coluainformativa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.repository.ColuaRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InstitutionalIdentityActivity extends AppCompatActivity {

    private ColuaRepository repository;
    private EditText etInstName, etInstSlogan, etLogoPath, etDistintivoPath, etHelpTitle, etHelpDesc;
    private LinearLayout layoutPbxContainer;

    private boolean isSelectingMainLogo = true;
    private ActivityResultLauncher<String> imagePickerLauncher;

    private static class PbxItem {
        String label;
        String number;
        PbxItem(String label, String number) {
            this.label = label;
            this.number = number;
        }
    }

    private final List<PbxItem> pbxList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_institutional_identity);

        repository = new ColuaRepository(this);

        etInstName = findViewById(R.id.et_inst_name);
        etInstSlogan = findViewById(R.id.et_inst_slogan);
        etLogoPath = findViewById(R.id.et_logo_path);
        etDistintivoPath = findViewById(R.id.et_distintivo_path);
        etHelpTitle = findViewById(R.id.et_help_title);
        etHelpDesc = findViewById(R.id.et_help_desc);
        layoutPbxContainer = findViewById(R.id.layout_pbx_container);

        findViewById(R.id.btn_back_identity).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_identity).setOnClickListener(v -> saveIdentity());
        findViewById(R.id.btn_save_identity_master).setOnClickListener(v -> saveIdentity());
        findViewById(R.id.btn_add_pbx).setOnClickListener(v -> addPbxRow("PBX Central", "7795-7795"));

        // Image Picker Launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        String path = uri.toString();
                        if (isSelectingMainLogo) {
                            etLogoPath.setText(path);
                            Toast.makeText(this, "Logo seleccionado de galería", Toast.LENGTH_SHORT).show();
                        } else {
                            etDistintivoPath.setText(path);
                            Toast.makeText(this, "Distintivo seleccionado de galería", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        findViewById(R.id.btn_pick_logo).setOnClickListener(v -> {
            isSelectingMainLogo = true;
            imagePickerLauncher.launch("image/*");
        });

        findViewById(R.id.btn_pick_distintivo).setOnClickListener(v -> {
            isSelectingMainLogo = false;
            imagePickerLauncher.launch("image/*");
        });

        loadIdentityData();
    }

    private void loadIdentityData() {
        new Thread(() -> {
            String name = repository.getGlobalConfig("nombre_institucional");
            String slogan = repository.getGlobalConfig("slogan_text");
            String logo = repository.getGlobalConfig("logo_path");
            String dist = repository.getGlobalConfig("distintivo_path");
            String helpTitle = repository.getGlobalConfig("help_title");
            String helpDesc = repository.getGlobalConfig("help_desc");
            String pbxJson = repository.getGlobalConfig("pbx_list_json");

            runOnUiThread(() -> {
                etInstName.setText(name.isEmpty() ? "COLUA MICOOPE" : name);
                etInstSlogan.setText(slogan.isEmpty() ? "SOMOS EL LADO HUMANO\nde los Ahorros y Créditos" : slogan);
                etLogoPath.setText(logo.isEmpty() ? "logo_composite" : logo);
                etDistintivoPath.setText(dist.isEmpty() ? "distintivo_colua" : dist);
                etHelpTitle.setText(helpTitle.isEmpty() ? "¿Necesitas ayuda adicionales?" : helpTitle);
                etHelpDesc.setText(helpDesc.isEmpty() ? "Comunícate a nuestro PBX central o búscanos en nuestras redes sociales oficiales." : helpDesc);

                layoutPbxContainer.removeAllViews();
                pbxList.clear();

                if (!pbxJson.isEmpty()) {
                    try {
                        JSONArray array = new JSONArray(pbxJson);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            addPbxRow(obj.optString("label", "PBX"), obj.optString("number", "7795-7795"));
                        }
                    } catch (Exception ignored) {}
                }

                if (pbxList.isEmpty()) {
                    addPbxRow("PBX Central", "7795-7795");
                }
            });
        }).start();
    }

    private void addPbxRow(String initialLabel, String initialNumber) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_pbx_row, layoutPbxContainer, false);
        EditText etLabel = row.findViewById(R.id.et_pbx_label);
        EditText etNumber = row.findViewById(R.id.et_pbx_number);
        ImageButton btnRemove = row.findViewById(R.id.btn_remove_pbx);

        etLabel.setText(initialLabel);
        etNumber.setText(initialNumber);

        btnRemove.setOnClickListener(v -> layoutPbxContainer.removeView(row));

        layoutPbxContainer.addView(row);
    }

    private void saveIdentity() {
        String name = etInstName.getText().toString().trim();
        String slogan = etInstSlogan.getText().toString().trim();
        String logo = etLogoPath.getText().toString().trim();
        String dist = etDistintivoPath.getText().toString().trim();
        String helpTitle = etHelpTitle.getText().toString().trim();
        String helpDesc = etHelpDesc.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "El nombre institucional es obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }

        // Construir JSON de PBXs
        JSONArray pbxArray = new JSONArray();
        for (int i = 0; i < layoutPbxContainer.getChildCount(); i++) {
            View row = layoutPbxContainer.getChildAt(i);
            EditText etLabel = row.findViewById(R.id.et_pbx_label);
            EditText etNumber = row.findViewById(R.id.et_pbx_number);

            if (etLabel != null && etNumber != null) {
                String label = etLabel.getText().toString().trim();
                String num = etNumber.getText().toString().trim();
                if (!num.isEmpty()) {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("label", label.isEmpty() ? "PBX" : label);
                        obj.put("number", num);
                        pbxArray.put(obj);
                    } catch (Exception ignored) {}
                }
            }
        }

        new Thread(() -> {
            repository.setGlobalConfig("nombre_institucional", name);
            repository.setGlobalConfig("slogan_text", slogan);
            repository.setGlobalConfig("logo_path", logo);
            repository.setGlobalConfig("distintivo_path", dist);
            repository.setGlobalConfig("help_title", helpTitle);
            repository.setGlobalConfig("help_desc", helpDesc);
            repository.setGlobalConfig("pbx_list_json", pbxArray.toString());

            // Marcar borrador con cambios pendientes
            getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_unpublished_changes", true)
                    .apply();

            runOnUiThread(() -> {
                Toast.makeText(this, "Identidad Institucional guardada en borrador local.", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
