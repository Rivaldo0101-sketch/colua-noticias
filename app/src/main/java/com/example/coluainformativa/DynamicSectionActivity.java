package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.ui.content.ContentItemAdapter;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.ui.content.BlockAdapter;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

import android.content.Intent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;

public class DynamicSectionActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_VISUAL_EDIT_MODE = "extra_visual_edit_mode";

    private ColuaRepository repository;
    private String sectionId;
    private boolean isVisualEditMode = false;
    private SectionEntity section;
    private ContentItemAdapter itemAdapter;
    private BlockAdapter blockAdapter;
    private List<ContentItemEntity> itemList = new ArrayList<>();
    private List<ContentItemEntity> paginatedItemList = new ArrayList<>();
    private List<ContentBlockEntity> blockList = new ArrayList<>();
    private List<ContentItemEntity> masterItemList = new ArrayList<>();
    private String currentSearchQuery = "";
    private int currentDateFilter = 0; // 0: All, 1: Last week, 2: This month
    
    private int currentPage = 1;
    private int itemsPerPage = 6; // Un poco más de 5 para llenar mejor la pantalla

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_section);

        repository = new ColuaRepository(this);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);
        isVisualEditMode = getIntent().getBooleanExtra(EXTRA_VISUAL_EDIT_MODE, false);

        if (sectionId == null) {
            finish();
            return;
        }

        if ("sec_home".equalsIgnoreCase(sectionId) || "home".equalsIgnoreCase(sectionId)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return;
        }



        loadSectionData();
        setupRecyclerView();
        setupNavigation();
        setupNavbar();
        setupDrawer();
        setupPreviewBanner();

        AppBarLayout appBarLayout = findViewById(R.id.app_bar_dynamic);
        if (appBarLayout != null) {
            appBarLayout.setLiftOnScroll(false);
        }
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colua_navy));
        }

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.app_bar_dynamic),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.scroll_content_dynamic)
        );

        View layoutSearchFilter = findViewById(R.id.layout_news_search_filter);
        if ("sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
            if (layoutSearchFilter != null) layoutSearchFilter.setVisibility(View.VISIBLE);
            
            EditText etSearch = findViewById(R.id.et_news_search);
            if (etSearch != null) {
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        currentSearchQuery = s.toString();
                        applyNewsFilter();
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }

            MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggle_group_date_filter);
            if (toggleGroup != null) {
                toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (isChecked) {
                        if (checkedId == R.id.btn_filter_all) currentDateFilter = 0;
                        else if (checkedId == R.id.btn_filter_recent) currentDateFilter = 1;
                        else if (checkedId == R.id.btn_filter_month) currentDateFilter = 2;
                        applyNewsFilter();
                    }
                });
                toggleGroup.check(R.id.btn_filter_all);
            }
        } else {
            if (layoutSearchFilter != null) layoutSearchFilter.setVisibility(View.GONE);
        }

        // Escuchar actualizaciones de configuración publicada silenciosamente en segundo plano
        repository.subscribeToPublishedConfig(newVersion -> Unit.INSTANCE);
    }

    private void setupPreviewBanner() {
        boolean isPreviewMode = getIntent().getBooleanExtra("extra_is_preview_mode", false) || isVisualEditMode;
        View layoutPreviewBanner = findViewById(R.id.layout_preview_banner);
        if (layoutPreviewBanner != null) {
            if (isPreviewMode) {
                layoutPreviewBanner.setVisibility(View.VISIBLE);
                View btnExit = findViewById(R.id.btn_exit_preview);
                if (btnExit != null) {
                    if (btnExit instanceof TextView) {
                        ((TextView) btnExit).setText("Volver al CMS");
                    }
                    btnExit.setOnClickListener(v -> finish());
                }
            } else {
                layoutPreviewBanner.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupNavigation();
    }

    private void showPreviewOptionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Opciones de Vista Previa (Borrador)")
                .setItems(new String[]{
                        "Seguir Editando (Volver al Editor)",
                        "Guardar Borrador Local",
                        "Publicar Cambios Ahora a la Nube",
                        "Sincronizar Ahora con Servidor"
                }, (d, which) -> {
                    if (which == 0) {
                        finish();
                    } else if (which == 1) {
                        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE).edit().putBoolean("has_unpublished_changes", true).apply();
                        Toast.makeText(this, "Borrador guardado localmente", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        Toast.makeText(this, "Publicando configuración...", Toast.LENGTH_SHORT).show();
                        repository.publishCurrentConfiguration(result -> {
                            if (result.getSuccess()) {
                                Toast.makeText(this, "¡Configuración publicada en la nube!", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(this, "Error: " + result.getErrorMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else if (which == 3) {
                        repository.actualizarUltimaActividad(this);
                        Toast.makeText(this, "Sincronización solicitada", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setupDrawer() {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        if (drawerLayout == null || navigationView == null) return;

        View menuIcon = findViewById(R.id.menu_icon);
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            View btnProfileHeader = headerView.findViewById(R.id.btn_profile);
            View closeDrawerBtn = headerView.findViewById(R.id.close_drawer);

            if (btnProfileHeader != null) {
                btnProfileHeader.setOnClickListener(v -> {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    startActivity(new Intent(this, ProfileActivity.class));
                });
            }

            if (closeDrawerBtn != null) {
                closeDrawerBtn.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));
            }
        }

        new Thread(() -> {
            List<NavigationItemEntity> rawSideItems = repository.getRobustSidebarItems();

            List<NavigationItemEntity> sideItems = rawSideItems.stream()
                    .filter(item -> !"action_reset".equals(item.targetSectionId)
                                 && !"side_reset".equals(item.id)
                                 && !"side_cuentas".equals(item.id)
                                 && !"sec_comunidad".equalsIgnoreCase(item.targetSectionId)
                                 && !"Cuentas".equalsIgnoreCase(item.label)
                                 && !"Restablecer".equalsIgnoreCase(item.label))
                    .collect(Collectors.toList());

            Collections.sort(sideItems, (i1, i2) -> {
                boolean isLogout1 = "side_logout".equalsIgnoreCase(i1.id) || "action_logout".equalsIgnoreCase(i1.targetSectionId);
                boolean isLogout2 = "side_logout".equalsIgnoreCase(i2.id) || "action_logout".equalsIgnoreCase(i2.targetSectionId);
                if (isLogout1 && !isLogout2) return 1;
                if (!isLogout1 && isLogout2) return -1;

                return Integer.compare(i1.displayOrder, i2.displayOrder);
            });

            List<NavigationItemEntity> finalItems = sideItems;
            runOnUiThread(() -> {
                navigationView.getMenu().clear();
                for (NavigationItemEntity item : finalItems) {
                    int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                    MenuItem menuItem = navigationView.getMenu().add(0, item.id.hashCode(), item.displayOrder, item.label);
                    menuItem.setIcon(iconRes != 0 ? iconRes : android.R.drawable.ic_menu_info_details);
                    if ("side_logout".equalsIgnoreCase(item.id) || "action_logout".equalsIgnoreCase(item.targetSectionId)) {
                        SpannableString s = new SpannableString(item.label);
                        s.setSpan(new ForegroundColorSpan(Color.RED), 0, s.length(), 0);
                        menuItem.setTitle(s);
                        if (menuItem.getIcon() != null) {
                            menuItem.getIcon().setTint(Color.RED);
                        }
                    }
                }

                navigationView.setNavigationItemSelectedListener(menuItem -> {
                    int id = menuItem.getItemId();
                    if (id == "side_admin".hashCode() || id == "dialog_admin".hashCode()) {
                        showAdminPasswordDialog();
                    } else {
                        for (NavigationItemEntity nav : finalItems) {
                            if (nav.id.hashCode() == id) {
                                handleNavAction(nav);
                                break;
                            }
                        }
                    }
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                });
            });
        }).start();
    }

    private void showAdminPasswordDialog() {
        AdminAuthManager authManager = new AdminAuthManager(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_login, null);
        EditText etPassword = dialogView.findViewById(R.id.et_admin_password);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnLogin.setOnClickListener(v -> {
            String pass = etPassword.getText().toString();
            // Paso 1: Verificar la Clave Global de Seguridad (1234 / admin123)
            if (authManager.checkPassword(pass)) {
                dialog.dismiss();

                // Paso 2: Verificar si el usuario que está en sesión tiene el Rol de ADMIN o es el Super Admin coluarl@gmail.com
                SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                String userRole = pref.getString("user_role", "GUEST");
                String userEmail = pref.getString("user_email", "");

                boolean isAdmin = "ADMIN".equalsIgnoreCase(userRole) 
                        || "coluarl@gmail.com".equalsIgnoreCase(userEmail);

                if (isAdmin) {
                    startActivity(new Intent(this, AdminActivity.class));
                } else {
                    Toast.makeText(this, "Acceso denegado: Tu cuenta no tiene permisos de administrador.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Clave incorrecta. Solo personal autorizado.", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void setupNavbar() {
        LinearLayout container = findViewById(R.id.btn_nosotros);
        new Thread(() -> {
            String logoName = repository.getGlobalConfig("logo_path");
            List<NavigationItemEntity> navItems = repository.getVisibleNavigation("TOP_ACTION");

            runOnUiThread(() -> {
                ImageView ivLogo = findViewById(R.id.img_header_logo);
                if (ivLogo != null && !logoName.isEmpty()) {
                    int logoRes = getResources().getIdentifier(logoName, "drawable", getPackageName());
                    if (logoRes != 0) ivLogo.setImageResource(logoRes);
                }

                if (container != null && !navItems.isEmpty()) {
                    NavigationItemEntity item = navItems.get(0);
                    container.setVisibility(View.VISIBLE);
                    TextView tvLabel = container.findViewById(R.id.tv_navbar_label);
                    if (tvLabel != null) tvLabel.setText(item.label);

                    int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                    if (iconRes != 0 && container.getChildCount() > 0 && container.getChildAt(0) instanceof ImageView) {
                        ((ImageView) container.getChildAt(0)).setImageResource(iconRes);
                    }

                    container.setOnClickListener(v -> handleNavAction(item));
                } else if (container != null) {
                    container.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);

        new Thread(() -> {
            List<NavigationItemEntity> items = repository.getVisibleNavigation("BOTTOM_NAV");
            if (items.size() < 5) {
                items = new ArrayList<>();
                items.add(new NavigationItemEntity("nav_servicios", "Servicios", "servicios_digitales", "sec_servicios", "BOTTOM_NAV", 1));
                items.add(new NavigationItemEntity("nav_agencias", "Agencias", "ubicacion", "sec_agencias", "BOTTOM_NAV", 2));
                items.add(new NavigationItemEntity("nav_home", "Inicio", "inicio", "sec_home", "BOTTOM_NAV", 3));
                items.add(new NavigationItemEntity("nav_beneficios", "Beneficios", "beneficios", "sec_beneficios", "BOTTOM_NAV", 4));
                items.add(new NavigationItemEntity("nav_noticias", "Noticias", "noticias_colua", "sec_noticias", "BOTTOM_NAV", 5));
            }

            Collections.sort(items, (i1, i2) -> Integer.compare(i1.displayOrder, i2.displayOrder));

            List<NavigationItemEntity> finalItems = items;
            runOnUiThread(() -> {
                bottomNav.getMenu().clear();
                int selectedId = 0;

                for (NavigationItemEntity item : finalItems) {
                    int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                    if (iconRes == 0) {
                        if ("inicio".equalsIgnoreCase(item.iconName) || "sec_home".equalsIgnoreCase(item.targetSectionId)) iconRes = R.drawable.ic_home;
                        else if ("ubicacion".equalsIgnoreCase(item.iconName)) iconRes = R.drawable.ic_location;
                        else if ("servicios_digitales".equalsIgnoreCase(item.iconName)) iconRes = R.drawable.ic_phone_android;
                        else if ("beneficios".equalsIgnoreCase(item.iconName)) iconRes = R.drawable.ic_star;
                        else if ("noticias".equalsIgnoreCase(item.iconName) || "noticias_colua".equalsIgnoreCase(item.iconName) || "sec_noticias".equalsIgnoreCase(item.targetSectionId)) iconRes = R.drawable.noticias_colua;
                        else iconRes = R.drawable.ic_info;
                    }

                    int itemId = Math.abs(item.id.hashCode());
                    bottomNav.getMenu().add(0, itemId, item.displayOrder, item.label)
                            .setIcon(iconRes);

                    if (item.targetSectionId.equalsIgnoreCase(sectionId)) {
                        selectedId = itemId;
                    }
                }

                bottomNav.setOnItemSelectedListener(null);
                if (selectedId != 0 && bottomNav.getMenu().findItem(selectedId) != null) {
                    bottomNav.setSelectedItemId(selectedId);
                } else {
                    bottomNav.getMenu().setGroupCheckable(0, true, false);
                    for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                        bottomNav.getMenu().getItem(i).setChecked(false);
                    }
                    bottomNav.getMenu().setGroupCheckable(0, true, true);
                }

                bottomNav.setOnItemSelectedListener(menuItem -> {
                    for (NavigationItemEntity nav : finalItems) {
                        if (Math.abs(nav.id.hashCode()) == menuItem.getItemId()) {
                            handleNavAction(nav);
                            return true;
                        }
                    }
                    return true;
                });
            });
        }).start();
    }

    private void handleNavAction(NavigationItemEntity nav) {
        String target = nav.targetSectionId;
        if ("sec_home".equals(target) || "home".equals(target)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } else if ("sec_agencias".equals(target) || "agencias".equals(target)) {
            startActivity(new Intent(this, AgenciasActivity.class));
            finish();
        } else if ("sec_beneficios".equals(target) || "beneficios".equals(target)) {
            startActivity(new Intent(this, BeneficiosActivity.class));
            finish();
        } else if ("activity_profile".equals(target)) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if ("action_logout".equals(target)) {
            finishAffinity();
            System.exit(0);
        } else if ("action_reset".equals(target)) {
            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            if (target.equals(sectionId)) return;
            
            Intent intent = new Intent(this, DynamicSectionActivity.class);
            intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, target);
            if (isVisualEditMode) intent.putExtra(EXTRA_VISUAL_EDIT_MODE, true);
            startActivity(intent);
            finish();
        }
    }

    private void loadSectionData() {
        View pb = findViewById(R.id.pb_dynamic_loading);
        if (pb != null) pb.setVisibility(View.VISIBLE);
        findViewById(R.id.rv_dynamic_items).setVisibility(View.GONE);
        findViewById(R.id.tv_empty_state).setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Resolver la sección por ID o Slug
                List<SectionEntity> allSections = repository.getAllSections();
                SectionEntity foundSection = null;
                for (SectionEntity s : allSections) {
                    if (sectionId.equalsIgnoreCase(s.id) || sectionId.equalsIgnoreCase(s.slug)) {
                        foundSection = s;
                        break;
                    }
                }
                
                section = foundSection;
                String canonicalId = section != null ? section.id : sectionId;

                boolean isPreview = isVisualEditMode || getIntent().getBooleanExtra("extra_is_preview_mode", false);

                // En modo vista previa cargar borradores, de lo contrario solo publicados
                List<ContentItemEntity> items = isPreview 
                        ? repository.getItemsBySection(canonicalId) 
                        : repository.getPublishedItemsBySection(canonicalId);

                List<ContentBlockEntity> blocks = isPreview 
                        ? repository.getBlocksBySection(canonicalId) 
                        : repository.getPublishedBlocksBySection(canonicalId);
                
                runOnUiThread(() -> {
                    FrameLayout containerNosotros = findViewById(R.id.container_nosotros_custom);
                    if ("sec_nosotros".equalsIgnoreCase(canonicalId) || "sec_ahorros".equalsIgnoreCase(canonicalId) || "sec_creditos".equalsIgnoreCase(canonicalId) || "sec_seguros".equalsIgnoreCase(canonicalId) || "sec_remesas".equalsIgnoreCase(canonicalId) || "sec_servicios".equalsIgnoreCase(canonicalId) || "sec_beneficios".equalsIgnoreCase(canonicalId)) {
                        // Para Nosotros, Ahorros, Créditos, Seguros, Remesas, Servicios o Beneficios mostramos sus respectivas plantillas perfectas (idénticas en todos los dispositivos)
                        findViewById(R.id.tv_dynamic_title).setVisibility(View.GONE);
                        findViewById(R.id.tv_dynamic_description).setVisibility(View.GONE);
                        findViewById(R.id.rv_dynamic_blocks).setVisibility(View.GONE);
                        findViewById(R.id.rv_dynamic_items).setVisibility(View.GONE);
                        findViewById(R.id.layout_pagination).setVisibility(View.GONE);
                        findViewById(R.id.tv_empty_state).setVisibility(View.GONE);
                        if (pb != null) pb.setVisibility(View.GONE);

                        if (containerNosotros != null) {
                            containerNosotros.setVisibility(View.VISIBLE);
                            if (containerNosotros.getChildCount() == 0) {
                                int layoutRes = "sec_ahorros".equalsIgnoreCase(canonicalId) ? R.layout.layout_ahorros_content 
                                              : "sec_creditos".equalsIgnoreCase(canonicalId) ? R.layout.layout_creditos_content 
                                              : "sec_seguros".equalsIgnoreCase(canonicalId) ? R.layout.layout_seguros_content 
                                              : "sec_remesas".equalsIgnoreCase(canonicalId) ? R.layout.layout_remesas_content 
                                              : "sec_servicios".equalsIgnoreCase(canonicalId) ? R.layout.layout_servicios_content 
                                              : "sec_beneficios".equalsIgnoreCase(canonicalId) ? R.layout.layout_beneficios_content 
                                              : R.layout.layout_nosotros_content;
                                View inflated = LayoutInflater.from(this).inflate(layoutRes, containerNosotros, true);
                                if ("sec_ahorros".equalsIgnoreCase(canonicalId)) {
                                    setupAhorrosView(inflated);
                                } else if ("sec_creditos".equalsIgnoreCase(canonicalId)) {
                                    setupCreditosView(inflated);
                                } else if ("sec_seguros".equalsIgnoreCase(canonicalId)) {
                                    setupSegurosView(inflated);
                                } else if ("sec_remesas".equalsIgnoreCase(canonicalId)) {
                                    setupRemesasView(inflated);
                                } else if ("sec_servicios".equalsIgnoreCase(canonicalId)) {
                                    setupServiciosView(inflated);
                                } else if ("sec_beneficios".equalsIgnoreCase(canonicalId)) {
                                    setupBeneficiosContent(inflated);
                                }
                            }
                        }
                    } else {
                        // Si existen contenidos o bloques dinámicos agregados desde el CMS por el administrador, se muestran dinámicamente
                        if (containerNosotros != null) {
                            containerNosotros.setVisibility(View.GONE);
                        }
                        if (section != null) {
                            String titleText = (section.title != null && !section.title.isEmpty()) ? section.title : sectionId;
                            String descText = (section.description != null) ? section.description : "";
                            ((TextView) findViewById(R.id.tv_dynamic_title)).setText(titleText);
                            ((TextView) findViewById(R.id.tv_dynamic_description)).setText(descText);
                            
                            if ("sec_ahorros".equals(canonicalId)) {
                                findViewById(R.id.tv_dynamic_title).setVisibility(View.GONE);
                                findViewById(R.id.tv_dynamic_description).setVisibility(View.GONE);
                            } else {
                                findViewById(R.id.tv_dynamic_title).setVisibility(View.VISIBLE);
                                findViewById(R.id.tv_dynamic_description).setVisibility(View.VISIBLE);
                            }
                        } else {
                            String cleanFallback = sectionId.replace("sec_", "").replace("_", " ");
                            if (!cleanFallback.isEmpty()) {
                                cleanFallback = cleanFallback.substring(0, 1).toUpperCase() + cleanFallback.substring(1);
                            }
                            ((TextView) findViewById(R.id.tv_dynamic_title)).setText(cleanFallback);
                            ((TextView) findViewById(R.id.tv_dynamic_description)).setText("");
                        }
                        
                        masterItemList.clear();
                        masterItemList.addAll(items);
                        applyNewsFilter();

                        blockList.clear();
                        blockList.addAll(blocks);
                        blockAdapter.notifyDataSetChanged();
                        
                        if (itemList.isEmpty() && blockList.isEmpty()) {
                            findViewById(R.id.tv_empty_state).setVisibility(View.VISIBLE);
                        } else {
                            findViewById(R.id.tv_empty_state).setVisibility(View.GONE);
                            findViewById(R.id.rv_dynamic_items).setVisibility(View.VISIBLE);
                        }
                        if (pb != null) pb.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void applyNewsFilter() {
        itemList.clear();
        long now = System.currentTimeMillis();
        long weekMillis = 7L * 24 * 60 * 60 * 1000;
        long monthMillis = 30L * 24 * 60 * 60 * 1000;

        for (ContentItemEntity item : masterItemList) {
            boolean matchesSearch = true;
            if (currentSearchQuery != null && !currentSearchQuery.trim().isEmpty()) {
                String q = currentSearchQuery.toLowerCase().trim();
                String title = item.title != null ? item.title.toLowerCase() : "";
                String desc = item.description != null ? item.description.toLowerCase() : "";
                String tags = item.tags != null ? item.tags.toLowerCase() : "";
                matchesSearch = title.contains(q) || desc.contains(q) || tags.contains(q);
            }

            boolean matchesDate = true;
            if (currentDateFilter == 1) { // Última semana
                matchesDate = (now - item.updatedAt) <= weekMillis;
            } else if (currentDateFilter == 2) { // Este mes
                matchesDate = (now - item.updatedAt) <= monthMillis;
            }

            if (matchesSearch && matchesDate) {
                itemList.add(item);
            }
        }

        currentPage = 1;
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = itemList.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        if (totalPages <= 1) {
            findViewById(R.id.layout_pagination).setVisibility(View.GONE);
            paginatedItemList.clear();
            paginatedItemList.addAll(itemList);
        } else {
            findViewById(R.id.layout_pagination).setVisibility(View.VISIBLE);
            int start = (currentPage - 1) * itemsPerPage;
            int end = Math.min(start + itemsPerPage, totalItems);
            
            paginatedItemList.clear();
            paginatedItemList.addAll(itemList.subList(start, end));
            
            setupPaginationBar(totalPages);
        }
        itemAdapter.notifyDataSetChanged();
    }

    private void setupPaginationBar(int totalPages) {
        View layoutPag = findViewById(R.id.layout_pagination);
        if (layoutPag == null) return;

        if (totalPages <= 1) {
            layoutPag.setVisibility(View.GONE);
            return;
        }

        layoutPag.setVisibility(View.VISIBLE);

        LinearLayout layoutNumbers = findViewById(R.id.layout_page_numbers);
        if (layoutNumbers == null) return;
        layoutNumbers.removeAllViews();

        List<Integer> pagesToShow = new ArrayList<>();
        if (totalPages <= 5) {
            for (int i = 1; i <= totalPages; i++) pagesToShow.add(i);
        } else {
            pagesToShow.add(1);
            if (currentPage > 3) pagesToShow.add(-1);

            int start = Math.max(2, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);

            if (currentPage <= 3) {
                start = 2;
                end = Math.min(3, totalPages - 1);
            }
            if (currentPage >= totalPages - 2) {
                start = Math.max(2, totalPages - 2);
                end = totalPages - 1;
            }

            for (int i = start; i <= end; i++) {
                if (!pagesToShow.contains(i)) pagesToShow.add(i);
            }

            if (currentPage < totalPages - 2) pagesToShow.add(-1);
            if (!pagesToShow.contains(totalPages)) pagesToShow.add(totalPages);
        }

        for (Integer p : pagesToShow) {
            if (p == -1) {
                TextView tvDots = new TextView(this);
                tvDots.setText("...");
                tvDots.setTextSize(14f);
                tvDots.setGravity(Gravity.CENTER);
                tvDots.setPadding(12, 0, 12, 0);
                tvDots.setTextColor(Color.parseColor("#94A3B8"));
                layoutNumbers.addView(tvDots);
                continue;
            }

            final int pageNum = p;
            TextView tvPage = new TextView(this);
            tvPage.setText(String.valueOf(pageNum));
            tvPage.setTextSize(13f);
            tvPage.setTypeface(null, Typeface.BOLD);
            tvPage.setGravity(Gravity.CENTER);

            int boxSize = (int) (36 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(boxSize, boxSize);
            lp.setMargins(6, 0, 6, 0);
            tvPage.setLayoutParams(lp);

            if (pageNum == currentPage) {
                tvPage.setTextColor(Color.WHITE);
                tvPage.setBackgroundResource(R.drawable.bg_outlined_spinner);
                tvPage.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
            } else {
                tvPage.setTextColor(Color.parseColor("#334155"));
                tvPage.setBackground(null);
            }

            tvPage.setOnClickListener(v -> {
                currentPage = pageNum;
                updatePagination();
            });

            layoutNumbers.addView(tvPage);
        }

        View btnPrev = findViewById(R.id.btn_pagination_prev);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (currentPage > 1) {
                    currentPage--;
                    updatePagination();
                }
            });
        }

        View btnNext = findViewById(R.id.btn_pagination_next);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                if (currentPage < totalPages) {
                    currentPage++;
                    updatePagination();
                }
            });
        }
    }

    private void setupRecyclerView() {
        // Items RecyclerView
        RecyclerView rvItems = findViewById(R.id.rv_dynamic_items);
        rvItems.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ContentItemAdapter(paginatedItemList, item -> {
            // Acción al hacer clic en el item
        });
        itemAdapter.setEditMode(isVisualEditMode);
        rvItems.setAdapter(itemAdapter);

        RecyclerView rvBlocks = findViewById(R.id.rv_dynamic_blocks);
        rvBlocks.setLayoutManager(new LinearLayoutManager(this));
        blockAdapter = new BlockAdapter(blockList);
        rvBlocks.setAdapter(blockAdapter);
    }

    private void setupAhorrosView(View view) {
        View btnAbreCuenta = view.findViewById(R.id.btn_abre_tu_cuenta);
        if (btnAbreCuenta != null) {
            btnAbreCuenta.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "PBX: 7795-7795", Toast.LENGTH_SHORT).show();
                }
            });
        }

        class AhorroInfo {
            String title;
            String desc;
            String fullInfo;
            String accentColor;
            String logoResName;
            AhorroInfo(String t, String d, String f, String c, String l) {
                title = t; desc = d; fullInfo = f; accentColor = c; logoResName = l;
            }
        }

        List<AhorroInfo> list = new ArrayList<>();
        list.add(new AhorroInfo(
            "Cuenta Aportación Adulto",
            "Es la cuenta que otorga el derecho a la persona natural a asociarse a la cooperativa, lo convierte en dueño con voz y voto en las decisiones de la asamblea general (no es una cuenta corriente).",
            "• Monto de apertura: desde Q50.00.\n• Tasa de interés: 5% anual afecto a ISR.\n• Intereses: capitalizables anualmente.",
            "#EF8819",
            null
        ));
        list.add(new AhorroInfo(
            "Cuenta Aportación Infanto Juvenil",
            "Es la cuenta que otorga el derecho al menor de edad a asociarse a la cooperativa e iniciar el hábito del ahorro (no es una cuenta corriente).",
            "• Monto de apertura: desde Q50.00.\n• Tasa de interés: 5% anual afecto a ISR.\n• Intereses: capitalizables anualmente.",
            "#E42A67",
            null
        ));
        list.add(new AhorroInfo(
            "Cuenta Ahorro Infanto Juvenil",
            "Es la cuenta diseñada para motivar y fomentar en los niños y adolescentes la cultura del ahorro.",
            "• Monto de apertura: desde Q10.00.\n• Tasa de interés: 3% anual afecto a ISR.\n• Obtiene 5 beneficios al mantener mínimo Q500.00 en su cuenta corriente después de 180 días de haberla aperturado (not aplica el beneficio de seguro de deudores hasta que cumpla +18 años).",
            "#59B8A4",
            "ahorro_infanto_juvenil"
        ));
        list.add(new AhorroInfo(
            "Cuenta Ahorro Disponible",
            "Es la cuenta que el asociado podrá utilizar para poder darle movimiento a sus ahorros de acuerdo con sus necesidades y conveniencias.",
            "• Monto de apertura: desde Q50.00 y/o $100.\n• Tasa de interés en Q: 3% anual afecto a ISR.\n• Tasa de interés en $: 1.50% anual afecto a ISR.\n• Intereses: capitalizables mensualmente.\n• Acceso a canales digitales (Tarjeta de Débito VISA, MICOOPE en línea y Fri) sin costos.\n• Obtiene 6 beneficios al mantener mínimo Q500.00 en su cuenta después de 180 días de haberla aperturado.\n• Recepción de remesa dirigida.",
            "#173789",
            "ahorro_disponible"
        ));
        list.add(new AhorroInfo(
            "Cuenta Ahorro Programado",
            "Es la cuenta que les permite a los asociados aportar cuotas fijas mensuales para un objetivo específico en el futuro.",
            "• Apertura desde Q25.00.\n• Tasa de interés: 7.50% anual, afecto a ISR.\n• Plazos de 3, 5, 10, 15 o 20 años.\n• Programas desde Q25.00, Q50.00, Q100.00 y múltiplos de Q100.00.\n• Intereses capitalizables mensualmente.\n• Obtiene 6 beneficios al mantener mínimo Q500.00 en su cuenta después de 180 días de haberla aperturado.\n• Crédito automático de inmediato.",
            "#634794",
            "ahorro_programado"
        ));
        list.add(new AhorroInfo(
            "Cuenta Ahorro Plazo Fijo",
            "Es la cuenta que le permite al asociado obtener alto rendimiento y seguridad sobre sus ahorros.",
            "• Apertura desde Q1,000.00 y/o $200.\n• Plazos de 90, 180 y 365 días.\n• Intereses capitalizables trimestralmente.\n• Obtiene 6 beneficios después de 180 días de haber aperturado la cuenta.\n• Crédito automático de inmediato.",
            "#EF8819",
            "ahorro_plazo_fijo"
        ));

        int[] cardIds = {R.id.card_ahorro_1, R.id.card_ahorro_2, R.id.card_ahorro_3, R.id.card_ahorro_4, R.id.card_ahorro_5, R.id.card_ahorro_6};
        for (int i = 0; i < cardIds.length && i < list.size(); i++) {
            View card = view.findViewById(cardIds[i]);
            AhorroInfo info = list.get(i);
            if (card != null) {
                TextView tvTitle = card.findViewById(R.id.card_ahorro_title);
                TextView tvDesc = card.findViewById(R.id.card_ahorro_desc);
                Button btnConocer = card.findViewById(R.id.btn_conocer_mas);
                ImageView ivLogo = card.findViewById(R.id.card_ahorro_logo);

                if (tvTitle != null) tvTitle.setText(info.title);
                if (tvDesc != null) tvDesc.setText(info.desc);
                if (ivLogo != null) {
                    if (info.logoResName != null && !info.logoResName.isEmpty()) {
                        int resId = getResources().getIdentifier(info.logoResName, "drawable", getPackageName());
                        if (resId != 0) {
                            ivLogo.setImageResource(resId);
                            ivLogo.setVisibility(View.VISIBLE);
                            if (tvTitle != null) tvTitle.setVisibility(View.GONE); // Ocultar título redundante cuando hay imagen
                        } else {
                            ivLogo.setVisibility(View.GONE);
                            if (tvTitle != null) tvTitle.setVisibility(View.VISIBLE);
                        }
                    } else {
                        ivLogo.setVisibility(View.GONE);
                        if (tvTitle != null) tvTitle.setVisibility(View.VISIBLE);
                    }
                }
                if (btnConocer != null) {
                    btnConocer.setOnClickListener(v -> showAhorroDetailsDialog(info.title, info.fullInfo, info.accentColor));
                }
            }
        }
    }

    private void showAhorroDetailsDialog(String title, String fullInfo, String colorHex) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_details, null);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title);
        TextView tvContent = dialogView.findViewById(R.id.dialog_content);
        View btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        Button btnCall = dialogView.findViewById(R.id.btn_dialog_call);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvContent != null) tvContent.setText(fullInfo);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnCall != null) {
            btnCall.setText("Llamar al PBX: 7795-7795");
            btnCall.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "PBX: 7795-7795", Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }

    private void setupCreditosView(View view) {
        class CreditoInfo {
            String title;
            String desc;
            String logoResName;
            CreditoInfo(String t, String d, String l) {
                title = t; desc = d; logoResName = l;
            }
        }

        List<CreditoInfo> list = new ArrayList<>();
        list.add(new CreditoInfo(
            "Crédito Productivo",
            "• Ideal para capital de trabajo, inventarios y/o mercadería.\n• Adquisición y/o remodelación de activos fijos (mobiliario, herramientas, equipo).\n• Compra de vehículo.\n\nMonto: desde Q1,000.00 en adelante.",
            "credito_productivo"
        ));
        list.add(new CreditoInfo(
            "Crédito Consumo",
            "• Gastos personales (varios destinos).\n• Adquisición de menaje de casa.\n• Compra de vehículo.\n• Crédito educativo.\n\nMonto: desde Q1,000.00 en adelante.",
            "credito_consumo"
        ));
        list.add(new CreditoInfo(
            "Crédito Vivienda",
            "• Mejoramiento y ampliación de vivienda residencial.\n• Construcción de vivienda nueva en terreno propio.\n• Compra de terreno con fines de vivienda.\n• Compra de vivienda.\n• Liberación de gravamen hipotecario (compra de deuda).\n\nMonto: desde Q1,000.00 en adelante.",
            "credito_vivienda"
        ));
        list.add(new CreditoInfo(
            "Crédi Vehículo",
            "• Adquisición de motocicletas o vehículos para actividades comerciales o para uso personal.\n\nMonto: desde Q1,000.00 en adelante.",
            "credi_vehiculo"
        ));
        list.add(new CreditoInfo(
            "Crédito MIPYMES",
            "• Servicio, industria, capital de trabajo o inversiones productivas.\n\nMonto: desde Q1,000.00 en adelante.",
            null
        ));
        list.add(new CreditoInfo(
            "Crédito Agrícola",
            "• Capital de trabajo destinado para siembra, renovación, mantenimiento e insumos para todo tipo de cultivo, siempre que este sea lícito.\n• Adquisición de activos fijos destinados para actividades agrícolas (maquinaria, equipo de trabajo, semovientes y herramientas).\n• Compra de terreno apto para cultivo y/o crianza de ganado.\n• Compra de animales para crianza y engorde, así como insumos para su desarrollo.\n\nMonto: desde Q1,000.00 en adelante.",
            null
        ));
        list.add(new CreditoInfo(
            "Crédito Automático",
            "• Libre disponibilidad (Cualquier destino).\n\nMonto: desde Q1,000.00 siempre y cuando el monto del crédito no sea mayor al 90% del monto de la inversión.",
            null
        ));
        list.add(new CreditoInfo(
            "Microcréditos",
            "• Financiamiento para pequeños negocios dedicados a la producción, comercio o servicios, con pagos respaldados por los ingresos de sus ventas.\n\nMonto: desde Q1,000.00 en adelante.",
            null
        ));

        int[] cardIds = {
            R.id.card_credito_1, R.id.card_credito_2, R.id.card_credito_3, 
            R.id.card_credito_4, R.id.card_credito_5, R.id.card_credito_6,
            R.id.card_credito_7, R.id.card_credito_8
        };

        for (int i = 0; i < cardIds.length && i < list.size(); i++) {
            View card = view.findViewById(cardIds[i]);
            CreditoInfo info = list.get(i);
            if (card != null) {
                TextView tvTitle = card.findViewById(R.id.card_credito_title);
                TextView tvDesc = card.findViewById(R.id.card_credito_desc);
                Button btnSolicitar = card.findViewById(R.id.btn_solicitar);
                ImageView ivLogo = card.findViewById(R.id.card_credito_logo);

                if (tvTitle != null) tvTitle.setText(info.title);
                if (tvDesc != null) tvDesc.setText(info.desc);

                if (ivLogo != null) {
                    if (info.logoResName != null && !info.logoResName.isEmpty()) {
                        int resId = getResources().getIdentifier(info.logoResName, "drawable", getPackageName());
                        if (resId != 0) {
                            ivLogo.setImageResource(resId);
                            ivLogo.setVisibility(View.VISIBLE);
                            if (tvTitle != null) tvTitle.setVisibility(View.GONE); // Ocultar título redundante cuando hay imagen
                        } else {
                            ivLogo.setVisibility(View.GONE);
                            if (tvTitle != null) tvTitle.setVisibility(View.VISIBLE);
                        }
                    } else {
                        ivLogo.setVisibility(View.GONE);
                        if (tvTitle != null) tvTitle.setVisibility(View.VISIBLE);
                    }
                }

                if (btnSolicitar != null) {
                    btnSolicitar.setText("Solicitar Información");
                    btnSolicitar.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, "PBX: 7795-7795", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    }

    private void setupSegurosView(View view) {
        View btnPbx = view.findViewById(R.id.btn_seguros_pbx);
        if (btnPbx != null) {
            btnPbx.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:00000000"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "PBX: 0000-0000", Toast.LENGTH_SHORT).show();
                }
            });
        }

        String[] logoNames = {
            "seguro_cv_personal",
            "seguro_vida_saludable",
            "seguro_edad_de_oro",
            "seguro_de_cancer",
            "seguro_accidentes_infanto_juvenil",
            "seguro_manejo",
            "seguro_de_vida_individual_o_familar"
        };

        String[] accentColors = {
            "#EF8819", "#E42A67", "#59B8A4", "#634794", "#173789", "#59B8A4", "#E42A67"
        };

        int[] cardIds = {
            R.id.card_seguro_1, R.id.card_seguro_2, R.id.card_seguro_3, 
            R.id.card_seguro_4, R.id.card_seguro_5, R.id.card_seguro_6,
            R.id.card_seguro_7
        };

        for (int i = 0; i < cardIds.length && i < logoNames.length; i++) {
            View card = view.findViewById(cardIds[i]);
            String logoName = logoNames[i];
            String colorHex = accentColors[i % accentColors.length];
            if (card != null) {
                View accent = card.findViewById(R.id.card_seguro_accent);
                ImageView ivLogo = card.findViewById(R.id.card_seguro_logo);
                Button btnSolicitar = card.findViewById(R.id.btn_solicitar_seguro);

                if (accent != null) {
                    try { accent.setBackgroundColor(Color.parseColor(colorHex)); } catch (Exception ignored) {}
                }

                if (ivLogo != null) {
                    int resId = getResources().getIdentifier(logoName, "drawable", getPackageName());
                    if (resId != 0) {
                        ivLogo.setImageResource(resId);
                        ivLogo.setVisibility(View.VISIBLE);
                    } else {
                        ivLogo.setVisibility(View.GONE);
                    }
                }

                if (btnSolicitar != null) {
                    btnSolicitar.setText("Solicitar Información →");
                    btnSolicitar.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:00000000"));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, "PBX: 0000-0000", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    }

    private void setupRemesasView(View view) {
        View btnBuscarAgencia = view.findViewById(R.id.btn_buscar_agencia);
        if (btnBuscarAgencia != null) {
            btnBuscarAgencia.setOnClickListener(v -> {
                startActivity(new Intent(this, AgenciasActivity.class));
            });
        }

        View btnSolicitarRemesa = view.findViewById(R.id.btn_solicitar_remesa);
        if (btnSolicitarRemesa != null) {
            btnSolicitarRemesa.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:22135580"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Atención al Asociado: (+502) 2213-5580", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View tvPhone = view.findViewById(R.id.tv_remesas_phone);
        if (tvPhone != null) {
            tvPhone.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:22135580"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Atención al Asociado: (+502) 2213-5580", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupServiciosView(View view) {
        View btnMicoope = view.findViewById(R.id.btn_micooope);
        if (btnMicoope != null) {
            btnMicoope.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=gt.coop.micoope"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "MICOOPE en línea", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnFri = view.findViewById(R.id.btn_fri);
        if (btnFri != null) {
            btnFri.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=gt.com.fri"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Red Fri", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupBeneficiosContent(View view) {
        class BeneficioInfo {
            String title;
            String desc;
            String iconName;
            BeneficioInfo(String t, String d, String i) {
                title = t; desc = d; iconName = i;
            }
        }

        List<BeneficioInfo> list = new ArrayList<>();
        list.add(new BeneficioInfo(
            "Renta Diaria por Hospitalización",
            "La cooperativa apoya económicamente al asociado en caso de que sea internado en un hospital público o privado, por enfermedad o accidente, calculando el pago según el monto de sus ahorros (1 a 69 años inclusive).",
            "renta_diaria"
        ));
        list.add(new BeneficioInfo(
            "Apoyo Quirúrgico",
            "La cooperativa otorgará al asociado un apoyo económico para los gastos médicos incurridos por alguna cirugía como consecuencia de una enfermedad o accidente. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 70 años).",
            "apoyo_quirurgico"
        ));
        list.add(new BeneficioInfo(
            "Servicio Funerario",
            "En caso de fallecimiento, la cooperativa apoya a la familia del asociado con un sepelio digno, proporcionándoles un ataúd fúnebre en coordinación con la red de funerarias autorizadas. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 68 años inclusive).",
            "servicio_funerario"
        ));
        list.add(new BeneficioInfo(
            "Seguro de Ahorrantes",
            "En caso de fallecimiento del asociado, la cooperativa garantiza la devolución de los ahorros a sus beneficiarios, así mismo se hace entrega del seguro sobre su dinero depositado en sus cuentas, hasta un monto máximo de Q150,000.00. (Este beneficio es de por vida, siempre y cuando se asocie en la edad de 1 a 68 años inclusive).",
            "beneficio_de_ahorrantes"
        ));
        list.add(new BeneficioInfo(
            "Seguro de Deudores",
            "En caso de fallecimiento del asociado con crédito vigente, la cooperativa ofrece un seguro que cubre los saldos insolutos hasta un monto máximo de Q200,000.00 (Asociados de 18 a 69 años inclusive).",
            "beneficio_de_deudores"
        ));
        list.add(new BeneficioInfo(
            "Beneficio de Oro",
            "La cooperativa brinda un apoyo económico a los asociados mayores de 70 años que sean diagnosticados por una enfermedad grave de acuerdo con el catálogo, a través de un único desembolso y cumpliendo con los requisitos establecidos.",
            "beneficio_de_oro"
        ));

        int[] cardIds = {
            R.id.card_beneficio_1, R.id.card_beneficio_2, R.id.card_beneficio_3,
            R.id.card_beneficio_4, R.id.card_beneficio_5, R.id.card_beneficio_6
        };

        for (int i = 0; i < cardIds.length && i < list.size(); i++) {
            View card = view.findViewById(cardIds[i]);
            BeneficioInfo info = list.get(i);
            if (card != null) {
                TextView tvTitle = card.findViewById(R.id.card_beneficio_title);
                TextView tvDesc = card.findViewById(R.id.card_beneficio_desc);
                ImageView ivIcon = card.findViewById(R.id.card_beneficio_icon);

                if (tvTitle != null) tvTitle.setText(info.title);
                if (tvDesc != null) tvDesc.setText(info.desc);
                if (ivIcon != null) {
                    int resId = getResources().getIdentifier(info.iconName, "drawable", getPackageName());
                    if (resId != 0) {
                        ivIcon.setImageResource(resId);
                        ivIcon.setVisibility(View.VISIBLE);
                    } else {
                        ivIcon.setVisibility(View.GONE);
                    }
                }
            }
        }
    }
}