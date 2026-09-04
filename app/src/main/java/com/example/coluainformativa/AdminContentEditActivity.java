package com.example.coluainformativa;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
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

        findViewById(R.id.btn_back_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_item).setOnClickListener(v -> saveItem(item != null ? item.isDraft : false));
        findViewById(R.id.btn_add_block).setOnClickListener(v -> showAddBlockDialog());
        findViewById(R.id.btn_preview_item).setOnClickListener(v -> showPreview());
        findViewById(R.id.btn_publish_item).setOnClickListener(v -> publishNow());

        setupColorSelector();
        setupDatePickers();
        setupTargetScreenPicker();

        if (itemId != null) {
            loadItem();
        } else {
            // Nuevo item
            item = new ContentItemEntity(UUID.randomUUID().toString(), sectionId, "", "", "", "#77839A", 0);
            updateDateFields();
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

                // Añadir opciones fijas
                labels.add("Ninguna (Solo informativo)"); ids.add("");
                labels.add("Inicio (ID: sec_home)"); ids.add("sec_home");
                labels.add("Mi Perfil (ID: activity_profile)"); ids.add("activity_profile");
                labels.add("Agencias (ID: sec_agencias)"); ids.add("sec_agencias");

                for (SectionEntity s : sections) {
                    if ("sec_home".equalsIgnoreCase(s.id) || "sec_agencias".equalsIgnoreCase(s.id)) continue;
                    labels.add(s.title + " (ID: " + s.id + ")");
                    ids.add(s.id);
                }

                new AlertDialog.Builder(this)
                        .setTitle("Seleccionar Pantalla de Destino")
                        .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                            etTargetId.setText(ids.get(which));
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }).start();
    }

    private void setupDatePickers() {
        etPubDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                pubCal.set(year, month, dayOfMonth);
                updateDateFields();
            }, pubCal.get(Calendar.YEAR), pubCal.get(Calendar.MONTH), pubCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        etEventDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                eventCal.set(year, month, dayOfMonth);
                updateDateFields();
            }, eventCal.get(Calendar.YEAR), eventCal.get(Calendar.MONTH), eventCal.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void updateDateFields() {
        etPubDate.setText(sdf.format(pubCal.getTime()));
        if (item != null && item.eventDate > 0) {
            etEventDate.setText(sdf.format(new Date(item.eventDate)));
        } else if (eventCal.getTimeInMillis() != pubCal.getTimeInMillis()) {
             etEventDate.setText(sdf.format(eventCal.getTime()));
        }
    }

    private void setupColorSelector() {
        cgColors.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            tilCustomColor.setVisibility(View.GONE);
            
            if (id == R.id.chip_color_white) etColor.setText("#FFFFFF");
            else if (id == R.id.chip_color_blue) etColor.setText("#173789");
            else if (id == R.id.chip_color_orange) etColor.setText("#EF8819");
            else if (id == R.id.chip_color_lila) etColor.setText("#E42A67");
            else if (id == R.id.chip_color_green) etColor.setText("#59B8A4");
            else if (id == R.id.chip_color_purple) etColor.setText("#634794");
            else if (id == R.id.chip_color_custom) {
                tilCustomColor.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadItem() {
        new Thread(() -> {
            item = repository.getItemById(itemId);
            if (item != null) {
                List<ContentBlockEntity> blocks = repository.getBlocksByItem(item.id);
                runOnUiThread(() -> {
                    etTitle.setText(item.title);
                    etSubtitle.setText(item.subtitle);
                    etShortDesc.setText(item.shortDescription);
                    etLongDesc.setText(item.description);
                    etTargetId.setText(item.targetSectionId);
                    etImage.setText(item.imagePath != null && !item.imagePath.isEmpty() ? item.imagePath : item.iconName);
                    etColor.setText(item.accentColor);
                    switchVisible.setChecked(item.isVisible);
                    
                    if (item.publicationDate > 0) pubCal.setTimeInMillis(item.publicationDate);
                    if (item.eventDate > 0) eventCal.setTimeInMillis(item.eventDate);
                    updateDateFields();

                    // Pre-seleccionar chip de color
                    if ("#FFFFFF".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_white);
                    else if ("#173789".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_blue);
                    else if ("#EF8819".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_orange);
                    else if ("#E42A67".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_lila);
                    else if ("#59B8A4".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_green);
                    else if ("#634794".equalsIgnoreCase(item.accentColor)) cgColors.check(R.id.chip_color_purple);
                    else if (item.accentColor != null && !item.accentColor.isEmpty()) {
                        cgColors.check(R.id.chip_color_custom);
                        tilCustomColor.setVisibility(View.VISIBLE);
                    }
                    
                    blockList.clear();
                    blockList.addAll(blocks);
                });
            }
        }).start();
    }

    private void publishNow() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Publicación")
                .setMessage("¿Desea publicar este contenido ahora mismo? Será visible para todos los asociados.")
                .setPositiveButton("PUBLICAR", (dialog, which) -> saveItem(false))
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void showPreview() {
        // Guardar cambios temporales en el objeto 'item' antes de mostrar preview
        updateItemFromFields();
        
        View dialogView = getLayoutInflater().inflate(R.layout.item_news_card, null);
        // Configurar la vista previa usando el layout de noticias o el de tarjetas
        // (Dependiendo de la sección, por simplicidad usaremos un diálogo genérico que muestre el card)
        
        new AlertDialog.Builder(this)
                .setTitle("Previsualización")
                .setView(dialogView)
                .setPositiveButton("Cerrar", null)
                .show();
        
        // Aquí deberías vincular los datos al dialogView similar a como lo hace el ContentItemAdapter
        // Para mayor fidelidad, se puede abrir una nueva actividad de Preview
    }

    private void updateItemFromFields() {
        if (item == null) return;
        item.title = etTitle.getText().toString();
        item.subtitle = etSubtitle.getText().toString();
        item.shortDescription = etShortDesc.getText().toString();
        item.description = etLongDesc.getText().toString();
        item.targetSectionId = etTargetId.getText().toString();
        item.imagePath = etImage.getText().toString();
        item.iconName = etImage.getText().toString();
        item.accentColor = etColor.getText().toString();
        item.isVisible = switchVisible.isChecked();
        item.publicationDate = pubCal.getTimeInMillis();
        item.eventDate = eventCal.getTimeInMillis();
    }

    private void saveItem(boolean isDraft) {
        if (item == null) return;
        updateItemFromFields();

        // Validar destino si no está vacío
        String target = item.targetSectionId != null ? item.targetSectionId.trim() : "";
        if (!target.isEmpty()) {
            boolean isKnownTarget = "sec_home".equalsIgnoreCase(target) ||
                    "sec_agencias".equalsIgnoreCase(target) ||
                    "activity_profile".equalsIgnoreCase(target) ||
                    "dialog_admin".equalsIgnoreCase(target) ||
                    "action_logout".equalsIgnoreCase(target);

            if (!isKnownTarget) {
                new Thread(() -> {
                    List<SectionEntity> sections = repository.getAllSections();
                    boolean exists = sections.stream().anyMatch(s -> s.id.equalsIgnoreCase(target) || (s.slug != null && s.slug.equalsIgnoreCase(target)));

                    runOnUiThread(() -> {
                        if (!exists) {
                            Toast.makeText(this, "ERROR: La pantalla de destino '" + target + "' no existe o fue eliminada.", Toast.LENGTH_LONG).show();
                        } else {
                            executeSaveItem(isDraft);
                        }
                    });
                }).start();
                return;
            }
        }

        executeSaveItem(isDraft);
    }

    private void executeSaveItem(boolean isDraft) {
        item.isDraft = isDraft;
        item.updatedAt = System.currentTimeMillis();
        
        // Marcar borrador local con cambios pendientes
        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();

        new Thread(() -> {
            repository.insertItem(item);
            runOnUiThread(() -> {
                if (isDraft) {
                    new AlertDialog.Builder(this)
                            .setTitle("Borrador Guardado Localmente")
                            .setMessage("El contenido se ha guardado en tu borrador local y no será visible para los usuarios hasta que lo publiques.\n\n¿Qué deseas hacer ahora?")
                            .setPositiveButton("PREVISUALIZAR PANTALLA", (d, w) -> showPreview())
                            .setNeutralButton("SEGUIR EDITANDO", null)
                            .setNegativeButton("VOLVER A LISTA", (d, w) -> finish())
                            .show();
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
        
        new AlertDialog.Builder(this)
                .setTitle("Nuevo Bloque de Texto")
                .setView(et)
                .setPositiveButton("Agregar", (dialog, which) -> {
                    String text = et.getText().toString();
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
                                Toast.makeText(this, "Bloque guardado", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
