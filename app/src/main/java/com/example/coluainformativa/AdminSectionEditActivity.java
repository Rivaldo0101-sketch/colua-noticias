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
        String iconKey;
        String category;

        IconOption(String displayName, String resName, String iconKey, String category) {
            this.displayName = displayName;
            this.resName = resName;
            this.iconKey = iconKey;
            this.category = category;
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

        // 1. Finanzas & Productos
        iconGallery.add(new IconOption("Ahorros / Alcancía", "ic_savings", "savings", "Finanzas"));
        iconGallery.add(new IconOption("Ahorros (PNG)", "ahorros", "ahorros_png", "Finanzas"));
        iconGallery.add(new IconOption("Ahorro Infanto Juvenil", "ahorro_infanto_juvenil", "ahorro_infanto_juvenil", "Finanzas"));
        iconGallery.add(new IconOption("Ahorro Programado", "ahorro_programado", "ahorro_programado", "Finanzas"));
        iconGallery.add(new IconOption("Tarjetas de Crédito", "ic_credit_card", "credit_card", "Finanzas"));
        iconGallery.add(new IconOption("Cuentas / Banco", "ic_account_balance", "account_balance", "Finanzas"));
        iconGallery.add(new IconOption("Créditos / Finanzas", "credito", "credito", "Finanzas"));
        iconGallery.add(new IconOption("Credi Consumo", "credito_consumo", "credito_consumo", "Finanzas"));
        iconGallery.add(new IconOption("Credi Vehículo", "credi_vehiculo", "credi_vehiculo", "Finanzas"));
        iconGallery.add(new IconOption("Crédito Productivo", "credito_productivo", "credito_productivo", "Finanzas"));
        iconGallery.add(new IconOption("Crédito Vivienda", "credito_vivienda", "credito_vivienda", "Finanzas"));
        iconGallery.add(new IconOption("Remesas & Envíos", "ic_send", "send", "Finanzas"));
        iconGallery.add(new IconOption("Remesas (PNG)", "remesa", "remesa_png", "Finanzas"));
        iconGallery.add(new IconOption("Inversión / Crecimiento", "ic_trending_up", "trending_up", "Finanzas"));
        iconGallery.add(new IconOption("Simulador / Calculadora", "ic_calculate", "calculate", "Finanzas"));
        iconGallery.add(new IconOption("Comprobantes / Pagos", "ic_receipt", "receipt", "Finanzas"));

        // 2. Servicios & Canales
        iconGallery.add(new IconOption("Agencias & Puntos", "ic_location", "location", "Servicios"));
        iconGallery.add(new IconOption("Agencias (PNG)", "ubicacion", "ubicacion_png", "Servicios"));
        iconGallery.add(new IconOption("Punto de Agencia", "ic_agencias_pin", "agencias_pin", "Servicios"));
        iconGallery.add(new IconOption("Cajeros & Agentes", "ic_atm", "atm", "Servicios"));
        iconGallery.add(new IconOption("Servicios Digitales", "ic_phone_android", "phone_android", "Servicios"));
        iconGallery.add(new IconOption("Servicios Digitales (PNG)", "servicios_digitales", "servicios_digitales_png", "Servicios"));
        iconGallery.add(new IconOption("Atención PBX", "ic_phone", "phone", "Servicios"));
        iconGallery.add(new IconOption("Soporte & Ayuda", "ic_support", "support", "Servicios"));
        iconGallery.add(new IconOption("Tienda / Comercios", "ic_store", "store", "Servicios"));
        iconGallery.add(new IconOption("Beneficios (PNG)", "beneficios", "beneficios_png", "Servicios"));

        // 3. Seguridad & Protección
        iconGallery.add(new IconOption("Seguros & Protección", "ic_security", "security", "Seguridad"));
        iconGallery.add(new IconOption("Seguro (PNG)", "seguro", "seguro_png", "Seguridad"));
        iconGallery.add(new IconOption("Seguro Accidentes Juvenil", "seguro_accidentes_infanto_juvenil", "seguro_accidentes_juvenil", "Seguridad"));
        iconGallery.add(new IconOption("Seguro CV Personal", "seguro_cv_personal", "seguro_cv_personal", "Seguridad"));
        iconGallery.add(new IconOption("Seguro de Cáncer", "seguro_de_cancer", "seguro_de_cancer", "Seguridad"));
        iconGallery.add(new IconOption("Seguro de Vida", "seguro_de_vida_individual_o_familar", "seguro_de_vida", "Seguridad"));
        iconGallery.add(new IconOption("Seguro Edad de Oro", "seguro_edad_de_oro", "seguro_edad_de_oro", "Seguridad"));
        iconGallery.add(new IconOption("Seguro Vida Saludable", "seguro_vida_saludable", "seguro_vida_saludable", "Seguridad"));
        iconGallery.add(new IconOption("Manejo Seguro", "manejo_seguro", "manejo_seguro", "Seguridad"));
        iconGallery.add(new IconOption("Salud & Vida", "ic_health", "health", "Seguridad"));
        iconGallery.add(new IconOption("Hospital & Asistencia", "ic_hospital", "hospital", "Seguridad"));
        iconGallery.add(new IconOption("Protección Familiar", "ic_family_restroom", "family_restroom", "Seguridad"));
        iconGallery.add(new IconOption("Seguridad & Claves", "ic_lock", "lock", "Seguridad"));
        iconGallery.add(new IconOption("Garantía & Certificado", "ic_verified", "verified", "Seguridad"));

        // 4. Comunidad & Sostenibilidad
        iconGallery.add(new IconOption("Comunidad / Asociados", "ic_groups", "groups", "Comunidad"));
        iconGallery.add(new IconOption("Comunidad (PNG)", "grupo", "grupo_png", "Comunidad"));
        iconGallery.add(new IconOption("Sostenibilidad", "ic_volunteer_activism", "volunteer_activism", "Comunidad"));
        iconGallery.add(new IconOption("Sostenibilidad Coope", "sostenibilidad_cooperativa", "sostenibilidad_cooperativa", "Comunidad"));
        iconGallery.add(new IconOption("Convenios & Alianzas", "ic_handshake", "handshake", "Comunidad"));
        iconGallery.add(new IconOption("Educación Cooperativa", "ic_school", "school", "Comunidad"));
        iconGallery.add(new IconOption("Servicio Público / Edu", "public_service", "public_service", "Comunidad"));
        iconGallery.add(new IconOption("Proyectos & Empleo", "ic_work", "work", "Comunidad"));
        iconGallery.add(new IconOption("Empresas & Negocios", "ic_business", "business", "Comunidad"));
        iconGallery.add(new IconOption("Agro & Desarrollo Rural", "ic_agriculture", "agriculture", "Comunidad"));

        // 5. Novedades & General
        iconGallery.add(new IconOption("Noticias & Boletín", "ic_newspaper", "newspaper", "General"));
        iconGallery.add(new IconOption("Noticias COLUA (PNG)", "noticias_colua", "noticias_colua_png", "General"));
        iconGallery.add(new IconOption("Beneficios & Ofertas", "ic_local_offer", "local_offer", "General"));
        iconGallery.add(new IconOption("Inicio / Principal", "ic_home", "home", "General"));
        iconGallery.add(new IconOption("Inicio (PNG)", "inicio", "inicio_png", "General"));
        iconGallery.add(new IconOption("Especial / Destacado", "ic_star", "star", "General"));
        iconGallery.add(new IconOption("Información / Nosotros", "ic_info", "info", "General"));
        iconGallery.add(new IconOption("Alertas & Avisos", "ic_notifications", "notifications", "General"));
        iconGallery.add(new IconOption("Campañas & Anuncios", "ic_campaign", "campaign", "General"));
        iconGallery.add(new IconOption("Innovación & Ideas", "ic_lightbulb", "lightbulb", "General"));
        iconGallery.add(new IconOption("Eventos & Calendario", "ic_event", "event", "General"));
        iconGallery.add(new IconOption("Mi Perfil / Usuario", "ic_person", "person", "General"));
        iconGallery.add(new IconOption("Perfil (PNG)", "perfil", "perfil_png", "General"));
        iconGallery.add(new IconOption("Portal Admin (PNG)", "portal_administrativo", "portal_administrativo", "General"));
        iconGallery.add(new IconOption("Instrucciones / Guía", "instrucciones", "instrucciones", "General"));
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

    private boolean isRevertingNavSelection = false;

    private void setupNavLocationSelector() {
        rgNavLocation.setOnCheckedChangeListener((group, checkedId) -> {
            if (isRevertingNavSelection) return;

            if (checkedId == R.id.rb_nav_bottom) {
                new Thread(() -> {
                    List<NavigationItemEntity> currentBottom = repository.getVisibleNavigation("BOTTOM_NAV");
                    long count = currentBottom.stream().filter(item ->
                            section == null || section.id == null || !item.targetSectionId.equalsIgnoreCase(section.id)
                    ).count();

                    if (count >= 5) {
                        runOnUiThread(() -> {
                            isRevertingNavSelection = true;
                            rgNavLocation.check(R.id.rb_nav_side);
                            isRevertingNavSelection = false;

                            tvNavLocationExplanation.setText("Explicación: Toda pantalla nueva aparece por defecto al final del menú lateral.");
                            layoutButtonLinkOptions.setVisibility(View.GONE);

                            new AlertDialog.Builder(AdminSectionEditActivity.this)
                                    .setTitle("⚠️ Barra Inferior Llena (5/5)")
                                    .setMessage("No es posible agregar esta pantalla a la barra inferior porque ya contiene el máximo permitido de 5 elementos.\n\nRetira o cambia de ubicación una pantalla de la barra inferior primero para liberar espacio.")
                                    .setPositiveButton("Entendido", null)
                                    .show();
                        });
                    } else {
                        runOnUiThread(() -> {
                            tvNavLocationExplanation.setText("Explicación: Aparecerá en la barra fija de navegación inferior de 5 botones (Espacio disponible: " + (5 - count) + "/5).");
                            layoutButtonLinkOptions.setVisibility(View.GONE);
                        });
                    }
                }).start();

            } else if (checkedId == R.id.rb_nav_top) {
                new Thread(() -> {
                    List<NavigationItemEntity> currentTop = repository.getVisibleNavigation("NAVBAR");
                    if (currentTop.isEmpty()) {
                        currentTop = repository.getVisibleNavigation("TOP_ACTION");
                    }
                    long count = currentTop.stream().filter(item ->
                            section == null || section.id == null || !item.targetSectionId.equalsIgnoreCase(section.id)
                    ).count();

                    if (count >= 1) {
                        runOnUiThread(() -> {
                            isRevertingNavSelection = true;
                            rgNavLocation.check(R.id.rb_nav_side);
                            isRevertingNavSelection = false;

                            tvNavLocationExplanation.setText("Explicación: Toda pantalla nueva aparece por defecto al final del menú lateral.");
                            layoutButtonLinkOptions.setVisibility(View.GONE);

                            new AlertDialog.Builder(AdminSectionEditActivity.this)
                                    .setTitle("⚠️ Acceso Superior Ocupado (1/1)")
                                    .setMessage("El acceso superior del encabezado ya está ocupado por otra pantalla.\n\nPara asignar esta pantalla aquí, primero debes retirar la otra pantalla del acceso superior.")
                                    .setPositiveButton("Entendido", null)
                                    .show();
                        });
                    } else {
                        runOnUiThread(() -> {
                            tvNavLocationExplanation.setText("Explicación: Aparecerá en el acceso superior del encabezado (Disponible: 1/1).");
                            layoutButtonLinkOptions.setVisibility(View.GONE);
                        });
                    }
                }).start();

            } else if (checkedId == R.id.rb_nav_button_link) {
                tvNavLocationExplanation.setText("Explicación: Creará automáticamente un botón en la pantalla elegida que abrirá esta nueva página.");
                layoutButtonLinkOptions.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_nav_none) {
                tvNavLocationExplanation.setText("Explicación: Página interna sin acceso automático. Podrás enlazarla manualmente desde un botón.");
                layoutButtonLinkOptions.setVisibility(View.GONE);
            } else {
                tvNavLocationExplanation.setText("Explicación: Toda pantalla nueva aparece por defecto al final del menú lateral.");
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
                if (selectedIconName != null && selectedIconName.startsWith("ic_")) {
                    ivSelectedIconPreview.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                } else {
                    ivSelectedIconPreview.setImageTintList(null);
                }
                tvSelectedIconName.setText(selectedIconName);
            }
        }
    }

    private void showIconSelectorDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_icon_selector, null);
        EditText etSearch = dialogView.findViewById(R.id.et_search_icons);
        ChipGroup cgCategories = dialogView.findViewById(R.id.chip_group_icon_categories);
        RecyclerView rvGrid = dialogView.findViewById(R.id.rv_icon_grid);

        rvGrid.setLayoutManager(new GridLayoutManager(this, 3));

        List<IconOption> filteredList = new ArrayList<>(iconGallery);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Cerrar", null)
                .create();

        RecyclerView.Adapter adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_selector, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                IconOption opt = filteredList.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tv_grid_icon_name);
                TextView tvKey = holder.itemView.findViewById(R.id.tv_grid_icon_key);
                ImageView ivImg = holder.itemView.findViewById(R.id.iv_grid_icon_image);

                tvName.setText(opt.displayName);
                if (tvKey != null) tvKey.setText("key: " + opt.iconKey);

                int res = getResources().getIdentifier(opt.resName, "drawable", getPackageName());
                if (res != 0) {
                    ivImg.setImageResource(res);
                    if (opt.resName.startsWith("ic_")) {
                        ivImg.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                    } else {
                        ivImg.setImageTintList(null);
                    }
                } else {
                    ivImg.setImageResource(R.drawable.ic_star);
                    ivImg.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                }

                holder.itemView.setOnClickListener(v -> {
                    selectedIconName = opt.resName;
                    selectedIconDisplayName = opt.displayName;
                    ivSelectedIconPreview.setImageResource(res != 0 ? res : R.drawable.ic_star);
                    if (opt.resName.startsWith("ic_")) {
                        ivSelectedIconPreview.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                    } else {
                        ivSelectedIconPreview.setImageTintList(null);
                    }
                    tvSelectedIconName.setText(opt.displayName + " (key: " + opt.iconKey + ")");
                    dialog.dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return filteredList.size();
            }
        };

        rvGrid.setAdapter(adapter);

        Runnable applyFilter = () -> {
            String query = etSearch != null ? etSearch.getText().toString().trim().toLowerCase(Locale.getDefault()) : "";
            int checkedId = cgCategories != null && !cgCategories.getCheckedChipIds().isEmpty() ? cgCategories.getCheckedChipIds().get(0) : R.id.chip_cat_todos;

            String selectedCategory = "Todos";
            if (checkedId == R.id.chip_cat_finanzas) selectedCategory = "Finanzas";
            else if (checkedId == R.id.chip_cat_servicios) selectedCategory = "Servicios";
            else if (checkedId == R.id.chip_cat_seguridad) selectedCategory = "Seguridad";
            else if (checkedId == R.id.chip_cat_comunidad) selectedCategory = "Comunidad";
            else if (checkedId == R.id.chip_cat_general) selectedCategory = "General";

            filteredList.clear();
            for (IconOption opt : iconGallery) {
                boolean matchesCategory = "Todos".equals(selectedCategory) || selectedCategory.equalsIgnoreCase(opt.category);
                boolean matchesQuery = query.isEmpty()
                        || opt.displayName.toLowerCase(Locale.getDefault()).contains(query)
                        || opt.iconKey.toLowerCase(Locale.getDefault()).contains(query)
                        || opt.category.toLowerCase(Locale.getDefault()).contains(query);

                if (matchesCategory && matchesQuery) {
                    filteredList.add(opt);
                }
            }
            adapter.notifyDataSetChanged();
        };

        if (cgCategories != null) {
            cgCategories.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter.run());
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter.run();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

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

            // Determinar ubicación de navegación
            int checkedNavId = rgNavLocation.getCheckedRadioButtonId();
            String navType = "SIDEBAR";
            if (checkedNavId == R.id.rb_nav_bottom) navType = "BOTTOM_NAV";
            else if (checkedNavId == R.id.rb_nav_top) navType = "NAVBAR";
            else if (checkedNavId == R.id.rb_nav_none) navType = "NONE";
            else if (checkedNavId == R.id.rb_nav_button_link) navType = "BUTTON_LINK";

            if ("BOTTOM_NAV".equalsIgnoreCase(navType)) {
                List<NavigationItemEntity> currentBottom = repository.getVisibleNavigation("BOTTOM_NAV");
                long count = currentBottom.stream().filter(item ->
                        section == null || section.id == null || !item.targetSectionId.equalsIgnoreCase(section.id)
                ).count();
                if (count >= 5) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(AdminSectionEditActivity.this)
                                .setTitle("⚠️ Barra Inferior Llena (5/5)")
                                .setMessage("No es posible agregar esta pantalla a la barra inferior porque ya contiene el máximo permitido de 5 elementos.\n\nRetira una pantalla de la barra inferior primero.")
                                .setPositiveButton("Entendido", null)
                                .show();
                    });
                    return;
                }
            } else if ("NAVBAR".equalsIgnoreCase(navType)) {
                List<NavigationItemEntity> currentTop = repository.getVisibleNavigation("NAVBAR");
                if (currentTop.isEmpty()) currentTop = repository.getVisibleNavigation("TOP_ACTION");
                long count = currentTop.stream().filter(item ->
                        section == null || section.id == null || !item.targetSectionId.equalsIgnoreCase(section.id)
                ).count();
                if (count >= 1) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(AdminSectionEditActivity.this)
                                .setTitle("⚠️ Acceso Superior Ocupado (1/1)")
                                .setMessage("El acceso superior del encabezado ya está ocupado por otra pantalla.\n\nRetira la pantalla del acceso superior primero.")
                                .setPositiveButton("Entendido", null)
                                .show();
                    });
                    return;
                }
            }

            // Guardar Pantalla Borrador
            repository.insertSection(section);

            if (!"NONE".equals(navType) && !"BUTTON_LINK".equals(navType)) {
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
