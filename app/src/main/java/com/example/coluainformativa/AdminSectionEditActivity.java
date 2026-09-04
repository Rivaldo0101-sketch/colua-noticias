package com.example.coluainformativa;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminSectionEditActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";

    private ColuaRepository repository;
    private String sectionId;
    private SectionEntity section;

    private EditText etTitle, etDescription, etColor, etButtonLinkText;
    private TextView tvGeneratedRoutePreview, tvSelectedIconName, tvNavLocationExplanation, tvHeader;
    private ImageView ivSelectedIconPreview;
    private MaterialCardView cardSelectIcon;
    private ChipGroup cgColors;
    private RadioGroup rgNavLocation;
    private View layoutButtonLinkOptions;
    private Spinner spinnerParentScreen;
    private MaterialSwitch switchVisible;

    private String selectedIconName = "ic_star";
    private String selectedIconDisplayName = "General / Otro";
    private String generatedSlug = "sec_nueva_pantalla";
    private List<SectionEntity> existingSections = new ArrayList<>();

    // Galería de íconos predeterminados e incluidos en la app
    private static class IconOption {
        String displayName;
        String resName;
        IconOption(String displayName, String resName) {
            this.displayName = displayName;
            this.resName = resName;
        }
    }

    private List<IconOption> iconGallery = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_section_edit);

        repository = new ColuaRepository(this);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.coordinator_section_edit),
                findViewById(R.id.app_bar_section_edit),
                null,
                findViewById(R.id.scroll_section_edit_content)
        );

        tvHeader = findViewById(R.id.tv_section_edit_header);
        etTitle = findViewById(R.id.et_section_title);
        tvGeneratedRoutePreview = findViewById(R.id.tv_generated_route_preview);
        etDescription = findViewById(R.id.et_section_description);
        
        cardSelectIcon = findViewById(R.id.card_select_icon);
        ivSelectedIconPreview = findViewById(R.id.iv_selected_icon_preview);
        tvSelectedIconName = findViewById(R.id.tv_selected_icon_name);

        etColor = findViewById(R.id.et_section_color);
        cgColors = findViewById(R.id.cg_section_colors);

        rgNavLocation = findViewById(R.id.rg_nav_location);
        tvNavLocationExplanation = findViewById(R.id.tv_nav_location_explanation);
        layoutButtonLinkOptions = findViewById(R.id.layout_button_link_options);
        spinnerParentScreen = findViewById(R.id.spinner_parent_screen);
        etButtonLinkText = findViewById(R.id.et_button_link_text);

        switchVisible = findViewById(R.id.switch_section_visible);

        findViewById(R.id.btn_back_section_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_section_draft).setOnClickListener(v -> createSectionAndOpenEditor());

        setupIconGallery();
        setupAutoSlugGenerator();
        setupColorSelector();
        setupNavLocationSelector();
        cardSelectIcon.setOnClickListener(v -> showIconSelectorDialog());

        loadExistingSectionsAndSetup();
    }

    private void setupIconGallery() {
        iconGallery.clear();
        iconGallery.add(new IconOption("Ahorros", "ahorros"));
        iconGallery.add(new IconOption("Crédito", "credito"));
        iconGallery.add(new IconOption("Seguros", "seguro"));
        iconGallery.add(new IconOption("Remesas", "remesa"));
        iconGallery.add(new IconOption("Agencias", "ubicacion"));
        iconGallery.add(new IconOption("Servicios digitales", "servicios_digitales"));
        iconGallery.add(new IconOption("Beneficios", "beneficios"));
        iconGallery.add(new IconOption("Noticias", "noticias_colua"));
        iconGallery.add(new IconOption("Comunidad", "grupo"));
        iconGallery.add(new IconOption("Educación", "public_service"));
        iconGallery.add(new IconOption("Sostenibilidad", "sostenibilidad_cooperativa"));
        iconGallery.add(new IconOption("Teléfono", "ic_phone"));
        iconGallery.add(new IconOption("Información", "ic_info"));
        iconGallery.add(new IconOption("General / Otro", "ic_star"));
    }

    private void setupAutoSlugGenerator() {
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                if (input.isEmpty()) {
                    generatedSlug = "sec_nueva_pantalla";
                } else {
                    String normalized = Normalizer.normalize(input.toLowerCase(Locale.getDefault()), Normalizer.Form.NFD);
                    String clean = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                            .replaceAll("[^a-z0-9]", "_")
                            .replaceAll("_+", "_");
                    generatedSlug = "sec_" + clean;
                }
                tvGeneratedRoutePreview.setText("Ruta automática: /" + generatedSlug);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupColorSelector() {
        cgColors.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_sec_blue) etColor.setText("#173789");
            else if (id == R.id.chip_sec_orange) etColor.setText("#EF8819");
            else if (id == R.id.chip_sec_lila) etColor.setText("#E42A67");
            else if (id == R.id.chip_sec_green) etColor.setText("#59B8A4");
            else if (id == R.id.chip_sec_purple) etColor.setText("#634794");
        });
    }

    private void setupNavLocationSelector() {
        rgNavLocation.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_nav_side) {
                tvNavLocationExplanation.setText("Explicación: Toda pantalla nueva aparece por defecto al final del menú lateral.");
                layoutButtonLinkOptions.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_nav_bottom) {
                tvNavLocationExplanation.setText("Explicación: Aparecerá en la barra fija de navegación inferior de 5 botones.");
                layoutButtonLinkOptions.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_nav_top) {
                tvNavLocationExplanation.setText("Explicación: Aparecerá en el acceso superior del encabezado.");
                layoutButtonLinkOptions.setVisibility(View.GONE);
            } else if (checkedId == R.id.rb_nav_button_link) {
                tvNavLocationExplanation.setText("Explicación: Creará automáticamente un botón en la pantalla elegida que abrirá esta nueva página.");
                layoutButtonLinkOptions.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_nav_none) {
                tvNavLocationExplanation.setText("Explicación: Página interna sin acceso automático. Podrás enlazarla manualmente desde un botón.");
                layoutButtonLinkOptions.setVisibility(View.GONE);
            }
        });
    }

    private void loadExistingSectionsAndSetup() {
        new Thread(() -> {
            existingSections = repository.getAllSections();
            Collections.sort(existingSections, (s1, s2) -> Integer.compare(s1.displayOrder, s2.displayOrder));

            List<String> screenNames = new ArrayList<>();
            for (SectionEntity s : existingSections) {
                screenNames.add(s.title + " (/" + (s.slug != null && !s.slug.isEmpty() ? s.slug : s.id) + ")");
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(AdminSectionEditActivity.this,
                        android.R.layout.simple_spinner_item, screenNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerParentScreen.setAdapter(adapter);

                if (sectionId != null && !sectionId.isEmpty()) {
                    tvHeader.setText("Editar Pantalla");
                    loadSectionDataForEditing();
                } else {
                    tvHeader.setText("Crear Nueva Pantalla");
                    section = new SectionEntity();
                    section.accentColor = "#173789";
                    section.isVisible = true;
                    section.isPublished = false;
                }
            });
        }).start();
    }

    private void loadSectionDataForEditing() {
        for (SectionEntity s : existingSections) {
            if (s.id.equals(sectionId)) {
                section = s;
                break;
            }
        }

        if (section != null) {
            etTitle.setText(section.title);
            etDescription.setText(section.description);
            etColor.setText(section.accentColor);
            switchVisible.setChecked(section.isVisible);

            selectedIconName = section.iconName;
            int resId = getResources().getIdentifier(selectedIconName, "drawable", getPackageName());
            if (resId != 0) {
                ivSelectedIconPreview.setImageResource(resId);
                tvSelectedIconName.setText(selectedIconName);
            }
        }
    }

    private void showIconSelectorDialog() {
        RecyclerView rvGrid = new RecyclerView(this);
        rvGrid.setPadding(24, 24, 24, 24);
        rvGrid.setLayoutManager(new GridLayoutManager(this, 3));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Seleccionar Ícono de Navegación")
                .setView(rvGrid)
                .setNegativeButton("Cancelar", null)
                .create();

        rvGrid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_selector, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                IconOption opt = iconGallery.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tv_grid_icon_name);
                ImageView ivImg = holder.itemView.findViewById(R.id.iv_grid_icon_image);

                tvName.setText(opt.displayName);
                int res = getResources().getIdentifier(opt.resName, "drawable", getPackageName());
                if (res != 0) ivImg.setImageResource(res);

                holder.itemView.setOnClickListener(v -> {
                    selectedIconName = opt.resName;
                    selectedIconDisplayName = opt.displayName;
                    ivSelectedIconPreview.setImageResource(res != 0 ? res : R.drawable.ic_star);
                    tvSelectedIconName.setText(opt.displayName);
                    dialog.dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return iconGallery.size();
            }
        });

        dialog.show();
    }

    private void createSectionAndOpenEditor() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "El nombre de la pantalla es obligatorio", Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }

        new Thread(() -> {
            int nextOrder = existingSections.size() + 1;
            String newDocId = (sectionId != null && !sectionId.isEmpty()) ? sectionId : generatedSlug;

            section.id = newDocId;
            section.title = title;
            section.slug = generatedSlug.replace("sec_", "");
            section.description = etDescription.getText().toString().trim();
            section.iconName = selectedIconName;
            section.accentColor = etColor.getText().toString().trim();
            section.displayOrder = nextOrder;
            section.isVisible = switchVisible.isChecked();
            section.isPublished = false; // BORRADOR
            section.updatedAt = System.currentTimeMillis();

            // Guardar Pantalla Borrador
            repository.insertSection(section);

            // Determinar ubicación de navegación
            int checkedNavId = rgNavLocation.getCheckedRadioButtonId();
            String navType = "SIDEBAR";
            if (checkedNavId == R.id.rb_nav_bottom) navType = "BOTTOM_NAV";
            else if (checkedNavId == R.id.rb_nav_top) navType = "NAVBAR";
            else if (checkedNavId == R.id.rb_nav_none) navType = "NONE";
            else if (checkedNavId == R.id.rb_nav_button_link) navType = "BUTTON_LINK";

            if (!"NONE".equals(navType) && !"BUTTON_LINK".equals(navType)) {
                if ("NAVBAR".equals(navType)) {
                    List<NavigationItemEntity> sideItems = repository.getVisibleNavigation("SIDEBAR");
                    boolean nosotrosInSide = sideItems.stream().anyMatch(n -> "sec_nosotros".equalsIgnoreCase(n.targetSectionId) || "side_nosotros".equalsIgnoreCase(n.id));
                    if (!nosotrosInSide) {
                        NavigationItemEntity nosotrosNav = new NavigationItemEntity("side_nosotros", "Nosotros", "public_service", "sec_nosotros", "SIDEBAR", 99, true);
                        repository.insertNavigationItem(nosotrosNav);
                    }
                } else if ("BOTTOM_NAV".equals(navType)) {
                    List<NavigationItemEntity> currentBottom = repository.getVisibleNavigation("BOTTOM_NAV");
                    if (currentBottom.size() >= 5) {
                        NavigationItemEntity itemToDisplace = null;
                        for (NavigationItemEntity item : currentBottom) {
                            if (!"sec_home".equalsIgnoreCase(item.targetSectionId) && !"nav_home".equalsIgnoreCase(item.id)) {
                                itemToDisplace = item;
                                break;
                            }
                        }
                        if (itemToDisplace != null) {
                            itemToDisplace.type = "SIDEBAR";
                            repository.insertNavigationItem(itemToDisplace);
                        }
                    }
                }

                NavigationItemEntity navItem = new NavigationItemEntity(
                        "nav_" + section.id,
                        section.title,
                        selectedIconName,
                        section.id,
                        navType,
                        nextOrder,
                        section.isVisible
                );
                repository.insertNavigationItem(navItem);
            } else if ("BUTTON_LINK".equals(navType) && !existingSections.isEmpty()) {
                // Crear automáticamente el botón de navegación dentro de la pantalla seleccionada
                int selectedParentIndex = spinnerParentScreen.getSelectedItemPosition();
                if (selectedParentIndex >= 0 && selectedParentIndex < existingSections.size()) {
                    SectionEntity parentSection = existingSections.get(selectedParentIndex);
                    String btnText = etButtonLinkText.getText().toString().trim();
                    if (btnText.isEmpty()) btnText = "Abrir " + section.title;

                    ContentItemEntity buttonItem = new ContentItemEntity();
                    buttonItem.id = "btn_" + UUID.randomUUID().toString().substring(0, 8);
                    buttonItem.sectionId = parentSection.id;
                    buttonItem.title = btnText;
                    buttonItem.subtitle = "BUTTON";
                    buttonItem.shortDescription = section.description;
                    buttonItem.accentColor = section.accentColor;
                    buttonItem.iconName = selectedIconName;
                    buttonItem.targetSectionId = section.id;
                    buttonItem.displayOrder = 100;
                    buttonItem.isVisible = true;
                    buttonItem.isDraft = true;

                    repository.insertItem(buttonItem);
                }
            }

            // Marcar estado con cambios sin publicar
            getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_unpublished_changes", true)
                    .apply();

            runOnUiThread(() -> {
                Toast.makeText(this, "Pantalla creada como borrador. Ahora agrega el contenido que deseas mostrar.", Toast.LENGTH_LONG).show();
                
                // Abrir directamente el Editor de Contenido de la nueva pantalla vacía
                Intent intent = new Intent(AdminSectionEditActivity.this, AdminContentListActivity.class);
                intent.putExtra(AdminContentListActivity.EXTRA_SECTION_ID, section.id);
                startActivity(intent);
                finish();
            });
        }).start();
    }
}
