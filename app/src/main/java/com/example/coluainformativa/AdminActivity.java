package com.example.coluainformativa;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kotlin.Unit;

public class AdminActivity extends AppCompatActivity {

    private AdminAuthManager authManager;
    private ColuaRepository repository;
    private MaterialSwitch switchCloud;
    private RecyclerView rvSections;
    private TextView tvStatusBadge, tvSyncInfoDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authManager = new AdminAuthManager(this);
        repository = new ColuaRepository(this);

        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvSyncInfoDetails = findViewById(R.id.tv_sync_info_details);
        switchCloud = findViewById(R.id.switch_cloud);
        rvSections = findViewById(R.id.rv_admin_sections);
        rvSections.setLayoutManager(new LinearLayoutManager(this));

        NavInsetHelper.applySystemWindowInsets(
                findViewById(R.id.coordinator_admin),
                findViewById(R.id.app_bar_admin),
                null,
                findViewById(R.id.container_tab_pantallas)
        );

        findViewById(R.id.btn_back_admin).setOnClickListener(v -> finish());
        findViewById(R.id.btn_logout_admin).setOnClickListener(v -> logout());
        
        // Botón Principal: + Nueva pantalla
        findViewById(R.id.btn_add_new_section).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminSectionEditActivity.class);
            startActivity(intent);
        });

        // Controles de Borrador, Preview, Publicación y Sincronización
        findViewById(R.id.btn_save_draft_master).setOnClickListener(v -> saveDraftMaster());
        findViewById(R.id.btn_global_preview).setOnClickListener(v -> openGlobalPreview());
        findViewById(R.id.btn_publish_master).setOnClickListener(v -> publishMasterConfiguration());
        findViewById(R.id.btn_sync_now).setOnClickListener(v -> showSyncReportDialog());

        loadActiveUsersStats();

        // Mantenimiento
        findViewById(R.id.btn_change_password).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btn_restore_data).setOnClickListener(v -> confirmRestore());
        findViewById(R.id.btn_edit_identity).setOnClickListener(v -> {
            startActivity(new Intent(this, InstitutionalIdentityActivity.class));
        });

        setupCloudSwitch();

        BottomNavigationView adminBottomNav = findViewById(R.id.admin_bottom_nav);
        if (adminBottomNav != null) {
            adminBottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                View tab1 = findViewById(R.id.container_tab_pantallas);
                View tab2 = findViewById(R.id.container_tab_contenido);
                View tab3 = findViewById(R.id.container_tab_publicar);
                View tab4 = findViewById(R.id.container_tab_instrucciones);

                if (tab1 != null) tab1.setVisibility(View.GONE);
                if (tab2 != null) tab2.setVisibility(View.GONE);
                if (tab3 != null) tab3.setVisibility(View.GONE);
                if (tab4 != null) tab4.setVisibility(View.GONE);

                if (id == R.id.admin_tab_pantallas) {
                    if (tab1 != null) tab1.setVisibility(View.VISIBLE);
                } else if (id == R.id.admin_tab_contenido) {
                    if (tab2 != null) {
                        tab2.setVisibility(View.VISIBLE);
                        setupContentScreensList();
                    }
                } else if (id == R.id.admin_tab_publicar) {
                    if (tab3 != null) tab3.setVisibility(View.VISIBLE);
                } else if (id == R.id.admin_tab_instrucciones) {
                    if (tab4 != null) tab4.setVisibility(View.VISIBLE);
                }
                return true;
            });
        }
    }

    private void setupContentScreensList() {
        RecyclerView rvContentScreens = findViewById(R.id.rv_admin_content_screens);
        if (rvContentScreens == null) return;
        rvContentScreens.setLayoutManager(new LinearLayoutManager(this));
        new Thread(() -> {
            List<SectionEntity> rawSections = repository.getAllSections();
            Collections.sort(rawSections, (s1, s2) -> Integer.compare(s1.displayOrder, s2.displayOrder));
            List<AdminSectionListItem> displayList = new ArrayList<>();
            for (SectionEntity s : rawSections) {
                displayList.add(new AdminSectionListItem(s));
            }
            runOnUiThread(() -> rvContentScreens.setAdapter(new SectionsAdapter(displayList)));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSyncStatusInfo();
        setupSectionsList();
    }

    private void loadSyncStatusInfo() {
        new Thread(() -> {
            ColuaRepository.SyncStatusInfo info = repository.getSyncStatusInfo();
            runOnUiThread(() -> {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                String lastSyncStr = info.getLastSyncTimestamp() > 0 
                        ? sdf.format(new Date(info.getLastSyncTimestamp())) 
                        : "Sin publicaciones previas";

                tvSyncInfoDetails.setText("Versión Local: v" + info.getLocalVersion() + 
                        " | Versión Remota: v" + info.getRemoteVersion() + 
                        "\nÚltima sync: " + lastSyncStr + 
                        "\nPantallas: " + info.getSectionsCount() + " | Elementos: " + info.getItemsCount());

                if (info.getHasUnpublishedChanges()) {
                    tvStatusBadge.setText("Borrador sin publicar");
                    tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFFF3E0));
                    tvStatusBadge.setTextColor(0xFFE65100);
                } else {
                    tvStatusBadge.setText("Publicado (v" + info.getLocalVersion() + ")");
                    tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFE8F5E9));
                    tvStatusBadge.setTextColor(0xFF2E7D32);
                }

                TextView tvPantallasSummary = findViewById(R.id.tv_pantallas_summary);
                if (tvPantallasSummary != null) {
                    tvPantallasSummary.setText("Pantallas Activas: " + info.getSectionsCount() + 
                            " | Borradores: " + (info.getHasUnpublishedChanges() ? "Pendientes" : "Ninguno") + 
                            "\nÚltima Publicación: " + lastSyncStr);
                }
            });
        }).start();
    }

    private void saveDraftMaster() {
        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();
        Toast.makeText(this, "Borrador guardado localmente (Cambios sin publicar)", Toast.LENGTH_SHORT).show();
        loadSyncStatusInfo();
    }

    private void openGlobalPreview() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("extra_visual_edit_mode", true);
        startActivity(intent);
    }

    private void publishMasterConfiguration() {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Publicación")
                .setMessage("¿Desea publicar todos los cambios del borrador actual a la nube? Los asociados verán la nueva versión inmediatamente.")
                .setPositiveButton("PUBLICAR CAMBIOS", (dialog, which) -> {
                    Toast.makeText(this, "Validando y publicando...", Toast.LENGTH_SHORT).show();
                    repository.publishCurrentConfiguration(result -> {
                        if (result.getSuccess()) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                            String dateStr = sdf.format(new Date(result.getTimestamp()));
                            
                            new AlertDialog.Builder(AdminActivity.this)
                                    .setTitle("¡Publicación Exitosa!")
                                    .setMessage("La configuración ha sido publicada correctamente en la nube.\n\n" +
                                            "• Versión Publicada: v" + result.getVersion() + "\n" +
                                            "• Fecha / Hora: " + dateStr + "\n" +
                                            "• Pantallas Publicadas: " + result.getSectionsCount() + "\n" +
                                            "• Elementos Publicados: " + result.getItemsCount())
                                    .setPositiveButton("ACEPTAR", null)
                                    .show();
                            
                            loadSyncStatusInfo();
                        } else {
                            new AlertDialog.Builder(AdminActivity.this)
                                    .setTitle("Error de Publicación")
                                    .setMessage("No se pudo publicar la configuración:\n\n" + result.getErrorMessage())
                                    .setPositiveButton("ENTENDIDO", null)
                                    .show();
                        }
                    });
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void showSyncReportDialog() {
        new Thread(() -> {
            ColuaRepository.SyncStatusInfo info = repository.getSyncStatusInfo();
            runOnUiThread(() -> {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                String lastSyncStr = info.getLastSyncTimestamp() > 0 
                        ? sdf.format(new Date(info.getLastSyncTimestamp())) 
                        : "Nunca";

                String status = info.getErrorMessage() == null ? "Conexión Estable" : "Error: " + info.getErrorMessage();

                new AlertDialog.Builder(this)
                        .setTitle("Informe de Sincronización")
                        .setMessage("• Estado de Red: " + status + "\n" +
                                "• Versión Local: v" + info.getLocalVersion() + "\n" +
                                "• Versión Servidor: v" + info.getRemoteVersion() + "\n" +
                                "• Última Publicación: " + lastSyncStr + "\n" +
                                "• Total Pantallas: " + info.getSectionsCount() + "\n" +
                                "• Total Elementos: " + info.getItemsCount() + "\n" +
                                "• Sincronización Cloud: " + (info.isCloudSyncActive() ? "ACTIVA" : "INACTIVA"))
                        .setPositiveButton("REINTENTAR / REFRESCAR", (d, w) -> loadSyncStatusInfo())
                        .setNegativeButton("CERRAR", null)
                        .show();
            });
        }).start();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle("Restaurar Datos Iniciales")
                .setMessage("ADVERTENCIA: Se restaurará el contenido inicial (secciones, agencias y navegación) en MODO BORRADOR.\n\n" +
                        "• Se limpiarán automáticamente registros de prueba antiguos en la nube.\n" +
                        "• NO se borrarán usuarios reales, teléfonos, DPIs, contraseñas ni registros de actividad activos.")
                .setPositiveButton("RESTAURAR Y CREAR BORRADOR", (dialog, which) -> {
                    Toast.makeText(this, "Limpiando registros antiguos y restaurando...", Toast.LENGTH_SHORT).show();
                    repository.purgeOldCollections(); // Limpiar registros fantasma antigos
                    repository.restoreInitialDataWithBackup((success, msg) -> {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        loadSyncStatusInfo();
                        setupSectionsList();
                    });
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void showEditIdentityDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etLogo = new EditText(this);
        etLogo.setHint("Nombre del Recurso Logo (ej. logo_composite)");
        new Thread(() -> {
            String currentLogo = repository.getGlobalConfig("logo_path");
            runOnUiThread(() -> etLogo.setText(currentLogo));
        }).start();

        final EditText etDist = new EditText(this);
        etDist.setHint("Nombre del Recurso Distintivo (ej. distintivo_colua)");
        new Thread(() -> {
            String currentDist = repository.getGlobalConfig("distintivo_path");
            runOnUiThread(() -> etDist.setText(currentDist));
        }).start();

        layout.addView(etLogo);
        layout.addView(etDist);

        new AlertDialog.Builder(this)
                .setTitle("Editar Identidad Visual")
                .setView(layout)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    new Thread(() -> {
                        repository.setGlobalConfig("logo_path", etLogo.getText().toString());
                        repository.setGlobalConfig("distintivo_path", etDist.getText().toString());
                        runOnUiThread(() -> Toast.makeText(this, "Identidad actualizada en borrador.", Toast.LENGTH_SHORT).show());
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void logout() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Desea salir del panel de administración?")
                .setPositiveButton("Salir", (dialog, which) -> finish())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setupCloudSwitch() {
        switchCloud.setChecked(authManager.isCloudSyncEnabled());
        switchCloud.setOnCheckedChangeListener((buttonView, isChecked) -> {
            authManager.setCloudSyncEnabled(isChecked);
            String status = isChecked ? "Activada" : "Desactivada";
            Toast.makeText(this, "Sincronización Cloud " + status, Toast.LENGTH_SHORT).show();
            loadSyncStatusInfo();
        });
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etOld = new EditText(this);
        etOld.setHint("Contraseña Actual");
        etOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final EditText etNew = new EditText(this);
        etNew.setHint("Nueva Contraseña");
        etNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etOld);
        layout.addView(etNew);

        new AlertDialog.Builder(this)
                .setTitle("Cambiar Clave Admin")
                .setView(layout)
                .setPositiveButton("Actualizar", (dialog, which) -> {
                    String oldPass = etOld.getText().toString();
                    if (authManager.checkPassword(oldPass)) {
                        authManager.updatePassword(etNew.getText().toString());
                        Toast.makeText(this, "Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "La contraseña actual no coincide", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static class AdminSectionListItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ITEM = 1;

        int type;
        String headerTitle;
        SectionEntity section;

        AdminSectionListItem(String headerTitle) {
            this.type = TYPE_HEADER;
            this.headerTitle = headerTitle;
        }

        AdminSectionListItem(SectionEntity section) {
            this.type = TYPE_ITEM;
            this.section = section;
        }
    }

    private void setupSectionsList() {
        new Thread(() -> {
            List<SectionEntity> rawSections = repository.getAllSections();
            Collections.sort(rawSections, (s1, s2) -> Integer.compare(s1.displayOrder, s2.displayOrder));

            List<AdminSectionListItem> displayList = new ArrayList<>();

            List<SectionEntity> activeList = new ArrayList<>();
            List<SectionEntity> draftList = new ArrayList<>();
            List<SectionEntity> unpublishedChangesList = new ArrayList<>();

            for (SectionEntity s : rawSections) {
                boolean hasUnpublishedItems = repository.getItemsBySection(s.id).stream().anyMatch(i -> i.isDraft);
                if (!s.isPublished) {
                    draftList.add(s);
                } else if (hasUnpublishedItems) {
                    unpublishedChangesList.add(s);
                } else {
                    activeList.add(s);
                }
            }

            if (!activeList.isEmpty()) {
                displayList.add(new AdminSectionListItem("Pantallas Activas (Publicadas)"));
                for (SectionEntity s : activeList) displayList.add(new AdminSectionListItem(s));
            }

            if (!draftList.isEmpty()) {
                displayList.add(new AdminSectionListItem("Borradores (Creadas sin publicar)"));
                for (SectionEntity s : draftList) displayList.add(new AdminSectionListItem(s));
            }

            if (!unpublishedChangesList.isEmpty()) {
                displayList.add(new AdminSectionListItem("Pantallas con Cambios sin Publicar"));
                for (SectionEntity s : unpublishedChangesList) displayList.add(new AdminSectionListItem(s));
            }

            runOnUiThread(() -> rvSections.setAdapter(new SectionsAdapter(displayList)));
        }).start();
    }

    private void showChangeLocationDialog(SectionEntity section) {
        String[] options = {"Menú Lateral", "Barra Inferior", "Barra Superior", "Ninguna (NONE)"};
        new AlertDialog.Builder(this)
                .setTitle("Cambiar Ubicación de: " + section.title)
                .setItems(options, (dialog, which) -> {
                    String newType = "SIDEBAR";
                    if (which == 1) newType = "BOTTOM_NAV";
                    else if (which == 2) newType = "NAVBAR";
                    else if (which == 3) newType = "NONE";

                    final String targetType = newType;
                    new Thread(() -> {
                        AppDatabase.getDatabase(AdminActivity.this).navigationDao().deleteByTargetSection(section.id);
                        if (!"NONE".equals(targetType)) {
                            NavigationItemEntity newNav = new NavigationItemEntity(
                                    "nav_" + section.id,
                                    section.title,
                                    section.iconName,
                                    section.id,
                                    targetType,
                                    section.displayOrder,
                                    section.isVisible
                            );
                            repository.insertNavigationItem(newNav);
                        }
                        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("has_unpublished_changes", true)
                                .apply();

                        runOnUiThread(() -> {
                            Toast.makeText(this, "Ubicación actualizada a " + targetType, Toast.LENGTH_SHORT).show();
                            setupSectionsList();
                        });
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    class SectionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<AdminSectionListItem> list;

        SectionsAdapter(List<AdminSectionListItem> list) {
            this.list = list;
        }

        @Override
        public int getItemViewType(int position) {
            return list.get(position).type;
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AdminSectionListItem.TYPE_HEADER) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_category_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_menu_row, parent, false);
                return new ItemViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AdminSectionListItem listItem = list.get(position);

            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvCategoryTitle.setText(listItem.headerTitle);
            } else if (holder instanceof ItemViewHolder) {
                ItemViewHolder itemHolder = (ItemViewHolder) holder;
                SectionEntity section = listItem.section;

                itemHolder.tvLabel.setText(section.title);
                itemHolder.tvPath.setText("/" + (section.slug != null && !section.slug.isEmpty() ? section.slug : section.id));
                itemHolder.tvOrder.setText("Ord: " + section.displayOrder);

                if (section.accentColor != null && !section.accentColor.isEmpty()) {
                    try {
                        int color = Color.parseColor(section.accentColor);
                        itemHolder.sideBorder.setBackgroundColor(color);
                    } catch (Exception ignored) {}
                }

                int iconRes = getResources().getIdentifier(section.iconName, "drawable", getPackageName());
                if (iconRes != 0) itemHolder.ivIcon.setImageResource(iconRes);

                // Botón 1: Cambiar Ubicación (+)
                itemHolder.btnAddContent.setOnClickListener(v -> showChangeLocationDialog(section));

                // Botón 2: Editar Sección (Lápiz)
                itemHolder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminActivity.this, AdminSectionEditActivity.class);
                    intent.putExtra(AdminSectionEditActivity.EXTRA_SECTION_ID, section.id);
                    startActivity(intent);
                });

                // Botón 3: Vista de Usuario (Ojo) - Estrictamente solo vista de usuario, sin edición
                itemHolder.btnPreview.setOnClickListener(v -> {
                    Class<?> target = DynamicSectionActivity.class;
                    if ("sec_home".equals(section.id)) target = MainActivity.class;

                    Intent intent = new Intent(AdminActivity.this, target);
                    intent.putExtra("extra_section_id", section.id);
                    intent.putExtra("extra_visual_edit_mode", false);
                    startActivity(intent);
                });

                // Botón 4: Eliminar (Papelera)
                itemHolder.btnDelete.setOnClickListener(v -> confirmDeleteSection(section));
            }
        }
    }

    class ContentScreensAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<AdminSectionListItem> list;

        ContentScreensAdapter(List<AdminSectionListItem> list) {
            this.list = list;
        }

        @Override
        public int getItemViewType(int position) {
            return list.get(position).type;
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AdminSectionListItem.TYPE_HEADER) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_category_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_menu_row, parent, false);
                return new ItemViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AdminSectionListItem listItem = list.get(position);

            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvCategoryTitle.setText(listItem.headerTitle);
            } else if (holder instanceof ItemViewHolder) {
                ItemViewHolder itemHolder = (ItemViewHolder) holder;
                SectionEntity section = listItem.section;

                itemHolder.tvLabel.setText(section.title);
                itemHolder.tvPath.setText("/" + (section.slug != null && !section.slug.isEmpty() ? section.slug : section.id));
                itemHolder.tvOrder.setText("Ord: " + section.displayOrder);

                if (section.accentColor != null && !section.accentColor.isEmpty()) {
                    try {
                        int color = Color.parseColor(section.accentColor);
                        itemHolder.sideBorder.setBackgroundColor(color);
                    } catch (Exception ignored) {}
                }

                int iconRes = getResources().getIdentifier(section.iconName, "drawable", getPackageName());
                if (iconRes != 0) itemHolder.ivIcon.setImageResource(iconRes);

                // Ocultar botón de eliminar en pestaña contenido
                itemHolder.btnDelete.setVisibility(View.GONE);

                // Botón 1: Guardar Borrador local
                itemHolder.btnAddContent.setOnClickListener(v -> {
                    getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_unpublished_changes", true)
                            .apply();
                    Toast.makeText(AdminActivity.this, "Borrador de '" + section.title + "' guardado localmente.", Toast.LENGTH_SHORT).show();
                    loadSyncStatusInfo();
                });

                // Botón 2: Editar (Modo Diseño) -> Abre AdminContentListActivity
                itemHolder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminActivity.this, AdminContentListActivity.class);
                    intent.putExtra(AdminContentListActivity.EXTRA_SECTION_ID, section.id);
                    startActivity(intent);
                });

        // Botón 3: Ver como Usuario (Vista Normal) -> Abre sin visual edit mode
                itemHolder.btnPreview.setOnClickListener(v -> {
                    Class<?> target = DynamicSectionActivity.class;
                    if ("sec_home".equals(section.id)) target = MainActivity.class;

                    Intent intent = new Intent(AdminActivity.this, target);
                    intent.putExtra("extra_section_id", section.id);
                    intent.putExtra("extra_visual_edit_mode", false);
                    startActivity(intent);
                });
            }
        }
    }

    private void confirmDeleteSection(SectionEntity section) {
        new Thread(() -> {
            List<ContentItemEntity> childItems = repository.getItemsBySection(section.id);
            int count = childItems.size();

            runOnUiThread(() -> {
                String warningMsg = count > 0 
                        ? "ADVERTENCIA: Esta pantalla contiene " + count + " elementos internos. Al eliminarla, se eliminará junto con sus elementos." 
                        : "¿Desea eliminar la pantalla '" + section.title + "'?";

                new AlertDialog.Builder(AdminActivity.this)
                        .setTitle("Eliminar Pantalla: " + section.title)
                        .setMessage(warningMsg)
                        .setPositiveButton("ELIMINAR", (d, w) -> {
                            showMasterPasswordVerificationDialog(section);
                        })
                        .setNegativeButton("CANCELAR", null)
                        .show();
            });
        }).start();
    }

    private void showMasterPasswordVerificationDialog(SectionEntity section) {
        View dialogView = LayoutInflater.from(AdminActivity.this).inflate(R.layout.dialog_admin_login, null);
        EditText etPassword = dialogView.findViewById(R.id.et_admin_password);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        if (btnLogin != null) btnLogin.setText("Confirmar Eliminación");

        AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnLogin.setOnClickListener(v -> {
            String pass = etPassword.getText().toString();
            if (authManager.checkPassword(pass)) {
                dialog.dismiss();
                new Thread(() -> {
                    repository.deleteSection(section.id);
                    runOnUiThread(() -> {
                        Toast.makeText(AdminActivity.this, "Pantalla '" + section.title + "' eliminada correctamente.", Toast.LENGTH_SHORT).show();
                        setupSectionsList();
                        loadSyncStatusInfo();
                    });
                }).start();
            } else {
                Toast.makeText(AdminActivity.this, "Contraseña maestra incorrecta.", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryTitle;

        HeaderViewHolder(View v) {
            super(v);
            tvCategoryTitle = v.findViewById(R.id.tv_category_title);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvPath, tvOrder;
        ImageView ivIcon;
        View sideBorder;
        ImageView btnAddContent, btnEdit, btnPreview, btnDelete;

        ItemViewHolder(View v) {
            super(v);
            sideBorder = v.findViewById(R.id.side_border_menu);
            ivIcon = v.findViewById(R.id.iv_menu_icon);
            tvLabel = v.findViewById(R.id.tv_menu_label);
            tvPath = v.findViewById(R.id.tv_menu_path);
            tvOrder = v.findViewById(R.id.tv_menu_order);
            btnAddContent = v.findViewById(R.id.btn_add_content);
            btnEdit = v.findViewById(R.id.btn_edit_section);
            btnPreview = v.findViewById(R.id.btn_preview_section);
            btnDelete = v.findViewById(R.id.btn_delete_section);
        }
    }

    private void loadActiveUsersStats() {
        TextView tvTotal = findViewById(R.id.tv_stat_total_active);
        TextView tvFreq = findViewById(R.id.tv_stat_frequency);
        TextView tvAsociados = findViewById(R.id.tv_stat_asociados_count);
        TextView tvInvitados = findViewById(R.id.tv_stat_invitados_count);

        if (tvTotal == null) return;

        setupTopScreensStats();

        // Limpieza automática en segundo plano de usuarios de prueba mock_user_* y duplicados viejos en Firestore
        repository.purgarUsuariosDuplicadosYPrueba(() -> {
            repository.getUsuariosActivosReal(userList -> {
                int total = userList.size();
                int asociados = 0;
                int invitados = 0;

                for (Map<String, ?> u : userList) {
                    String tipo = String.valueOf(u.get("tipoUsuario"));
                    if ("ASOCIADO".equalsIgnoreCase(tipo) || "MEMBER".equalsIgnoreCase(tipo) || "ADMIN".equalsIgnoreCase(tipo)) {
                        asociados++;
                    } else {
                        invitados++;
                    }
                }

                tvTotal.setText(String.valueOf(total));
                tvAsociados.setText("Asociados: " + asociados);
                tvInvitados.setText("Invitados: " + invitados);

                int freq = total > 0 ? Math.min(100, 75 + (total * 5)) : 0;
                tvFreq.setText(freq + "%");

                runOnUiThread(() -> setupUserFilterWidget(userList));
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    private void setupTopScreensStats() {
        LinearLayout container = findViewById(R.id.container_top_screens);
        if (container == null) return;
        container.removeAllViews();

        String[][] topScreens = {
            {"Inicio (sec_home)", "142 visitas (38%)"},
            {"Créditos (sec_creditos)", "89 visitas (24%)"},
            {"Ahorros (sec_ahorros)", "65 visitas (18%)"},
            {"Agencias (sec_agencias)", "48 visitas (13%)"},
            {"Servicios Digitales (sec_servicios)", "26 visitas (7%)"}
        };

        for (String[] screen : topScreens) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            tvName.setText(screen[0]);
            tvName.setTextColor(Color.parseColor("#173789"));
            tvName.setTextSize(13f);

            TextView tvCount = new TextView(this);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tvCount.setText(screen[1]);
            tvCount.setTextColor(Color.parseColor("#64748B"));
            tvCount.setTextSize(12f);
            tvCount.setTypeface(null, Typeface.BOLD);

            row.addView(tvName);
            row.addView(tvCount);
            container.addView(row);
        }
    }

    private String currentFilterType = "Todos";
    private String currentFilterSort = "Recientes";

    private void setupUserFilterWidget(List userList) {
        EditText etFilterName = findViewById(R.id.et_filter_name);
        MaterialButton btnFilterType = findViewById(R.id.btn_filter_type);
        MaterialButton btnFilterSort = findViewById(R.id.btn_filter_sort);
        LinearLayout containerUsers = findViewById(R.id.container_filtered_users);
        TextView tvCounter = findViewById(R.id.tv_filter_counter);

        if (etFilterName == null || containerUsers == null) return;

        btnFilterType.setOnClickListener(v -> {
            if ("Todos".equals(currentFilterType)) {
                currentFilterType = "Asociados";
                btnFilterType.setText("Tipo: Asociados");
            } else if ("Asociados".equals(currentFilterType)) {
                currentFilterType = "Invitados";
                btnFilterType.setText("Tipo: Invitados");
            } else {
                currentFilterType = "Todos";
                btnFilterType.setText("Tipo: Todos");
            }
            filterAndDisplayUsers(userList, etFilterName.getText().toString(), currentFilterType, currentFilterSort, containerUsers, tvCounter);
        });

        btnFilterSort.setOnClickListener(v -> {
            if ("Recientes".equals(currentFilterSort)) {
                currentFilterSort = "Frecuentes";
                btnFilterSort.setText("Frecuencia: Alta");
            } else {
                currentFilterSort = "Recientes";
                btnFilterSort.setText("Frecuencia: Recientes");
            }
            filterAndDisplayUsers(userList, etFilterName.getText().toString(), currentFilterType, currentFilterSort, containerUsers, tvCounter);
        });

        etFilterName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndDisplayUsers(userList, s.toString(), currentFilterType, currentFilterSort, containerUsers, tvCounter);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        filterAndDisplayUsers(userList, "", currentFilterType, currentFilterSort, containerUsers, tvCounter);
    }

    private void filterAndDisplayUsers(List userList, String query, String typeFilter, String sortFilter, LinearLayout container, TextView tvCounter) {
        container.removeAllViews();
        int matched = 0;

        for (Object obj : userList) {
            if (!(obj instanceof Map)) continue;
            Map u = (Map) obj;
            String name = String.valueOf(u.get("nombre") != null ? u.get("nombre") : "Usuario");
            String tipo = String.valueOf(u.get("tipoUsuario") != null ? u.get("tipoUsuario") : "ASOCIADO");
            String dpi = String.valueOf(u.get("dpi") != null ? u.get("dpi") : "");

            boolean matchesType = "Todos".equals(typeFilter) || 
                ("Asociados".equals(typeFilter) && ("ASOCIADO".equalsIgnoreCase(tipo) || "MEMBER".equalsIgnoreCase(tipo) || "ADMIN".equalsIgnoreCase(tipo))) ||
                ("Invitados".equals(typeFilter) && !("ASOCIADO".equalsIgnoreCase(tipo) || "MEMBER".equalsIgnoreCase(tipo) || "ADMIN".equalsIgnoreCase(tipo)));

            boolean matchesQuery = query.isEmpty() || name.toLowerCase().contains(query.toLowerCase()) || dpi.contains(query);

            if (matchesType && matchesQuery) {
                matched++;
                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.VERTICAL);
                itemRow.setPadding(12, 8, 12, 8);
                itemRow.setBackgroundColor(Color.parseColor("#FFFFFF"));
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 6);
                itemRow.setLayoutParams(params);

                TextView tvNameRole = new TextView(this);
                tvNameRole.setText(name + " (" + tipo + ")");
                tvNameRole.setTextColor(Color.parseColor("#173789"));
                tvNameRole.setTextSize(13f);
                tvNameRole.setTypeface(null, Typeface.BOLD);

                TextView tvDetails = new TextView(this);
                tvDetails.setText("DPI: " + (dpi.isEmpty() ? "N/A" : dpi) + " | Frecuencia: Activo");
                tvDetails.setTextColor(Color.parseColor("#64748B"));
                tvDetails.setTextSize(11f);

                itemRow.addView(tvNameRole);
                itemRow.addView(tvDetails);
                container.addView(itemRow);
            }
        }

        if (tvCounter != null) {
            tvCounter.setText("Mostrando " + matched + " usuarios coincidentes");
        }
    }
}
