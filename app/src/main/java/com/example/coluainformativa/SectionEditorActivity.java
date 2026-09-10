package com.example.coluainformativa;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.UUID;

public class SectionEditorActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";

    private ColuaRepository repository;
    private String sectionId;
    private SectionEntity section;

    private EditText etTitle, etDesc;
    private MaterialSwitch switchPublished, switchVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirigir el editor legacy de secciones al editor unificado del Portal Administrativo
        Intent intent = new Intent(this, AdminSectionEditActivity.class);
        if (getIntent().hasExtra(EXTRA_SECTION_ID)) {
            intent.putExtra(AdminSectionEditActivity.EXTRA_SECTION_ID, getIntent().getStringExtra(EXTRA_SECTION_ID));
        }
        startActivity(intent);
        finish();
    }

    private void loadSection() {
        new Thread(() -> {
            section = repository.getAllSections().stream()
                    .filter(s -> s.id.equals(sectionId))
                    .findFirst().orElse(null);
            
            runOnUiThread(() -> {
                if (section != null) {
                    etTitle.setText(section.title);
                    etDesc.setText(section.description);
                    switchPublished.setChecked(section.isPublished);
                    switchVisible.setChecked(section.isVisible);
                }
            });
        }).start();
    }

    private void saveSection() {
        section.title = etTitle.getText().toString();
        section.description = etDesc.getText().toString();
        section.slug = section.title.toLowerCase().replace(" ", "_");
        section.isPublished = switchPublished.isChecked();
        section.isVisible = switchVisible.isChecked();
        section.updatedAt = System.currentTimeMillis();

        new Thread(() -> {
            repository.insertSection(section);
            runOnUiThread(() -> {
                Toast.makeText(this, "Página guardada", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}