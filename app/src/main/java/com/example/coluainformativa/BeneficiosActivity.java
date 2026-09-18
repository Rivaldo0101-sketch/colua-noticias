package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
        setupBeneficiosContent();

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.app_bar_beneficios),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.scroll_content_beneficios)
        );
    }

    private void setupBeneficiosContent() {
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
            View card = findViewById(cardIds[i]);
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

        View btnPbx = findViewById(R.id.btn_beneficios_pbx);
        if (btnPbx != null) {
            btnPbx.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "PBX: 7795-7795", Toast.LENGTH_SHORT).show();
                }
            });
        }
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
