package com.example.coluainformativa;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BeneficiosActivity extends AppCompatActivity {

    private ColuaRepository repository;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beneficios);

        repository = new ColuaRepository(this);
        drawerLayout = findViewById(R.id.drawer_layout);

        setupNavigation();
        setupNavbar();
        setupDrawer();

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.app_bar_beneficios),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.scroll_content_beneficios)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupNavigation();
    }

    private void setupDrawer() {
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
                if (ivLogo != null && logoName != null && !logoName.isEmpty()) {
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

                    if ("sec_beneficios".equalsIgnoreCase(item.targetSectionId)) {
                        selectedId = itemId;
                    }
                }

                bottomNav.setOnItemSelectedListener(null);
                if (selectedId != 0 && bottomNav.getMenu().findItem(selectedId) != null) {
                    bottomNav.setSelectedItemId(selectedId);
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
            // Already here
        } else {
            Intent intent = new Intent(this, DynamicSectionActivity.class);
            intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, target);
            startActivity(intent);
            finish();
        }
    }
}
