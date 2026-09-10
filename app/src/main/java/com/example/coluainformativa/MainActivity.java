package com.example.coluainformativa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.coluainformativa.database.DataSeeder;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.ui.content.BlockAdapter;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.coluainformativa.database.NavigationItemEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.example.coluainformativa.security.AdminAuthManager;

import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.ui.home.HomeItemAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import kotlin.Unit;

public class MainActivity extends AppCompatActivity {

    private ColuaRepository repository;
    private AdminAuthManager authManager;
    private DrawerLayout drawerLayout;
    private String userRole;
    private String userName;
    private boolean isVisualEditMode = false;
    private RecyclerView rvSections;
    private HomeItemAdapter adapter;
    private List<ContentItemEntity> gridItemList = new ArrayList<>();

    private TextView tvSlogan1, tvSlogan2, tvHelpTitle, tvHelpDesc;
    private Button btnPbx;
    private ImageView imgDistintivo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        isVisualEditMode = getIntent().getBooleanExtra("extra_visual_edit_mode", false);
        setContentView(R.layout.activity_main);
        
        loadSession();
        repository = new ColuaRepository(this);
        authManager = new AdminAuthManager(this);
        drawerLayout = findViewById(R.id.drawer_layout);
        rvSections = findViewById(R.id.rv_sections);
        
        tvSlogan1 = findViewById(R.id.tv_slogan_1);
        tvSlogan2 = findViewById(R.id.tv_slogan_2);
        tvHelpTitle = findViewById(R.id.tv_help_title);
        tvHelpDesc = findViewById(R.id.tv_help_desc);
        btnPbx = findViewById(R.id.btn_pbx);
        imgDistintivo = findViewById(R.id.img_distintivo);



