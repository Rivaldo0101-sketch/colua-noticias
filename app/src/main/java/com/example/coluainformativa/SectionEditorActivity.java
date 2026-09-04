package com.example.coluainformativa;

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
        setContentView(R.layout.activity_section_editor);

        repository = new ColuaRepository(this);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);

        etTitle = findViewById(R.id.et_section_title);
        etDesc = findViewById(R.id.et_section_desc);
        switchPublished = findViewById(R.id.switch_published);
        switchVisible = findViewById(R.id.switch_visible_nav);

        findViewById(R.id.btn_back_section_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_section).setOnClickListener(v -> saveSection());

        if (sectionId != null) {
            loadSection();
        } else {
            section = new SectionEntity(UUID.randomUUID().toString(), "", "", "", "ic_custom", "#173789", 0, true);
        }
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