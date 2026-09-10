package com.example.coluainformativa;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.ui.content.ElementTypeAdapter;
import com.example.coluainformativa.utils.DialogHelper;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminBlockEditActivity extends AppCompatActivity {

    public static final String EXTRA_BLOCK_ID = "extra_block_id";
    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_BLOCK_TYPE = "extra_block_type";
    private static final int REQUEST_PICK_IMAGE = 101;

    private ColuaRepository repository;
    private AdminAuthManager authManager;
    private String blockId;
    private String sectionId;
    private ContentBlockEntity block;

    private Spinner spinnerActionType, spinnerTargetScreen, spinnerCardStyle;
    private String selectedBlockTypeKey = "TEXT";
    private TextView tvElementTypeTitle, tvElementTypeSubtitle, tvActionSummary;
    private ImageView ivElementTypeIcon;
    private EditText etTitle, etContent, etBenefitsList, etHighlightAmount, etMedia, etButtonText, etButtonAction;
    private RadioGroup rgAlignment;
    private RadioButton rbLeft, rbCenter, rbRight;

    private View layoutFieldCardStyle, layoutFieldText, layoutFieldMedia, layoutFieldButton, layoutFieldAlignment, layoutActionTargetScreen, tilButtonActionValue;

    private List<SectionEntity> availableSections = new ArrayList<>();

    private static final String[] ACTION_TYPES = {
            "1. Abrir una pantalla de la app",
            "2. Llamar por teléfono (tel:)",
            "3. Abrir sitio web o red social (URL)",
            "4. Enviar WhatsApp (wa.me)",
            "5. Sin acción / informativo"
    };

    private static final String[] CARD_STYLES = {
            "1. Estilo Créditos (Ícono + Título + Viñetas + Monto + Botón)",
            "2. Estilo Seguros (Imagen Destacada + Título + Botón CTA)",
            "3. Estilo Agencias / Puntos (Título + Ubicación + Teléfono)",
            "4. Estilo Estándar (Borde de Color + Título + Párrafo + Botón)"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_block_edit);

        repository = new ColuaRepository(this);
        authManager = new AdminAuthManager(this);
        blockId = getIntent().getStringExtra(EXTRA_BLOCK_ID);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);

        spinnerCardStyle = findViewById(R.id.spinner_card_style);
        spinnerActionType = findViewById(R.id.spinner_button_action_type);
        spinnerTargetScreen = findViewById(R.id.spinner_target_screen);

        tvElementTypeTitle = findViewById(R.id.tv_element_type_title);
        tvElementTypeSubtitle = findViewById(R.id.tv_element_type_subtitle);
        tvActionSummary = findViewById(R.id.tv_button_action_summary);
        ivElementTypeIcon = findViewById(R.id.iv_element_type_icon);

        etTitle = findViewById(R.id.et_block_title);
        etContent = findViewById(R.id.et_block_content);
        etBenefitsList = findViewById(R.id.et_block_benefits_list);
        etHighlightAmount = findViewById(R.id.et_block_highlight_amount);
        etMedia = findViewById(R.id.et_block_media);
        etButtonText = findViewById(R.id.et_block_button_text);
        etButtonAction = findViewById(R.id.et_block_button_action);

        rgAlignment = findViewById(R.id.rg_block_alignment);
        rbLeft = findViewById(R.id.rb_align_left);
        rbCenter = findViewById(R.id.rb_align_center);
        rbRight = findViewById(R.id.rb_align_right);

        layoutFieldCardStyle = findViewById(R.id.layout_field_card_style);
        layoutFieldText = findViewById(R.id.layout_field_text);
        layoutFieldMedia = findViewById(R.id.layout_field_media);
        layoutFieldButton = findViewById(R.id.layout_field_button);
        layoutFieldAlignment = findViewById(R.id.layout_field_alignment);
        layoutActionTargetScreen = findViewById(R.id.layout_action_target_screen);
        tilButtonActionValue = findViewById(R.id.til_button_action_value);

        setupSpinners();
        setupLiveTextWatchers();

        findViewById(R.id.btn_back_block_edit).setOnClickListener(v -> finish());

        // Selector Modal de Tipo de Elemento
        View cardSelectType = findViewById(R.id.card_select_element_type);
        if (cardSelectType != null) {
            cardSelectType.setOnClickListener(v -> showSelectElementTypeDialog());
        }

        // Botón Galería
        View btnSelectMedia = findViewById(R.id.btn_select_block_icon);
        if (btnSelectMedia != null) {
            btnSelectMedia.setOnClickListener(v -> showResourcePickerDialog());
        }

        // 3 Botones de salida y guardado
        View btnCancel = findViewById(R.id.btn_cancel_block);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }

        View btnSaveDraft = findViewById(R.id.btn_save_draft_block);
        if (btnSaveDraft != null) {
            btnSaveDraft.setOnClickListener(v -> saveBlock(false));
        }

        View btnSaveAndPreview = findViewById(R.id.btn_save_and_preview_block);
        if (btnSaveAndPreview != null) {
            btnSaveAndPreview.setOnClickListener(v -> saveBlock(true));
        }

        loadAvailableScreens();

        if (blockId != null) {
            loadBlock();
        } else {
            block = new ContentBlockEntity();
            block.id = UUID.randomUUID().toString();
            block.sectionId = sectionId != null ? sectionId : "sec_home";

            String preselectedType = getIntent().getStringExtra(EXTRA_BLOCK_TYPE);
            if (preselectedType == null || preselectedType.trim().isEmpty()) {
                preselectedType = "TEXT";
            }
            selectedBlockTypeKey = preselectedType;
            block.type = selectedBlockTypeKey;
            block.alignment = "LEFT";

            updateElementTypeHeaderAndForm(selectedBlockTypeKey);
        }
    }

    private void setupSpinners() {
        if (spinnerCardStyle != null) {
            ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, CARD_STYLES);
            styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCardStyle.setAdapter(styleAdapter);
        }

        if (spinnerActionType != null) {
            ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, ACTION_TYPES);
            actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerActionType.setAdapter(actionAdapter);

            spinnerActionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    updateButtonActionFieldsVisibility(position);
                    updateLiveActionSummary();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void showSelectElementTypeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_element, null);
        RecyclerView rvTypes = dialogView.findViewById(R.id.rv_element_types);
        EditText etSearch = dialogView.findViewById(R.id.et_search_element_type);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_categories);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_dialog);

        List<ElementTypeAdapter.ElementTypeItem> items = new ArrayList<>();
        items.add(new ElementTypeAdapter.ElementTypeItem("TEXT", "Texto", "Título, Subtítulo, Párrafo, Informativo", R.drawable.ic_newspaper, "#EFF6FF", "#1D4ED8", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("IMAGE", "Imagen", "Logo, Banner, Fotografía, Ilustración", R.drawable.contenido_depantallas, "#DCFCE7", "#15803D", "Multimedia", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("ICON", "Ícono", "Ícono de Galería o Vectorial con estilo", R.drawable.ic_star, "#F3E8FF", "#7E22CE", "Multimedia", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("BUTTON", "Botón", "Navegación, PBX / Llamada, Enlace web", R.drawable.ic_campaign, "#FEF3C7", "#B45309", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("CARD", "Tarjeta", "Contenedor visual con borde, elevación y sombra", R.drawable.ic_credit_card, "#E0F2FE", "#0369A1", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("PRODUCT_CARD", "Tarjeta de Producto", "Crédito, Ahorro, Seguro, Remesa COLUA", R.drawable.ic_account_balance, "#0F172A", "#10B981", "Financiero", true));
        items.add(new ElementTypeAdapter.ElementTypeItem("CONTAINER", "Sección / Contenedor", "Agrupa elementos en pantalla sin tarjeta", R.drawable.ic_business, "#F1F5F9", "#475569", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("LIST", "Lista", "Viñetas, Beneficios, Requisitos por puntos", R.drawable.ic_receipt, "#E0F2FE", "#0284C7", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("SEPARATOR", "Separador", "Línea divisora horizontal de sección", R.drawable.ic_calculate, "#F1F5F9", "#64748B", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("SPACER", "Espaciador", "Espaciado vertical entre bloques", R.drawable.ic_calculate, "#F3E8FF", "#9333EA", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("BANNER", "Banner Destacado", "Imagen o bloque promocional ancho completo", R.drawable.ic_trending_up, "#DCFCE7", "#16A34A", "Multimedia", false));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ElementTypeAdapter adapter = new ElementTypeAdapter(items, selectedItem -> {
            dialog.dismiss();
            selectedBlockTypeKey = selectedItem.key;
            updateElementTypeHeaderAndForm(selectedBlockTypeKey);

            TextView tvLabel = findViewById(R.id.tv_selected_element_type_label);
            if (tvLabel != null) {
                tvLabel.setText("Tipo actual: " + selectedItem.title);
            }
        });

        if (rvTypes != null) {
            rvTypes.setLayoutManager(new LinearLayoutManager(this));
            rvTypes.setAdapter(adapter);
        }

        final String[] selectedCat = {"Todos"};
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s.toString(), selectedCat[0]);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    selectedCat[0] = "Todos";
                } else {
                    int id = checkedIds.get(0);
                    if (id == R.id.chip_cat_basics) selectedCat[0] = "Básicos";
                    else if (id == R.id.chip_cat_media) selectedCat[0] = "Multimedia";
                    else if (id == R.id.chip_cat_structure) selectedCat[0] = "Estructura";
                    else if (id == R.id.chip_cat_financial) selectedCat[0] = "Financiero";
                    else selectedCat[0] = "Todos";
                }
                String q = etSearch != null ? etSearch.getText().toString() : "";
                adapter.filter(q, selectedCat[0]);
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void updateButtonActionFieldsVisibility(int actionIndex) {
        if (layoutActionTargetScreen != null) {
            layoutActionTargetScreen.setVisibility(actionIndex == 0 ? View.VISIBLE : View.GONE);
        }
        if (tilButtonActionValue != null) {
            tilButtonActionValue.setVisibility((actionIndex >= 1 && actionIndex <= 3) ? View.VISIBLE : View.GONE);
        }
    }

    private void loadAvailableScreens() {
        new Thread(() -> {
            availableSections = repository.getAllSections();
            List<String> screenLabels = new ArrayList<>();
            for (SectionEntity s : availableSections) {
                screenLabels.add(s.title + " (/" + (s.slug != null && !s.slug.isEmpty() ? s.slug : s.id) + ")");
            }

            runOnUiThread(() -> {
                if (spinnerTargetScreen != null) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(AdminBlockEditActivity.this,
                            android.R.layout.simple_spinner_item, screenLabels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerTargetScreen.setAdapter(adapter);

                    spinnerTargetScreen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < availableSections.size()) {
                                SectionEntity s = availableSections.get(position);
                                etButtonAction.setText("/" + (s.slug != null && !s.slug.isEmpty() ? s.slug : s.id));
                            }
                            updateLiveActionSummary();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            });
        }).start();
    }

    private void setupLiveTextWatchers() {
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLiveActionSummary();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (etButtonText != null) etButtonText.addTextChangedListener(tw);
        if (etButtonAction != null) etButtonAction.addTextChangedListener(tw);
    }

    private void updateLiveActionSummary() {
        if (tvActionSummary == null) return;

        String btnLabel = etButtonText != null ? etButtonText.getText().toString().trim() : "";
        if (btnLabel.isEmpty()) btnLabel = "Botón CTA";

        int actionIndex = spinnerActionType != null ? spinnerActionType.getSelectedItemPosition() : 0;
        String actionValue = etButtonAction != null ? etButtonAction.getText().toString().trim() : "";

        String summaryText = "Resumen: ";
        if (actionIndex == 0) {
            summaryText += "El botón '" + btnLabel + "' abrirá la pantalla interna '" + actionValue + "'.";
        } else if (actionIndex == 1) {
            summaryText += "El botón '" + btnLabel + "' abrirá el marcador telefónico con '" + actionValue + "'.";
        } else if (actionIndex == 2) {
            summaryText += "El botón '" + btnLabel + "' abrirá la página web/red social '" + actionValue + "'.";
        } else if (actionIndex == 3) {
            summaryText += "El botón '" + btnLabel + "' abrirá un chat de WhatsApp al número '" + actionValue + "'.";
        } else {
            summaryText += "El botón '" + btnLabel + "' es de solo lectura (sin acción).";
        }

        tvActionSummary.setText(summaryText);
    }

    private void updateElementTypeHeaderAndForm(String type) {
        if (tvElementTypeTitle == null || tvElementTypeSubtitle == null) return;

        String upperType = type != null ? type.toUpperCase(Locale.getDefault()) : "TEXT";

        if ("BUTTON".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Botón / Acción CTA");
            tvElementTypeSubtitle.setText("Elemento de llamada a la acción para navegar, contactar o abrir un enlace");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_campaign);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.GONE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.VISIBLE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.VISIBLE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("IMAGE".equalsIgnoreCase(upperType) || "BANNER".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Imagen / Banner Destacado");
            tvElementTypeSubtitle.setText("Logotipo, banner, fotografía o ilustración promocional en alta definición");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.contenido_depantallas);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.VISIBLE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("ICON".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Ícono de Galería");
            tvElementTypeSubtitle.setText("Ícono gráfico o vectorial de la galería de la app");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_star);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.VISIBLE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("PRODUCT_CARD".equalsIgnoreCase(upperType) || "CARD".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Tarjeta de Producto / Servicio");
            tvElementTypeSubtitle.setText("Producto, crédito, seguro, remesa u oferta estructurada con borde y sombra");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_credit_card);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.VISIBLE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.VISIBLE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.VISIBLE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("CONTAINER".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Sección / Contenedor Libre");
            tvElementTypeSubtitle.setText("Agrupa elementos en pantalla de forma ordenada y estructurada");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_business);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.VISIBLE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("LIST".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Lista (Viñetas / Beneficios / Requisitos)");
            tvElementTypeSubtitle.setText("Organiza puntos, ventajas o requerimientos de forma clara");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_receipt);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.GONE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);

        } else if ("SEPARATOR".equalsIgnoreCase(upperType) || "SPACER".equalsIgnoreCase(upperType)) {
            tvElementTypeTitle.setText("Separador / Espaciador");
            tvElementTypeSubtitle.setText("Línea divisora o espacio vertical entre elementos");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_calculate);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.GONE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.GONE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.GONE);

        } else { // TEXT, etc.
            tvElementTypeTitle.setText("Texto / Bloque Informativo");
            tvElementTypeSubtitle.setText("Títulos, subtítulos, párrafos y textos informativos");
            if (ivElementTypeIcon != null) ivElementTypeIcon.setImageResource(R.drawable.ic_newspaper);

            if (layoutFieldCardStyle != null) layoutFieldCardStyle.setVisibility(View.GONE);
            if (layoutFieldText != null) layoutFieldText.setVisibility(View.VISIBLE);
            if (layoutFieldMedia != null) layoutFieldMedia.setVisibility(View.GONE);
            if (layoutFieldButton != null) layoutFieldButton.setVisibility(View.GONE);
            if (layoutFieldAlignment != null) layoutFieldAlignment.setVisibility(View.VISIBLE);
        }
    }

    private void updateLiveImagePreview(String path) {
        View cardPreview = findViewById(R.id.card_media_preview_container);
        ImageView ivThumb = findViewById(R.id.iv_media_preview_thumb);
        TextView tvStatus = findViewById(R.id.tv_media_selected_status);
        TextView tvName = findViewById(R.id.tv_media_selected_name);

        if (cardPreview == null || path == null || path.trim().isEmpty()) {
            if (cardPreview != null) cardPreview.setVisibility(View.GONE);
            return;
        }

        String cleanPath = path.trim();
        cardPreview.setVisibility(View.VISIBLE);

        if (tvStatus != null) tvStatus.setText("✓ Imagen / Ícono seleccionado con éxito");
        if (tvName != null) tvName.setText(cleanPath);

        if (ivThumb != null) {
            AdminNewsEditActivity.loadNewsImageIntoView(this, ivThumb, cleanPath);
        }
    }

    private void showResourcePickerDialog() {
        DialogHelper.showResourcePickerDialog(this, (resName, displayLabel) -> {
            if (etMedia != null) {
                etMedia.setText(resName);
                Toast.makeText(this, "✓ Imagen/Ícono seleccionado con éxito: " + resName, Toast.LENGTH_SHORT).show();
            }
            updateLiveImagePreview(resName);
        }, () -> {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Seleccionar archivo de imagen"), REQUEST_PICK_IMAGE);
            } catch (Exception e) {
                Toast.makeText(this, "Error al abrir el selector de archivos del dispositivo", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            String localPath = saveImageToInternalStorage(imageUri);
            if (localPath != null && !localPath.isEmpty()) {
                if (etMedia != null) {
                    etMedia.setText(localPath);
                    Toast.makeText(this, "✓ Foto integrada con éxito", Toast.LENGTH_SHORT).show();
                }
                updateLiveImagePreview(localPath);
            } else {
                if (etMedia != null) {
                    etMedia.setText(imageUri.toString());
                }
                updateLiveImagePreview(imageUri.toString());
            }
        }
    }

    private String saveImageToInternalStorage(Uri sourceUri) {
        if (sourceUri == null) return null;
        try {
            File dir = new File(getFilesDir(), "news_images");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "block_img_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(dir, fileName);

            try (InputStream in = getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("COLUA_CMS", "Error copiando imagen a almacenamiento interno", e);
            return null;
        }
    }

    private void loadBlock() {
        new Thread(() -> {
            block = repository.getBlockById(blockId);
            runOnUiThread(() -> {
                if (block != null) {
                    etTitle.setText(block.title != null ? block.title : "");
                    etContent.setText(block.content != null ? block.content : "");
                    if (etBenefitsList != null) etBenefitsList.setText(block.fontSize != null && block.fontSize.startsWith("BENEFITS:") ? block.fontSize.replace("BENEFITS:", "") : "");
                    if (etHighlightAmount != null) etHighlightAmount.setText(block.textColor != null && block.textColor.startsWith("MOUNT:") ? block.textColor.replace("MOUNT:", "") : "");
                    etMedia.setText(block.mediaPath != null ? block.mediaPath : "");
                    if (block.mediaPath != null && !block.mediaPath.trim().isEmpty()) {
                        updateLiveImagePreview(block.mediaPath);
                    }
                    etButtonText.setText(block.buttonText != null ? block.buttonText : "");
                    etButtonAction.setText(block.buttonAction != null ? block.buttonAction : "");

                    if ("CENTER".equalsIgnoreCase(block.alignment)) {
                        rbCenter.setChecked(true);
                    } else if ("RIGHT".equalsIgnoreCase(block.alignment)) {
                        rbRight.setChecked(true);
                    } else {
                        rbLeft.setChecked(true);
                    }

                    String type = block.type != null ? block.type : "TEXT";
                    selectedBlockTypeKey = type;
                    updateElementTypeHeaderAndForm(type);
                    
                    TextView tvLabel = findViewById(R.id.tv_selected_element_type_label);
                    if (tvLabel != null) {
                        tvLabel.setText("Tipo actual: " + type);
                    }
                    updateLiveActionSummary();
                }
            });
        }).start();
    }

    private void saveBlock(boolean openPreview) {
        if (block == null) {
            block = new ContentBlockEntity();
            block.id = UUID.randomUUID().toString();
            block.sectionId = sectionId != null ? sectionId : "sec_home";
        }

        String selectedType = selectedBlockTypeKey != null ? selectedBlockTypeKey : "TEXT";

        block.type = selectedType;
        String titleInput = etTitle.getText().toString().trim();
        String btnText = etButtonText.getText().toString().trim();

        if (titleInput.isEmpty()) {
            if ("BUTTON".equalsIgnoreCase(selectedType)) {
                titleInput = !btnText.isEmpty() ? "Botón: " + btnText : "Botón CTA";
            } else if ("IMAGE".equalsIgnoreCase(selectedType) || "BANNER".equalsIgnoreCase(selectedType)) {
                titleInput = "Imagen / Banner";
            } else if ("ICON".equalsIgnoreCase(selectedType)) {
                titleInput = "Ícono de Galería";
            } else if ("CONTAINER".equalsIgnoreCase(selectedType)) {
                titleInput = "Sección / Contenedor";
            } else {
                titleInput = "Bloque de Texto";
            }
        }

        block.title = titleInput;
        block.content = etContent.getText().toString().trim();
        if (etBenefitsList != null) block.fontSize = "BENEFITS:" + etBenefitsList.getText().toString().trim();
        if (etHighlightAmount != null) block.textColor = "MOUNT:" + etHighlightAmount.getText().toString().trim();
        block.mediaPath = etMedia.getText().toString().trim();

        if ("BUTTON".equalsIgnoreCase(selectedType) || "PRODUCT_CARD".equalsIgnoreCase(selectedType) || "CARD".equalsIgnoreCase(selectedType) || "CONTAINER".equalsIgnoreCase(selectedType)) {
            block.buttonText = btnText;
            int actionIndex = spinnerActionType != null ? spinnerActionType.getSelectedItemPosition() : 0;
            String rawActionValue = etButtonAction != null ? etButtonAction.getText().toString().trim() : "";

            if (actionIndex == 1 && !rawActionValue.startsWith("tel:")) {
                rawActionValue = "tel:" + rawActionValue.replaceAll("[^0-9+]", "");
            } else if (actionIndex == 3 && !rawActionValue.startsWith("http")) {
                rawActionValue = "https://wa.me/502" + rawActionValue.replaceAll("[^0-9]", "");
            }
            block.buttonAction = rawActionValue;
        } else {
            block.buttonText = "";
            block.buttonAction = "";
        }

        if (rbCenter.isChecked()) {
            block.alignment = "CENTER";
        } else if (rbRight.isChecked()) {
            block.alignment = "RIGHT";
        } else {
            block.alignment = "LEFT";
        }

        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();

        final String targetSec = block.sectionId;

        Log.d("COLUA_CMS", "Saving block id=" + block.id + " type=" + block.type + " sectionId=" + targetSec + " openPreview=" + openPreview);

        new Thread(() -> {
            repository.insertBlock(block);
            runOnUiThread(() -> {
                Toast.makeText(this, "Elemento guardado en borrador", Toast.LENGTH_SHORT).show();
                if (openPreview) {
                    Class<?> target = DynamicSectionActivity.class;
                    if ("sec_home".equalsIgnoreCase(targetSec)) target = MainActivity.class;
                    else if ("sec_agencias".equalsIgnoreCase(targetSec)) target = AgenciasActivity.class;

                    Intent intent = new Intent(this, target);
                    intent.putExtra("extra_section_id", targetSec);
                    intent.putExtra("extra_visual_edit_mode", false);
                    intent.putExtra("extra_is_preview_mode", true);
                    startActivity(intent);
                }
                finish();
            });
        }).start();
    }
}