        // Listener por defecto para el PBX
        btnPbx.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:77957795"));
            startActivity(intent);
        });

        setupMenuIcons();
        setupNosotrosButton();
        setupPreviewBanner();
        initData();
        setupRecyclerView();
        setupNavigation();
        setupDrawer();

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.drawer_layout),
                findViewById(R.id.toolbar),
                findViewById(R.id.bottom_navigation),
                findViewById(R.id.rv_sections)
        );

        updateUIBasedOnRole();

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
                    btnExit.setOnClickListener(v -> finish());
                }
            } else {
                layoutPreviewBanner.setVisibility(View.GONE);
            }
        }
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

    @Override
    protected void onResume() {
        super.onResume();
        loadSession();
        updateUIBasedOnRole();
        refreshContent();
        setupNavigation();
        
        // Sincronizar actividad real con Firestore (basado en installationId)
        repository.actualizarUltimaActividad(this);
    }

    private void initData() {
        new Thread(() -> {
            DataSeeder.seedIfEmpty(this);
            refreshContent();
        }).start();
    }

    private void setupRecyclerView() {
        adapter = new HomeItemAdapter(gridItemList, item -> {
            if (item.targetSectionId != null && !item.targetSectionId.isEmpty()) {
                Intent intent = new Intent(this, DynamicSectionActivity.class);
                intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, item.targetSectionId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Información disponible próximamente", Toast.LENGTH_SHORT).show();
            }
        });
        adapter.setEditMode(isVisualEditMode);
        rvSections.setLayoutManager(new LinearLayoutManager(this));
        rvSections.setAdapter(adapter);
    }

    private void refreshContent() {
        View pb = findViewById(R.id.pb_main_loading);
        if (pb != null) pb.setVisibility(View.VISIBLE);
        rvSections.setVisibility(View.GONE);

        boolean isPreviewMode = getIntent().getBooleanExtra("extra_is_preview_mode", false) || isVisualEditMode;

        new Thread(() -> {
            try {
                // Cargar items del grid para el Home (En modo vista previa, cargar borradores)
                List<ContentItemEntity> items = isPreviewMode 
                        ? repository.getItemsBySection("sec_home") 
                        : repository.getPublishedItemsBySection("sec_home");
                
                // Cargar bloques de contenido
                List<ContentBlockEntity> blocks = isPreviewMode 
                        ? repository.getBlocksBySection("sec_home") 
                        : repository.getPublishedBlocksBySection("sec_home");

                // Cargar Slogan, Ayuda y PBX desde la Configuración Global Dinámica
                String sloganGlobal = repository.getGlobalConfig("slogan_text");
                String helpTitleGlobal = repository.getGlobalConfig("help_title");
                String helpDescGlobal = repository.getGlobalConfig("help_desc");
                String pbxListJson = repository.getGlobalConfig("pbx_list_json");

                runOnUiThread(() -> {
                    gridItemList.clear();
                    gridItemList.addAll(items);
                    adapter.notifyDataSetChanged();

                    // Renderizar bloques dinámicos extra en Inicio
                    RecyclerView rvMainBlocks = findViewById(R.id.rv_main_blocks);
                    if (rvMainBlocks != null) {
                        List<ContentBlockEntity> extraBlocks = blocks.stream()
                                .filter(b -> !"block_home_institutional_contact".equalsIgnoreCase(b.id)
                                          && !"block_home_slogan".equalsIgnoreCase(b.id)
                                          && !"block_home_help".equalsIgnoreCase(b.id))
                                .collect(Collectors.toList());
                        rvMainBlocks.setLayoutManager(new LinearLayoutManager(this));
                        rvMainBlocks.setAdapter(new BlockAdapter(extraBlocks));
                    }

                    // Buscar bloque institucional dinámico
                    ContentBlockEntity instBlock = null;
                    for (ContentBlockEntity b : blocks) {
                        if ("block_home_institutional_contact".equalsIgnoreCase(b.id) || "CONTAINER".equalsIgnoreCase(b.type)) {
                            instBlock = b;
                            break;
                        }
                    }

                    if (instBlock != null) {
                        // Logo / Imagen
                        if (imgDistintivo != null && instBlock.mediaPath != null && !instBlock.mediaPath.isEmpty()) {
                            int resId = getResources().getIdentifier(instBlock.mediaPath, "drawable", getPackageName());
                            if (resId != 0) imgDistintivo.setImageResource(resId);
                        }

                        // Título y Subtítulo
                        if (instBlock.title != null && !instBlock.title.isEmpty()) {
                            tvSlogan1.setText(instBlock.title);
                        }

                        if (instBlock.content != null && !instBlock.content.isEmpty()) {
                            String[] lines = instBlock.content.split("\n\n");
                            if (lines.length > 0) {
                                String[] sloganLines = lines[0].split("\n");
                                if (sloganLines.length > 0) tvSlogan2.setText(sloganLines[0]);
                            }
                            if (lines.length > 1) {
                                tvHelpTitle.setText(lines[1]);
                            }
                            if (lines.length > 2) {
                                tvHelpDesc.setText(lines[2]);
                            }
                        }

                        // Botón PBX
                        if (btnPbx != null) {
                            if (instBlock.buttonText != null && !instBlock.buttonText.isEmpty()) {
                                btnPbx.setText(instBlock.buttonText);
                            }
                            final String actionUrl = instBlock.buttonAction != null && !instBlock.buttonAction.isEmpty() ? instBlock.buttonAction : "tel:77957795";
                            btnPbx.setOnClickListener(v -> {
                                try {
                                    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse(actionUrl));
                                    startActivity(dialIntent);
                                } catch (Exception e) {
                                    Toast.makeText(this, "No se pudo realizar la llamada al PBX", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } else {
                        // Aplicar Slogan Global por defecto
                        if (!sloganGlobal.isEmpty()) {
                            String[] lines = sloganGlobal.split("\n");
                            if (lines.length > 0) tvSlogan1.setText(lines[0]);
                            if (lines.length > 1) tvSlogan2.setText(lines[1]);
                        }

                        // Aplicar Ayuda Global
                        if (!helpTitleGlobal.isEmpty()) tvHelpTitle.setText(helpTitleGlobal);
                        if (!helpDescGlobal.isEmpty()) tvHelpDesc.setText(helpDescGlobal);

                        // Configurar PBX
                        btnPbx.setOnClickListener(v -> showPbxSelectionDialog(pbxListJson));
                    }

                    if (pb != null) pb.setVisibility(View.GONE);
                    rvSections.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                    rvSections.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void showPbxSelectionDialog(String pbxListJson) {
        if (pbxListJson == null || pbxListJson.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
            startActivity(intent);
            return;
        }

        try {
            JSONArray array = new JSONArray(pbxListJson);
            if (array.length() == 1) {
                String num = array.getJSONObject(0).optString("number", "7795-7795");
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num)));
                return;
            }

            CharSequence[] items = new CharSequence[array.length()];
            String[] numbers = new String[array.length()];

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String label = obj.optString("label", "PBX");
                String num = obj.optString("number", "7795-7795");
                items[i] = label + ": " + num;
                numbers[i] = num;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Seleccionar Línea PBX")
                    .setItems(items, (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + numbers[which]));
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795")));
        }
    }
    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        
        // Carga dinámica de la BARRA DE NAVEGACIÓN INFERIOR (5 OPCIONES - INICIO OBLIGATORIO EN EL CENTRO POSICIÓN 3)
        new Thread(() -> {
            List<NavigationItemEntity> items = repository.getVisibleNavigation("BOTTOM_NAV");
            
            // Garantizar siempre 5 accesos con Inicio en el centro (Posición 3)
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
                int homeItemId = 0;

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

                    if ("sec_home".equalsIgnoreCase(item.targetSectionId) || "nav_home".equalsIgnoreCase(item.id) || "Inicio".equalsIgnoreCase(item.label)) {
                        homeItemId = itemId;
                    }
                }
                
                // Seleccionar 'Inicio' (Posición 3 en el centro) por defecto al iniciar la app
                bottomNav.setOnItemSelectedListener(null); 
                if (homeItemId != 0 && bottomNav.getMenu().findItem(homeItemId) != null) {
                    bottomNav.setSelectedItemId(homeItemId);
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
        if ("sec_home".equalsIgnoreCase(nav.targetSectionId)) {
            refreshContent();
        } else if ("sec_agencias".equalsIgnoreCase(nav.targetSectionId)) {
            Intent intent = new Intent(this, AgenciasActivity.class);
            startActivity(intent);
        } else if ("activity_profile".equalsIgnoreCase(nav.targetSectionId)) {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        } else if (nav.targetSectionId != null && !nav.targetSectionId.isEmpty()) {
            Intent intent = new Intent(this, DynamicSectionActivity.class);
            intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, nav.targetSectionId);
            startActivity(intent);
        }
    }

    private void loadSession() {
        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        userRole = pref.getString("user_role", "GUEST");
        userName = pref.getString("user_name", "Invitado");
    }

    private void updateUIBasedOnRole() {
        if ("ADMIN".equals(userRole)) {
            Toast.makeText(this, "Modo Administrador Activo", Toast.LENGTH_SHORT).show();
        }
        
        TextView tvGreeting = findViewById(R.id.tv_welcome_greeting);
        String name = (userName != null && !userName.isEmpty() && !"Invitado".equals(userName)) 
                ? userName.toUpperCase() : "";
        
        if (!name.isEmpty()) {
            tvGreeting.setText("BIENVENIDO, " + name + "\nA COLUA MICOOPE");
        } else {
            tvGreeting.setText("BIENVENIDO A\nCOLUA MICOOPE");
        }
    }

    private void setupMenuIcons() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
        }
    }

    private void setupNosotrosButton() {
        // Carga dinámica del TOP_ACTION y Logos
        LinearLayout container = findViewById(R.id.btn_nosotros);
        new Thread(() -> {
            List<NavigationItemEntity> navItems = repository.getVisibleNavigation("TOP_ACTION");
            String logoName = repository.getGlobalConfig("logo_path");
            String distintivoName = repository.getGlobalConfig("distintivo_path");

            runOnUiThread(() -> {
                // Actualizar Logo Global
                ImageView ivLogo = findViewById(R.id.img_header_logo);
                if (ivLogo != null && !logoName.isEmpty()) {
                    if (logoName.startsWith("content://") || logoName.startsWith("file://")) {
                        ivLogo.setImageURI(Uri.parse(logoName));
                    } else {
                        int logoRes = getResources().getIdentifier(logoName, "drawable", getPackageName());
                        if (logoRes != 0) ivLogo.setImageResource(logoRes);
                    }
                }

                // Actualizar Distintivo Global
                if (imgDistintivo != null && !distintivoName.isEmpty()) {
                    if (distintivoName.startsWith("content://") || distintivoName.startsWith("file://")) {
                        imgDistintivo.setImageURI(Uri.parse(distintivoName));
                    } else {
                        int distRes = getResources().getIdentifier(distintivoName, "drawable", getPackageName());
                        if (distRes != 0) imgDistintivo.setImageResource(distRes);
                    }
                }

                // Configurar primer item de Navbar (ej. Nosotros)
                List<NavigationItemEntity> effectiveNavItems = navItems;
                if (effectiveNavItems.isEmpty()) {
                    effectiveNavItems = new ArrayList<>();
                    effectiveNavItems.add(new NavigationItemEntity("nav_nosotros", "Nosotros", "public_service", "sec_nosotros", "TOP_ACTION", 1));
                }

                if (container != null && !effectiveNavItems.isEmpty()) {
                    NavigationItemEntity item = effectiveNavItems.get(0);
                    container.setVisibility(View.VISIBLE);
                    TextView tvLabel = container.findViewById(R.id.tv_navbar_label);
                    if (tvLabel != null) tvLabel.setText(item.label);
                    int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                    if (iconRes == 0 && "public_service".equalsIgnoreCase(item.iconName)) iconRes = R.drawable.public_service;
                    if (iconRes != 0 && container.getChildCount() > 0 && container.getChildAt(0) instanceof ImageView) {
                        ((ImageView) container.getChildAt(0)).setImageResource(iconRes);
                    }
                    
                    container.setOnClickListener(v -> handleNavAction(item));
                } else if (container != null) {
                    container.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void setupDrawer() {
        NavigationView navigationView = findViewById(R.id.nav_view);

        View menuIcon = findViewById(R.id.menu_icon);
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        View headerView = navigationView != null ? navigationView.getHeaderView(0) : null;
        if (headerView == null) return;
        
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

        // Carga dinámica del SIDE_MENU garantizando robustez
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
                                handleSidebarAction(nav);
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

    private void handleSidebarAction(NavigationItemEntity nav) {
        if ("activity_profile".equals(nav.targetSectionId)) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if ("action_logout".equals(nav.targetSectionId)) {
            finishAffinity();
            System.exit(0); // Cierre limpio sin diálogos de sistema
        } else if ("action_reset".equals(nav.targetSectionId)) {
            new AlertDialog.Builder(this)
                    .setTitle("Restablecer App")
                    .setMessage("¿Desea borrar sus datos de sesión? Esto no afectará el contenido administrativo.")
                    .setPositiveButton("Borrar", (d, w) -> {
                        getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().clear().apply();
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else if (nav.targetSectionId.startsWith("sec_")) {
            Intent intent = new Intent(this, DynamicSectionActivity.class);
            intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, nav.targetSectionId);
            startActivity(intent);
        }
    }

    private void showAdminPasswordDialog() {
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
                // Registrar al Administrador en la nube al entrar con la nueva estrategia real
                SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                repository.registrarUsuarioReal(
                    pref.getString("user_name", "Admin COLUA"),
                    "", // Teléfono
                    pref.getString("user_id", "admin_01"),
                    false // No es invitado
                );
                startActivity(new Intent(this, AdminActivity.class));
            } else {
                Toast.makeText(this, "Clave incorrecta. Solo personal autorizado.", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}