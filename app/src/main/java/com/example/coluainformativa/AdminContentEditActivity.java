package com.example.coluainformativa;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.DialogHelper;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminContentEditActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_SECTION_ID = "extra_section_id";

    private ColuaRepository repository;
    private String itemId;
    private String sectionId;
    private ContentItemEntity item;

    private EditText etTitle, etSubtitle, etShortDesc, etLongDesc, etTargetId, etImage, etColor, etValue;
    private EditText etPubDate, etEventDate;
    private MaterialSwitch switchVisible;
    private ChipGroup cgColors;
    private TextInputLayout tilCustomColor;
    private List<ContentBlockEntity> blockList = new ArrayList<>();

    // Vistas de Previsualización en Vivo
    private TextView tvLiveTitle, tvLivePill, tvLiveDesc, tvLiveValue, tvActiveColorLabel, tvBlocksCount;
    private ImageView ivLiveIcon;
    private View btnBrowseScreen;

    private Calendar pubCal = Calendar.getInstance();
    private Calendar eventCal = Calendar.getInstance();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_content_edit);

        repository = new ColuaRepository(this);
        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);

        etTitle = findViewById(R.id.et_item_title);
        etSubtitle = findViewById(R.id.et_item_subtitle);
        etShortDesc = findViewById(R.id.et_item_short_desc);
        etLongDesc = findViewById(R.id.et_item_long_desc);
        etTargetId = findViewById(R.id.et_item_target_id);
        etImage = findViewById(R.id.et_item_image);
        etColor = findViewById(R.id.et_item_color);
        etValue = findViewById(R.id.et_item_value);
        etPubDate = findViewById(R.id.et_item_pub_date);
        etEventDate = findViewById(R.id.et_item_event_date);
        switchVisible = findViewById(R.id.switch_item_visible);
        cgColors = findViewById(R.id.cg_accent_colors);
        tilCustomColor = findViewById(R.id.til_custom_color);

        // Vistas de Previsualización en Vivo
        tvLiveTitle = findViewById(R.id.tv_live_title);
        tvLivePill = findViewById(R.id.tv_live_pill);
        tvLiveDesc = findViewById(R.id.tv_live_desc);
        tvLiveValue = findViewById(R.id.tv_live_value);
        ivLiveIcon = findViewById(R.id.iv_live_icon);
        tvActiveColorLabel = findViewById(R.id.tv_active_color_label);
        tvBlocksCount = findViewById(R.id.tv_blocks_count);
        btnBrowseScreen = findViewById(R.id.btn_browse_screen);

        findViewById(R.id.btn_back_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_item).setOnClickListener(v -> saveItem(item != null ? item.isDraft : false));
        findViewById(R.id.btn_add_block).setOnClickListener(v -> showAddBlockDialog());
        findViewById(R.id.btn_preview_item).setOnClickListener(v -> showPreview());
        findViewById(R.id.btn_publish_item).setOnClickListener(v -> publishNow());

        setupColorSelector();
        setupDatePickers();
        setupTargetScreenPicker();
        setupLivePreviewListeners();
        setupIconChooserAndSuggestions();

        if (itemId != null) {
            loadItem();
        } else {
            item = new ContentItemEntity(UUID.randomUUID().toString(), sectionId, "", "", "", "#173789", 0);
            updateDateFields();
            updateLivePreview();
        }
    }

    private void setupIconChooserAndSuggestions() {
        View btnChoose = findViewById(R.id.btn_choose_icon);
        if (btnChoose != null) {
            btnChoose.setOnClickListener(v -> {
                DialogHelper.showResourcePickerDialog(this, (resName, displayLabel) -> {
                    if (resName != null && !resName.isEmpty()) {
                        if (etImage != null) etImage.setText(resName);
                        updateLivePreview();
                    }
                }, null);
            });
        }

        View chipCredito = findViewById(R.id.chip_sug_credito);
        if (chipCredito != null) chipCredito.setOnClickListener(v -> { if (etImage != null) etImage.setText("credito"); updateLivePreview(); });

        View chipAhorro = findViewById(R.id.chip_sug_ahorro);
        if (chipAhorro != null) chipAhorro.setOnClickListener(v -> { if (etImage != null) etImage.setText("ahorros"); updateLivePreview(); });

        View chipSeguro = findViewById(R.id.chip_sug_seguro);
        if (chipSeguro != null) chipSeguro.setOnClickListener(v -> { if (etImage != null) etImage.setText("seguro"); updateLivePreview(); });

        View chipStar = findViewById(R.id.chip_sug_star);
        if (chipStar != null) chipStar.setOnClickListener(v -> { if (etImage != null) etImage.setText("ic_star"); updateLivePreview(); });

        if (btnBrowseScreen != null) {
            btnBrowseScreen.setOnClickListener(v -> showScreenPicker());
        }
    }

    private void setupLivePreviewListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLivePreview();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (etTitle != null) etTitle.addTextChangedListener(watcher);
        if (etSubtitle != null) etSubtitle.addTextChangedListener(watcher);
        if (etShortDesc != null) etShortDesc.addTextChangedListener(watcher);
        if (etValue != null) etValue.addTextChangedListener(watcher);
        if (etImage != null) etImage.addTextChangedListener(watcher);
        if (etColor != null) etColor.addTextChangedListener(watcher);
    }

    private void updateLivePreview() {
        String titleStr = etTitle != null ? etTitle.getText().toString().trim() : "";
        String pillStr = etSubtitle != null ? etSubtitle.getText().toString().trim() : "";
        String descStr = etShortDesc != null ? etShortDesc.getText().toString().trim() : "";
        String valStr = etValue != null ? etValue.getText().toString().trim() : "";
        String iconStr = etImage != null ? etImage.getText().toString().trim() : "";
        String colorStr = etColor != null ? etColor.getText().toString().trim() : "#173789";

        if (tvLiveTitle != null) {
            tvLiveTitle.setText(!titleStr.isEmpty() ? titleStr : "Título de la tarjeta");
        }
        if (tvLivePill != null) {
            if (!pillStr.isEmpty()) {
                tvLivePill.setText(pillStr);
                tvLivePill.setVisibility(View.VISIBLE);
            } else {
                tvLivePill.setVisibility(View.GONE);
            }
        }
        if (tvLiveDesc != null) {
            tvLiveDesc.setText(!descStr.isEmpty() ? descStr : "Descripción corta de la tarjeta o servicio...");
        }
        if (tvLiveValue != null) {
            if (!valStr.isEmpty()) {
                tvLiveValue.setText("DESDE " + valStr);
                tvLiveValue.setVisibility(View.VISIBLE);
            } else {
                tvLiveValue.setVisibility(View.GONE);
            }
        }
        if (ivLiveIcon != null) {
            AdminNewsEditActivity.loadNewsImageIntoView(this, ivLiveIcon, !iconStr.isEmpty() ? iconStr : "credito");
        }

        if (tvActiveColorLabel != null && !colorStr.isEmpty()) {
            try {
                int parsedColor = Color.parseColor(colorStr);
                tvActiveColorLabel.setText(colorStr.toUpperCase(Locale.getDefault()));
                tvActiveColorLabel.setTextColor(parsedColor);
                if (tvLiveValue != null) tvLiveValue.setTextColor(parsedColor);
            } catch (Exception ignored) {}
        }
    }

    private void setupTargetScreenPicker() {
        if (etTargetId != null) {
            etTargetId.setFocusable(false);
            etTargetId.setOnClickListener(v -> showScreenPicker());
        }
    }

    private void showScreenPicker() {
        new Thread(() -> {
            List<SectionEntity> sections = repository.getAllSections();
            runOnUiThread(() -> {
                List<String> labels = new ArrayList<>();
                List<String> ids = new ArrayList<>();

                labels.add("Ninguna (Solo informativo)"); ids.add("");
                labels.add("Inicio (ID: sec_home)"); ids.add("sec_home");
                labels.add("Mi Perfil (ID: activity_profile)"); ids.add("activity_profile");
                labels.add("Agencias (ID: sec_agencias)"); ids.add("sec_agencias");

                for (SectionEntity s : sections) {
                    if ("sec_home".equalsIgnoreCase(s.id) || "sec_agencias".equalsIgnoreCase(s.id)) continue;
                    labels.add(s.title + " (ID: " + s.id + ")");
                    ids.add(s.id);
                }

                new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
                        .setTitle("Seleccionar Pantalla de Destino")
                        .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                            etTargetId.setText(ids.get(which));
                            updateLivePreview();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }).start();
    }

    private void setupDatePickers() {
        if (etPubDate != null) {
            etPubDate.setOnClickListener(v -> {
                new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    pubCal.set(year, month, dayOfMonth);
                    updateDateFields();
                }, pubCal.get(Calendar.YEAR), pubCal.get(Calendar.MONTH), pubCal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        if (etEventDate != null) {
            etEventDate.setOnClickListener(v -> {
                new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    eventCal.set(year, month, dayOfMonth);
                    updateDateFields();
                }, eventCal.get(Calendar.YEAR), eventCal.get(Calendar.MONTH), eventCal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }
    }

    private void updateDateFields() {
        if (etPubDate != null) etPubDate.setText(sdf.format(pubCal.getTime()));
        if (etEventDate != null) {
            if (item != null && item.eventDate > 0) {
                etEventDate.setText(sdf.format(new Date(item.eventDate)));
            } else if (eventCal.getTimeInMillis() != pubCal.getTimeInMillis()) {
                etEventDate.setText(sdf.format(eventCal.getTime()));
            }
        }
    }

    private void setupColorSelector() {
        if (cgColors == null) return;
        cgColors.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (tilCustomColor != null) tilCustomColor.setVisibility(View.GONE);

            if (id == R.id.chip_color_white && etColor != null) etColor.setText("#FFFFFF");
            else if (id == R.id.chip_color_blue && etColor != null) etColor.setText("#173789");
            else if (id == R.id.chip_color_orange && etColor != null) etColor.setText("#EF8819");
            else if (id == R.id.chip_color_lila && etColor != null) etColor.setText("#E42A67");
            else if (id == R.id.chip_color_green && etColor != null) etColor.setText("#59B8A4");
            else if (id == R.id.chip_color_purple && etColor != null) etColor.setText("#634794");
            else if (id == R.id.chip_color_custom && tilCustomColor != null) {
                tilCustomColor.setVisibility(View.VISIBLE);
            }
            updateLivePreview();
        });
    }

    private void loadItem() {
        new Thread(() -> {
            item = repository.getItemById(itemId);
            if (item != null) {
                List<ContentBlockEntity> blocks = repository.getBlocksByItem(item.id);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (etTitle != null) etTitle.setText(item.title != null ? item.title : "");
                    if (etSubtitle != null) etSubtitle.setText(item.subtitle != null ? item.subtitle : "");
                    if (etShortDesc != null) etShortDesc.setText(item.shortDescription != null ? item.shortDescription : "");
                    if (etLongDesc != null) etLongDesc.setText(item.description != null ? item.description : "");
                    if (etTargetId != null) etTargetId.setText(item.targetSectionId != null ? item.targetSectionId : "");
                    if (etImage != null) etImage.setText(item.imagePath != null && !item.imagePath.isEmpty() ? item.imagePath : (item.iconName != null ? item.iconName : "credito"));
                    if (etColor != null) etColor.setText(item.accentColor != null ? item.accentColor : "#173789");
                    if (switchVisible != null) switchVisible.setChecked(item.isVisible);

                    if (item.publicationDate > 0) pubCal.setTimeInMillis(item.publicationDate);
                    if (item.eventDate > 0) eventCal.setTimeInMillis(item.eventDate);
                    updateDateFields();

                    if (cgColors != null && item.accentColor != null) {
                        if ("#FFFFFF".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_white);
                        else if ("#173789".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_blue);
                        else if ("#EF8819".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_orange);
                        else if ("#E42A67".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_lila);
                        else if ("#59B8A4".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_green);
                        else if ("#634794".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_purple);
                        else if (!item.accentColor.isEmpty()) {
                            cgColors.check(R.id.chip_color_custom);
                            if (tilCustomColor != null) tilCustomColor.setVisibility(View.VISIBLE);
                        }
                    }

                    blockList.clear();
                    if (blocks != null) blockList.addAll(blocks);
                    if (tvBlocksCount != null) tvBlocksCount.setText(blockList.size() + " bloques activos");

                    updateLivePreview();
                });
            }
        }).start();
    }

    private void publishNow() {
        DialogHelper.showLightReportDialog(
                this,
                "Confirmar Publicación",
                "¿Desea publicar este contenido en la nube? Los asociados lo verán inmediatamente en la aplicación.",
                "PUBLICAR AHORA",
                () -> saveItem(false),
                "CANCELAR"
        );
    }

    private void showPreview() {
        updateItemFromFields();
        Intent intent = new Intent(this, DynamicSectionActivity.class);
        intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, sectionId != null ? sectionId : "sec_home");
        intent.putExtra("extra_is_preview_mode", true);
        startActivity(intent);
    }

    private void updateItemFromFields() {
        if (item == null) return;
        if (etTitle != null) item.title = etTitle.getText().toString().trim();
        if (etSubtitle != null) item.subtitle = etSubtitle.getText().toString().trim();
        if (etShortDesc != null) item.shortDescription = etShortDesc.getText().toString().trim();
        if (etLongDesc != null) item.description = etLongDesc.getText().toString().trim();
        if (etTargetId != null) item.targetSectionId = etTargetId.getText().toString().trim();
        if (etImage != null) {
            String img = etImage.getText().toString().trim();
            item.imagePath = img;
            item.iconName = img;
        }
        if (etColor != null) item.accentColor = etColor.getText().toString().trim();
        if (switchVisible != null) item.isVisible = switchVisible.isChecked();
        item.publicationDate = pubCal.getTimeInMillis();
        item.eventDate = eventCal.getTimeInMillis();
    }

    private void saveItem(boolean isDraft) {
        if (item == null) return;
        updateItemFromFields();
        executeSaveItem(isDraft);
    }

    private void executeSaveItem(boolean isDraft) {
        item.isDraft = isDraft;
        item.updatedAt = System.currentTimeMillis();

        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();

        new Thread(() -> {
            repository.insertItem(item);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (isDraft) {
                    DialogHelper.showLightReportDialog(
                            this,
                            "Borrador Guardado",
                            "El contenido se ha guardado en tu borrador local y no será visible para los usuarios hasta que lo publiques.",
                            "VOLVER AL CANVAS",
                            () -> finish(),
                            "SEGUIR EDITANDO"
                    );
                } else {
                    Toast.makeText(this, "¡Contenido publicado exitosamente!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    private void showAddBlockDialog() {
        EditText et = new EditText(this);
        et.setHint("Escribe el contenido del bloque...");

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setTitle("Nuevo Bloque de Texto")
                .setView(et)
                .setPositiveButton("Agregar", (dialog, which) -> {
                    String text = et.getText().toString().trim();
                    if (!text.isEmpty()) {
                        ContentBlockEntity block = new ContentBlockEntity(
                                UUID.randomUUID().toString(),
                                item.id,
                                "TEXT",
                                text,
                                blockList.size() + 1
                        );
                        new Thread(() -> {
                            repository.insertBlock(block);
                            runOnUiThread(() -> {
                                blockList.add(block);
                                if (tvBlocksCount != null) tvBlocksCount.setText(blockList.size() + " bloques activos");
                                Toast.makeText(this, "Bloque agregado", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
