package com.example.coluainformativa;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConfigEstructuraActivity extends AppCompatActivity {

    private List<SectionEntity> sectionsList = new ArrayList<>();
    private ColuaRepository repository;
    private AdminAuthManager authManager;
    private ScreensAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_estructura);

        repository = new ColuaRepository(this);
        authManager = new AdminAuthManager(this);

        findViewById(R.id.btn_back_config).setOnClickListener(v -> finish());

        RecyclerView rvScreens = findViewById(R.id.rv_screens);
        rvScreens.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ScreensAdapter();
        rvScreens.setAdapter(adapter);

        findViewById(R.id.btn_add_section).setOnClickListener(v -> showAddSectionDialog());

        loadSections();
    }

    private void loadSections() {
        new Thread(() -> {
            List<SectionEntity> sections = repository.getAllSections();
            runOnUiThread(() -> {
                sectionsList.clear();
                sectionsList.addAll(sections);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void showAddSectionDialog() {
        Intent intent = new Intent(this, SectionEditorActivity.class);
        startActivity(intent);
    }

    class ScreensAdapter extends RecyclerView.Adapter<ScreensAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screen_config, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SectionEntity section = sectionsList.get(position);
            holder.tvName.setText(section.title);
            
            int color = Color.parseColor(section.accentColor);
            holder.viewIndicator.setBackgroundColor(color);
            holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(color));

            if (!section.isVisible) {
                holder.btnVisible.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                holder.itemView.setAlpha(0.5f);
            } else {
                holder.btnVisible.setImageResource(android.R.drawable.ic_menu_view);
                holder.itemView.setAlpha(1.0f);
            }

            holder.btnVisible.setOnClickListener(v -> {
                section.isVisible = !section.isVisible;
                new Thread(() -> {
                    repository.insertSection(section); // Update
                    runOnUiThread(() -> notifyItemChanged(position));
                }).start();
            });

            holder.btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(ConfigEstructuraActivity.this, SectionEditorActivity.class);
                intent.putExtra(SectionEditorActivity.EXTRA_SECTION_ID, section.id);
                startActivity(intent);
            });

            holder.btnManageContent.setOnClickListener(v -> {
                Intent intent = new Intent(ConfigEstructuraActivity.this, AdminContentListActivity.class);
                intent.putExtra(AdminContentListActivity.EXTRA_SECTION_ID, section.id);
                startActivity(intent);
            });

            holder.btnDelete.setOnClickListener(v -> {
                if ("sec_home".equals(section.id)) {
                    Toast.makeText(ConfigEstructuraActivity.this, "La sección Inicio es obligatoria", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                showDeleteConfirmation(section, position);
            });
        }

        private void showDeleteConfirmation(SectionEntity section, int position) {
            android.widget.EditText etPass = new android.widget.EditText(ConfigEstructuraActivity.this);
            etPass.setHint("Confirmar Contraseña Admin");
            etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

            new androidx.appcompat.app.AlertDialog.Builder(ConfigEstructuraActivity.this)
                    .setTitle("¿Eliminar " + section.title + "?")
                    .setMessage("Esta acción eliminará todo el contenido de esta sección.")
                    .setView(etPass)
                    .setPositiveButton("Eliminar Permanentemente", (dialog, which) -> {
                        if (authManager.checkPassword(etPass.getText().toString())) {
                            new Thread(() -> {
                                AppDatabase.getDatabase(ConfigEstructuraActivity.this).sectionDao().deleteById(section.id);
                                runOnUiThread(() -> {
                                    sectionsList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, sectionsList.size());
                                });
                            }).start();
                        } else {
                            Toast.makeText(ConfigEstructuraActivity.this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }

        @Override
        public int getItemCount() {
            return sectionsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            ImageView ivIcon;
            View viewIndicator;
            ImageButton btnVisible, btnDelete, btnEdit, btnManageContent;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_screen_name);
                ivIcon = v.findViewById(R.id.iv_screen_icon);
                viewIndicator = v.findViewById(R.id.view_indicator);
                btnVisible = v.findViewById(R.id.btn_toggle_visibility);
                btnDelete = v.findViewById(R.id.btn_delete_screen);
                btnEdit = v.findViewById(R.id.btn_edit_screen); // Necesito asegurar que este ID existe en item_screen_config.xml
                btnManageContent = v.findViewById(R.id.btn_manage_content);
            }
        }
    }
}