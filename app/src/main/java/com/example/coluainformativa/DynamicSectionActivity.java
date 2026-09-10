package com.example.coluainformativa;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
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



        loadSectionData();
        setupRecyclerView();
        setupNavigation();
        setupNavbar();
        setupDrawer();
        setupPreviewBanner();

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.app_bar_dynamic),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.scroll_content_dynamic)
        );

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
                    .filter(item -> !"action_reset".equalsIgnoreCase(item.targetSectionId)
                                 && !"side_reset".equalsIgnoreCase(item.id)
                                 && !"Restablecer".equalsIgnoreCase(item.label))
                    .collect(Collectors.toList());

            Collections.sort(sideItems, (item1, item2) -> Integer.compare(item1.displayOrder, item2.displayOrder));

            List<NavigationItemEntity> finalItems = sideItems;
            runOnUiThread(() -> {
                navigationView.getMenu().clear();
                for (NavigationItemEntity item : finalItems) {
                    int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                    navigationView.getMenu().add(0, item.id.hashCode(), item.displayOrder, item.label)
                            .setIcon(iconRes != 0 ? iconRes : android.R.drawable.ic_menu_info_details);
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
            if (authManager.checkPassword(pass)) {
                dialog.dismiss();
                startActivity(new Intent(this, AdminActivity.class));
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
                    
                    itemList.clear();
                    itemList.addAll(items);
                    updatePagination();

                    blockList.clear();
                    blockList.addAll(blocks);
                    blockAdapter.notifyDataSetChanged();
                    
                    if (itemList.isEmpty() && blockList.isEmpty()) {
                        TextView tvEmpty = findViewById(R.id.tv_empty_state);
                        if (tvEmpty != null) {
                            if ("sec_noticias".equalsIgnoreCase(canonicalId) || "noticias".equalsIgnoreCase(canonicalId)) {
                                tvEmpty.setText("Aún no hay publicaciones. Crea la primera noticia.");
                            } else {
                                tvEmpty.setText("No hay información disponible actualmente.");
                            }
                            tvEmpty.setVisibility(View.VISIBLE);
                        }
                    } else {
                        findViewById(R.id.tv_empty_state).setVisibility(View.GONE);
                        findViewById(R.id.rv_dynamic_items).setVisibility(View.VISIBLE);
                    }
                    if (pb != null) pb.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
}