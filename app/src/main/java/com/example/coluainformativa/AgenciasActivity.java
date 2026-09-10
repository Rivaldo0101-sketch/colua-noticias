package com.example.coluainformativa;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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

import com.example.coluainformativa.database.AgenciaEntity;
import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.ui.agencias.AgenciaAdapter;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

import android.content.Intent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class AgenciasActivity extends AppCompatActivity {

    private ColuaRepository repository;
    private RecyclerView rvAgencias;
    private AgenciaAdapter adapter;
    private List<AgenciaEntity> allAgenciasFiltered = new ArrayList<>();
    private List<Object> paginatedListDecorated = new ArrayList<>();
    
    private boolean isVisualEditMode = false;
    private String currentQuery = "";
    private String currentDepto = "Todos los Deptos.";
    private String currentType = "Todos";
    private int currentPage = 1;
    private int itemsPerPage = 6; // Configurado estrictamente a 6 agencias por página como solicitó el usuario

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agencias);

        isVisualEditMode = getIntent().getBooleanExtra("extra_visual_edit_mode", false);

        repository = new ColuaRepository(this);
        rvAgencias = findViewById(R.id.rv_agencias);
        rvAgencias.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new AgenciaAdapter(paginatedListDecorated);
        adapter.setEditMode(isVisualEditMode);
        rvAgencias.setAdapter(adapter);



        setupSearch();
        setupFilters();
        setupNavigation();
        setupNavbar();
        setupDrawer();
        setupPreviewBanner();

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.app_bar_agencias),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.scroll_content_agencias)
        );
        
        loadData();
    }

    private void setupPreviewBanner() {
        boolean isPreviewMode = getIntent().getBooleanExtra("extra_is_preview_mode", false) || isVisualEditMode;
        View layoutPreviewBanner = findViewById(R.id.layout_preview_banner);
        if (layoutPreviewBanner != null) {
            if (isPreviewMode) {
                layoutPreviewBanner.setVisibility(View.VISIBLE);
                View btnExit = findViewById(R.id.btn_exit_preview);
                if (btnExit != null) {
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
                int agenciasItemId = 0;

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

                    if ("sec_agencias".equalsIgnoreCase(item.targetSectionId) || "nav_agencias".equalsIgnoreCase(item.id) || "Agencias".equalsIgnoreCase(item.label)) {
                        agenciasItemId = itemId;
                    }
                }

                bottomNav.setOnItemSelectedListener(null);
                if (agenciasItemId != 0 && bottomNav.getMenu().findItem(agenciasItemId) != null) {
                    bottomNav.setSelectedItemId(agenciasItemId);
                } else {
                    int size = bottomNav.getMenu().size();
                    for (int i = 0; i < size; i++) {
                        bottomNav.getMenu().getItem(i).setChecked(false);
                    }
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
        if ("sec_home".equals(nav.targetSectionId)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } else if ("sec_agencias".equals(nav.targetSectionId)) {
            // Ya estamos aquí
        } else if (nav.targetSectionId.startsWith("sec_")) {
            Intent intent = new Intent(this, DynamicSectionActivity.class);
            intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, nav.targetSectionId);
            startActivity(intent);
            finish();
        }
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search_agencias);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                currentPage = 1; // Reset pagination on search
                loadData();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // El teclado se cierra automáticamente con ActionDone en el XML
                return true;
            }
            return false;
        });
    }

    private void setupFilters() {
        ChipGroup typeGroup = findViewById(R.id.chip_group_types);
        typeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentType = "Todos";
            } else {
                Chip chip = findViewById(checkedIds.get(0));
                String text = chip.getText().toString();
                if ("Agencias".equals(text)) currentType = "AGENCIA";
                else if ("Agentes MICOOPE".equals(text)) currentType = "AGENTE";
                else if ("Cajeros".equals(text)) currentType = "CAJERO";
                else currentType = "Todos";
            }
            currentPage = 1;
            loadData();
        });

        ChipGroup deptoGroup = findViewById(R.id.chip_group_deptos);
        deptoGroup.removeAllViews(); // Limpiar antiguos
        
        String[] departamentos = {"Todos los Deptos.", "Sololá", "Quiché", "Totonicapán", "Suchitepéquez"};
        for (String depto : departamentos) {
            Chip chip = new Chip(this);
            chip.setText(depto);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setId(View.generateViewId());
            if (depto.equals(currentDepto)) chip.setChecked(true);
            deptoGroup.addView(chip);
        }
        
        deptoGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentDepto = "Todos los Deptos.";
            } else {
                Chip chip = findViewById(checkedIds.get(0));
                currentDepto = chip.getText().toString();
            }
            currentPage = 1;
            loadData();
        });
    }

    private void loadData() {
        View pb = findViewById(R.id.pb_agencias_loading);
        if (pb != null) pb.setVisibility(View.VISIBLE);
        rvAgencias.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                List<AgenciaEntity> filtered = repository.getAllAgencias();
                List<AgenciaEntity> result = new ArrayList<>();

                for (AgenciaEntity a : filtered) {
                    if (a == null) continue;
                    String nombre = a.nombre != null ? a.nombre : "";
                    String depto = a.departamento != null ? a.departamento : "";
                    
                    boolean matchesSearch = currentQuery.isEmpty() || 
                            nombre.toLowerCase().contains(currentQuery.toLowerCase()) || 
                            depto.toLowerCase().contains(currentQuery.toLowerCase());
                    
                    boolean matchesDepto = currentDepto.equals("Todos los Deptos.") || 
                            currentDepto.equals(depto);
                    
                    boolean matchesType = currentType.equals("Todos") || 
                            currentType.equals(a.tipo);

                    if (matchesSearch && matchesDepto && matchesType) {
                        result.add(a);
                    }
                }

                // Ordenar por departamento y luego por nombre
                Collections.sort(result, (a1, a2) -> {
                    int deptoComp = a1.departamento.compareToIgnoreCase(a2.departamento);
                    if (deptoComp != 0) return deptoComp;
                    return a1.nombre.compareToIgnoreCase(a2.nombre);
                });

                runOnUiThread(() -> {
                    allAgenciasFiltered.clear();
                    allAgenciasFiltered.addAll(result);
                    updatePagination();
                    rvAgencias.setVisibility(View.VISIBLE);
                    if (pb != null) pb.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al cargar agencias", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updatePagination() {
        // Ahora paginamos basándonos ÚNICAMENTE en las Agencias (no encabezados)
        int totalRealItems = allAgenciasFiltered.size();
        int totalPages = (int) Math.ceil((double) totalRealItems / itemsPerPage);

        if (totalPages <= 1) {
            findViewById(R.id.layout_pagination).setVisibility(View.GONE);
            decorateList(allAgenciasFiltered);
        } else {
            findViewById(R.id.layout_pagination).setVisibility(View.VISIBLE);
            if (currentPage > totalPages) currentPage = totalPages;
            
            int start = (currentPage - 1) * itemsPerPage;
            int end = Math.min(start + itemsPerPage, totalRealItems);
            
            if (start < totalRealItems) {
                decorateList(allAgenciasFiltered.subList(start, end));
            }
            
            setupPaginationBar(totalPages);
        }
        adapter.notifyDataSetChanged();
        rvAgencias.scrollToPosition(0);
    }

    private void decorateList(List<AgenciaEntity> pageItems) {
        paginatedListDecorated.clear();
        String lastDepto = "";
        
        for (AgenciaEntity a : pageItems) {
            // Si el departamento cambia o es el primero de la página, añadimos el header
            if (!a.departamento.equalsIgnoreCase(lastDepto)) {
                paginatedListDecorated.add(a.departamento);
                lastDepto = a.departamento;
            }
            paginatedListDecorated.add(a);
        }
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
}
