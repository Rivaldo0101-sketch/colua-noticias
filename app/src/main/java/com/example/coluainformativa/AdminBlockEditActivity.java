package com.example.coluainformativa;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.repository.ColuaRepository;

public class AdminBlockEditActivity extends AppCompatActivity {

    public static final String EXTRA_BLOCK_ID = "extra_block_id";

    private ColuaRepository repository;
    private String blockId;
    private ContentBlockEntity block;

    private EditText etTitle, etContent, etMedia;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_block_edit);

        repository = new ColuaRepository(this);
        blockId = getIntent().getStringExtra(EXTRA_BLOCK_ID);

        etTitle = findViewById(R.id.et_block_title);
        etContent = findViewById(R.id.et_block_content);
        etMedia = findViewById(R.id.et_block_media);

        findViewById(R.id.btn_back_block_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_block).setOnClickListener(v -> saveBlock());

        if (blockId != null) {
            loadBlock();
        }
    }

    private void loadBlock() {
        new Thread(() -> {
            block = repository.getBlocksBySection("sec_home").stream() // Simplificado para búsqueda global
                    .filter(b -> b.id.equals(blockId))
                    .findFirst()
                    .orElse(null);
            
            // Si no está en Home, buscar por item (o podríamos agregar getBlockById al repo)
            
            runOnUiThread(() -> {
                if (block != null) {
                    etTitle.setText(block.title);
                    etContent.setText(block.content);
                    etMedia.setText(block.mediaPath);
                }
            });
        }).start();
    }

    private void saveBlock() {
        if (block == null) return;

        block.title = etTitle.getText().toString();
        block.content = etContent.getText().toString();
        block.mediaPath = etMedia.getText().toString();

        new Thread(() -> {
            repository.insertBlock(block);
            runOnUiThread(() -> {
                Toast.makeText(this, "Bloque actualizado", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
