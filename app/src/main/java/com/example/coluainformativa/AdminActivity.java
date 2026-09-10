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
import android.util.Log;
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.NavigationItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import java.util.stream.Collectors;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.utils.DialogHelper;
import com.example.coluainformativa.utils.NavInsetHelper;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.Timestamp;

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

        // Interceptar el botón físico de Android, gestos de regresar y botones de navegación
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleControlledAdminExit();
            }
        });

        findViewById(R.id.btn_back_admin).setOnClickListener(v -> handleControlledAdminExit());
        findViewById(R.id.btn_logout_admin).setOnClickListener(v -> handleControlledAdminExit());
        
        // Botón Principal: + Nueva Publicación Rápida
        View btnCreateNewsQuick = findViewById(R.id.btn_create_news_quick);
        if (btnCreateNewsQuick != null) {
            btnCreateNewsQuick.setOnClickListener(v -> {
                Intent intent = new Intent(this, AdminNewsEditActivity.class);
                startActivity(intent);
            });
        }

        // Botón Secundario: + Nueva pantalla
        findViewById(R.id.btn_add_new_section).setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminSectionEditActivity.class);
            startActivity(intent);
        });

        // Controles de Borrador, Preview, Publicación y Sincronización
        findViewById(R.id.btn_save_draft_master).setOnClickListener(v -> saveDraftMaster());
        findViewById(R.id.btn_global_preview).setOnClickListener(v -> openGlobalPreview());
        findViewById(R.id.btn_publish_master).setOnClickListener(v -> publishMasterConfiguration());
        findViewById(R.id.btn_sync_now).setOnClickListener(v -> showSyncReportDialog());

        View btnReview = findViewById(R.id.btn_review_changes);
        if (btnReview != null) {
            btnReview.setOnClickListener(v -> showReviewChangesDialog());
        }

        loadActiveUsersStats();

        // Mantenimiento
        findViewById(R.id.btn_change_password).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btn_restore_data).setOnClickListener(v -> confirmRestore());
        findViewById(R.id.btn_wipe_users).setOnClickListener(v -> confirmWipeUsers());
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
            runOnUiThread(() -> rvContentScreens.setAdapter(new ContentScreenSelectionAdapter(rawSections)));
        }).start();
    }

    class ContentScreenSelectionAdapter extends RecyclerView.Adapter<ContentScreenSelectionAdapter.ViewHolder> {
        private final List<SectionEntity> list;

        ContentScreenSelectionAdapter(List<SectionEntity> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content_screen_selector, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SectionEntity section = list.get(position);
            holder.tvTitle.setText(section.title);
            holder.tvRoute.setText("/" + (section.slug != null && !section.slug.isEmpty() ? section.slug : section.id));

            if (section.accentColor != null && !section.accentColor.isEmpty()) {
                try {
                    holder.sideBorder.setBackgroundColor(Color.parseColor(section.accentColor));
                } catch (Exception ignored) {}
            }

            int iconRes = getResources().getIdentifier(section.iconName, "drawable", getPackageName());
            if (iconRes != 0) {
                holder.ivIcon.setImageResource(iconRes);
                if (section.iconName != null && section.iconName.startsWith("ic_")) {
                    holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                } else {
                    holder.ivIcon.setImageTintList(null);
                }
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_star);
                holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
            }

            // Recuento de elementos (Manejo especial para Agencias)
            new Thread(() -> {
                int count;
                if ("sec_agencias".equalsIgnoreCase(section.id) || "agencias".equalsIgnoreCase(section.id)) {
                    count = repository.getAllAgencias().size();
                } else {
                    count = repository.getItemsBySection(section.id).size() + repository.getBlocksBySection(section.id).size();
                }
                final int finalCount = count;
                runOnUiThread(() -> {
                    if (holder.tvCount != null) {
                        holder.tvCount.setText(finalCount + (finalCount == 1 ? " elemento registrado" : " elementos registrados"));
                    }
                });
            }).start();

            // Abrir Canvas Editor al hacer clic
            View.OnClickListener openCanvasListener = v -> {
                Intent intent = new Intent(AdminActivity.this, AdminContentListActivity.class);
                intent.putExtra(AdminContentListActivity.EXTRA_SECTION_ID, section.id);
                if ("sec_agencias".equalsIgnoreCase(section.id) || "agencias".equalsIgnoreCase(section.id)) {
                    intent.putExtra(AdminContentListActivity.EXTRA_MANAGE_AGENCIAS, true);
                }
                startActivity(intent);
            };

            holder.btnOpenCanvas.setOnClickListener(openCanvasListener);
            holder.itemView.setOnClickListener(openCanvasListener);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvRoute, tvCount;
            ImageView ivIcon;
            View sideBorder, btnOpenCanvas;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_content_screen_title);
                tvRoute = v.findViewById(R.id.tv_content_screen_route);
                tvCount = v.findViewById(R.id.tv_content_items_count);
                ivIcon = v.findViewById(R.id.iv_content_screen_icon);
                sideBorder = v.findViewById(R.id.side_border_content);
                btnOpenCanvas = v.findViewById(R.id.btn_open_canvas_editor);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager == null || !authManager.isSessionActive()) {
            Toast.makeText(this, "Sesión vencida o no autorizada. Redirigiendo...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        loadSyncStatusInfo();
        setupSectionsList();
    }

    private void loadSyncStatusInfo() {
        new Thread(() -> {
            ColuaRepository.SyncStatusInfo info = repository.getSyncStatusInfo();
            
            // Métricas de borradores reales
            List<SectionEntity> allSections = repository.getAllSections();
            int pendingSections = (int) allSections.stream().filter(s -> !s.isPublished).count();
            
            List<ContentItemEntity> allItems = repository.getAllItems();
            int pendingItems = (int) allItems.stream().filter(i -> i.isDraft).count();
            
            List<ContentBlockEntity> allBlocks = repository.getAllBlocks();
            int pendingBlocks = (int) allBlocks.stream().filter(b -> b.isDraft).count();
            
            int totalPending = pendingSections + pendingItems + pendingBlocks;

            List<ContentItemEntity> newsList = repository.getItemsBySection("sec_noticias");
            int totalLikes = newsList.stream().mapToInt(n -> n.likesCount).sum();
            int totalShares = newsList.stream().mapToInt(n -> n.sharesCount).sum();

            runOnUiThread(() -> {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                String lastSyncStr = info.getLastSyncTimestamp() > 0 
                        ? sdf.format(new Date(info.getLastSyncTimestamp())) 
                        : "Sin publicaciones previas";

                tvSyncInfoDetails.setText("Borrador local: v" + (info.getLocalVersion() + (totalPending > 0 ? 1 : 0)) + 
                        " | Publicada: v" + info.getLocalVersion() + 
                        "\nÚltima sync: " + lastSyncStr + 
                        "\nPantallas: " + info.getSectionsCount() + " | Elementos: " + info.getItemsCount() +
                        "\nReacciones: ❤️ " + totalLikes + " me gusta | 🔁 " + totalShares + " compartidos");

                TextView tvTotalPending = findViewById(R.id.tv_metric_total_pending);
                if (tvTotalPending != null) tvTotalPending.setText("Pendientes: " + totalPending);

                TextView tvMetricNew = findViewById(R.id.tv_metric_new);
                if (tvMetricNew != null) tvMetricNew.setText("Nuevos: " + (pendingSections + pendingItems));

                TextView tvMetricEdited = findViewById(R.id.tv_metric_edited);
                if (tvMetricEdited != null) tvMetricEdited.setText("Editados: " + pendingBlocks);

                TextView tvMetricIssues = findViewById(R.id.tv_metric_issues);
                if (tvMetricIssues != null) tvMetricIssues.setText("Incompletos: 0");

                if (totalPending > 0 || info.getHasUnpublishedChanges()) {
                    tvStatusBadge.setText("🟧 Cambios pendientes (" + totalPending + ")");
                    tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFFF3E0));
                    tvStatusBadge.setTextColor(0xFFE65100);
                } else {
                    tvStatusBadge.setText("🟢 Todo publicado (v" + info.getLocalVersion() + ")");
                    tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFE8F5E9));
                    tvStatusBadge.setTextColor(0xFF2E7D32);
                }

                TextView tvRecentLog = findViewById(R.id.tv_recent_activity_log);
                if (tvRecentLog != null) {
                    if (totalPending > 0) {
                        tvRecentLog.setText("Actividad reciente:\n• " + totalPending + " elementos o borradores pendientes por publicar.");
                    } else {
                        tvRecentLog.setText("Actividad reciente:\n• No hay cambios pendientes. La versión publicada v" + info.getLocalVersion() + " está actualizada.");
                    }
                }
            });
        }).start();
    }

    private void showReviewChangesDialog() {
        new Thread(() -> {
            List<SectionEntity> pendingSecs = repository.getAllSections().stream().filter(s -> !s.isPublished).collect(Collectors.toList());
            List<ContentItemEntity> pendingItems = repository.getAllItems().stream().filter(i -> i.isDraft).collect(Collectors.toList());
            List<ContentBlockEntity> pendingBlocks = repository.getAllBlocks().stream().filter(b -> b.isDraft).collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("DESGLOSE DE CAMBIOS PENDIENTES:\n\n");

            if (pendingSecs.isEmpty() && pendingItems.isEmpty() && pendingBlocks.isEmpty()) {
                sb.append("No hay cambios pendientes. La versión publicada está actualizada en la nube.\n");
            } else {
                if (!pendingSecs.isEmpty()) {
                    sb.append("PANTALLAS EN BORRADOR (").append(pendingSecs.size()).append("):\n");
                    for (SectionEntity s : pendingSecs) sb.append(" • ").append(s.title).append(" (/").append(s.slug).append(")\n");
                    sb.append("\n");
                }
                if (!pendingItems.isEmpty()) {
                    sb.append("TARJETAS DE PRODUCTO (").append(pendingItems.size()).append("):\n");
                    for (ContentItemEntity i : pendingItems) sb.append(" • ").append(i.title).append("\n");
                    sb.append("\n");
                }
                if (!pendingBlocks.isEmpty()) {
                    sb.append("BLOQUES / ELEMENTOS (").append(pendingBlocks.size()).append("):\n");
                    for (ContentBlockEntity b : pendingBlocks) sb.append(" • ").append(b.title != null ? b.title : b.type).append("\n");
                }
            }

            final String report = sb.toString();
            runOnUiThread(() -> {
                DialogHelper.showLightReportDialog(this, "Revisar Detalle de Cambios", report, "Publicar Ahora", () -> publishMasterConfiguration(), "Cerrar");
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
        intent.putExtra("extra_visual_edit_mode", false);
        intent.putExtra("extra_is_preview_mode", true);
        startActivity(intent);
    }

    private void publishMasterConfiguration() {
        DialogHelper.showLightReportDialog(this, "Confirmar Publicación Masiva",
                "¿Desea publicar todos los cambios del borrador actual a la nube? Los asociados verán la nueva versión inmediatamente.",
                "Publicar Todo",
                () -> {
                    Toast.makeText(this, "Validando y publicando...", Toast.LENGTH_SHORT).show();
                    repository.publishCurrentConfiguration(result -> {
                        if (result.getSuccess()) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                            String dateStr = sdf.format(new Date(result.getTimestamp()));

                            String successMsg = "La configuración ha sido publicada correctamente en la nube.\n\n" +
                                    "• Versión Publicada: v" + result.getVersion() + "\n" +
                                    "• Fecha / Hora: " + dateStr + "\n" +
                                    "• Pantallas Publicadas: " + result.getSectionsCount() + "\n" +
                                    "• Elementos Publicados: " + result.getItemsCount();

                            DialogHelper.showLightReportDialog(AdminActivity.this, "¡Publicación Exitosa!", successMsg, null, null, "Aceptar");
                            loadSyncStatusInfo();
                        } else {
                            DialogHelper.showLightReportDialog(AdminActivity.this, "Error de Publicación", "No se pudo publicar la configuración:\n\n" + result.getErrorMessage(), null, null, "Entendido");
                        }
                    });
                },
                "Cancelar"
        );
    }

    private void showSyncReportDialog() {
        new Thread(() -> {
            ColuaRepository.SyncStatusInfo info = repository.getSyncStatusInfo();
            int totalAgencias = repository.getAllAgencias().size();
            int totalBlocks = repository.getAllBlocks().size();

            runOnUiThread(() -> {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                String lastSyncStr = info.getLastSyncTimestamp() > 0 
                        ? sdf.format(new Date(info.getLastSyncTimestamp())) 
                        : "Nunca";

                String status = info.getErrorMessage() == null ? "Conexión Estable" : "Error: " + info.getErrorMessage();

                String reportMsg = "• Estado de Red: " + status + "\n" +
                        "• Versión Local Publicada: v" + info.getLocalVersion() + "\n" +
                        "• Versión Servidor Remoto: v" + info.getRemoteVersion() + "\n" +
                        "• Última Sincronización: " + lastSyncStr + "\n" +
                        "• Total Pantallas: " + info.getSectionsCount() + "\n" +
                        "• Tarjetas de Productos: " + info.getItemsCount() + "\n" +
                        "• Bloques CMS Estructurales: " + totalBlocks + "\n" +
                        "• Agencias y Puntos MICOOPE: " + totalAgencias + "\n" +
                        "• Estado Sincronización Cloud: " + (info.isCloudSyncActive() ? "ACTIVA" : "INACTIVA");

                DialogHelper.showLightReportDialog(this, "Informe de Sincronización", reportMsg, "Refrescar", () -> loadSyncStatusInfo(), "Cerrar");
            });
        }).start();
    }

    private void showDatabaseStatsDialog() {
        new Thread(() -> {
            ColuaRepository.SyncStatusInfo info = repository.getSyncStatusInfo();
            int totalSecs = repository.getAllSections().size();
            int totalItems = repository.getAllItems().size();
            int totalBlocks = repository.getAllBlocks().size();
            int totalAgencias = repository.getAllAgencias().size();
            int pendingItems = (int) repository.getAllItems().stream().filter(i -> i.isDraft).count();
            int pendingBlocks = (int) repository.getAllBlocks().stream().filter(b -> b.isDraft).count();

            String statsMsg = "ESTADÍSTICAS GENERALES DE BASE DE DATOS:\n\n" +
                    "• Versión de Esquema BD: v17 (SQLite / Room)\n" +
                    "• Sincronización Remota: " + (info.isCloudSyncActive() ? "Firestore Cloud Activo" : "Inactivo") + "\n" +
                    "• Versión Publicada Servidor: v" + info.getRemoteVersion() + "\n" +
                    "• Versión Borrador Local: v" + info.getLocalVersion() + "\n\n" +
                    "MÉTRICAS DE REGISTROS:\n" +
                    "• Pantallas Activas: " + totalSecs + "\n" +
                    "• Tarjetas de Producto: " + totalItems + "\n" +
                    "• Bloques Estructurales CMS: " + totalBlocks + "\n" +
                    "• Agencias y Puntos MICOOPE: " + totalAgencias + "\n" +
                    "• Borradores Pendientes: " + (pendingItems + pendingBlocks) + "\n\n" +
                    "ALMACENAMIENTO Y PERSISTENCIA:\n" +
                    "• Motor Local: Room DB con transacciones atómicas.\n" +
                    "• Caché Remoto: Offline Persistence Firestore habilitado.";

            runOnUiThread(() -> {
                DialogHelper.showLightReportDialog(this, "Estadísticas de Base de Datos", statsMsg, "Refrescar", () -> loadSyncStatusInfo(), "Cerrar");
            });
        }).start();
    }

    private void confirmRestore() {
        DialogHelper.showLightReportDialog(this, "Restaurar Datos Iniciales",
                "ADVERTENCIA: Se restaurará el contenido inicial (secciones, agencias y navegación) en MODO BORRADOR.\n\n" +
                        "• Se limpiarán automáticamente registros de prueba antiguos en la nube.\n" +
                        "• NO se borrarán usuarios reales, teléfonos, DPIs, contraseñas ni registros de actividad activos.",
                "Restaurar y Crear Borrador",
                () -> {
                    Toast.makeText(this, "Limpiando registros antiguos y restaurando...", Toast.LENGTH_SHORT).show();
                    repository.purgeOldCollections();
                    repository.restoreInitialDataWithBackup((success, msg) -> {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        loadSyncStatusInfo();
                        setupSectionsList();
                    });
                },
                "Cancelar"
        );
    }

    private void confirmWipeUsers() {
        DialogHelper.showLightReportDialog(this, "Limpieza Profunda de Usuarios",
                "ADVERTENCIA CRÍTICA: Se buscarán y eliminarán todos los perfiles de usuario, registros, sesiones, y dispositivos en Firestore de manera permanente.\n\n" +
                        "• No se modificará el CMS, Noticias o Secciones.\n" +
                        "• Recuerda vaciar también 'Authentication' en la consola de Firebase.\n" +
                        "• Esta acción NO SE PUEDE DESHACER.",
                "CONFIRMAR LIMPIEZA",
                () -> {
                    Toast.makeText(this, "Iniciando eliminación profunda...", Toast.LENGTH_SHORT).show();
                    repository.deepWipeAllUserData(reportMsg -> {
                        runOnUiThread(() -> {
                            DialogHelper.showLightReportDialog(this, "Reporte de Limpieza", reportMsg, "Entendido", () -> loadSyncStatusInfo(), null);
                        });
                        return Unit.INSTANCE;
                    });
                },
                "Cancelar"
        );
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

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
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

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleControlledAdminExit();
    }

    private void handleControlledAdminExit() {
        boolean hasUnpublished = getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .getBoolean("has_unpublished_changes", false);

        if (hasUnpublished) {
            DialogHelper.showUnsavedChangesDialog(this,
                    () -> {
                        Toast.makeText(this, "Borrador guardado localmente.", Toast.LENGTH_SHORT).show();
                        showConfirmLogoutDialog();
                    },
                    () -> {
                        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("has_unpublished_changes", false)
                                .apply();
                        Toast.makeText(this, "Cambios sin publicar descartados.", Toast.LENGTH_SHORT).show();
                        showConfirmLogoutDialog();
                    }
            );
        } else {
            showConfirmLogoutDialog();
        }
    }

    private void showConfirmLogoutDialog() {
        DialogHelper.showConfirmExitDialog(this, () -> performRealLogout());
    }

    private void performRealLogout() {
        if (repository != null) {
            repository.unsubscribeAll();
        }

        if (authManager != null) {
            authManager.logout();
        }

        getSharedPreferences("AdminSecurityPrefs", MODE_PRIVATE).edit().clear().apply();

        if (rvSections != null) {
            rvSections.setAdapter(null);
        }

        Toast.makeText(this, "Sesión administrativa cerrada correctamente.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
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
        boolean isArchived = false;

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

            List<SectionEntity> archivedList = repository.getArchivedSections();
            if (!archivedList.isEmpty()) {
                displayList.add(new AdminSectionListItem("Pantallas Archivadas (En Papelera)"));
                for (SectionEntity s : archivedList) {
                    AdminSectionListItem item = new AdminSectionListItem(s);
                    item.isArchived = true;
                    displayList.add(item);
                }
            }

            runOnUiThread(() -> rvSections.setAdapter(new SectionsAdapter(displayList)));
        }).start();
    }

    private void showChangeLocationDialog(SectionEntity section) {
        String[] options = {"Menú Lateral", "Barra Inferior", "Barra Superior", "Ninguna (NONE)"};
        new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
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

                itemHolder.tvLabel.setText(section.title + (listItem.isArchived ? " (ARCHIVADA)" : ""));
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

                if (listItem.isArchived) {
                    if (itemHolder.colEdit != null) itemHolder.colEdit.setVisibility(View.GONE);
                    if (itemHolder.colPreview != null) itemHolder.colPreview.setVisibility(View.GONE);
                    if (itemHolder.colDelete != null) itemHolder.colDelete.setVisibility(View.VISIBLE);
                    if (itemHolder.colAddContent != null) itemHolder.colAddContent.setVisibility(View.VISIBLE);

                    itemHolder.btnAddContent.setImageResource(android.R.drawable.ic_menu_revert);
                    if (itemHolder.tvBtnAddContent != null) itemHolder.tvBtnAddContent.setText("Restaurar");
                    if (itemHolder.tvBtnDelete != null) itemHolder.tvBtnDelete.setText("Eliminar");

                    View.OnClickListener restoreListener = v -> {
                        new Thread(() -> {
                            repository.restoreArchivedSection(section.id);
                            runOnUiThread(() -> {
                                Toast.makeText(AdminActivity.this, "Pantalla '" + section.title + "' restaurada.", Toast.LENGTH_SHORT).show();
                                setupSectionsList();
                                loadSyncStatusInfo();
                            });
                        }).start();
                    };
                    itemHolder.btnAddContent.setOnClickListener(restoreListener);
                    if (itemHolder.colAddContent != null) itemHolder.colAddContent.setOnClickListener(restoreListener);

                    View.OnClickListener purgeListener = v -> confirmPurgeSection(section);
                    itemHolder.btnDelete.setOnClickListener(purgeListener);
                    if (itemHolder.colDelete != null) itemHolder.colDelete.setOnClickListener(purgeListener);
                } else {
                    if (itemHolder.colEdit != null) itemHolder.colEdit.setVisibility(View.VISIBLE);
                    if (itemHolder.colPreview != null) itemHolder.colPreview.setVisibility(View.VISIBLE);
                    if (itemHolder.colDelete != null) itemHolder.colDelete.setVisibility(View.VISIBLE);
                    if (itemHolder.colAddContent != null) itemHolder.colAddContent.setVisibility(View.VISIBLE);

                    itemHolder.btnAddContent.setImageResource(R.drawable.direccion);
                    if (itemHolder.tvBtnAddContent != null) itemHolder.tvBtnAddContent.setText("Cambiar sitio");
                    if (itemHolder.tvBtnEdit != null) itemHolder.tvBtnEdit.setText("Detalles");
                    if (itemHolder.tvBtnPreview != null) itemHolder.tvBtnPreview.setText("Vista previa");
                    if (itemHolder.tvBtnDelete != null) itemHolder.tvBtnDelete.setText("Eliminar");

                    // Botón 1: Cambiar sitio (+)
                    View.OnClickListener addContentListener = v -> showChangeLocationDialog(section);
                    itemHolder.btnAddContent.setOnClickListener(addContentListener);
                    if (itemHolder.colAddContent != null) itemHolder.colAddContent.setOnClickListener(addContentListener);

                    // Botón 2: Detalles (Editar Sección)
                    View.OnClickListener editListener = v -> {
                        Intent intent = new Intent(AdminActivity.this, AdminSectionEditActivity.class);
                        intent.putExtra(AdminSectionEditActivity.EXTRA_SECTION_ID, section.id);
                        startActivity(intent);
                    };
                    itemHolder.btnEdit.setOnClickListener(editListener);
                    if (itemHolder.colEdit != null) itemHolder.colEdit.setOnClickListener(editListener);

                    // Botón 3: Vista previa
                    View.OnClickListener previewListener = v -> {
                        Class<?> target = DynamicSectionActivity.class;
                        if ("sec_home".equals(section.id)) target = MainActivity.class;
                        else if ("sec_agencias".equals(section.id)) target = AgenciasActivity.class;

                        Intent intent = new Intent(AdminActivity.this, target);
                        intent.putExtra("extra_section_id", section.id);
                        intent.putExtra("extra_visual_edit_mode", false);
                        intent.putExtra("extra_is_preview_mode", true);
                        startActivity(intent);
                    };
                    itemHolder.btnPreview.setOnClickListener(previewListener);
                    if (itemHolder.colPreview != null) itemHolder.colPreview.setOnClickListener(previewListener);

                    // Botón 4: Eliminar / Archivar
                    View.OnClickListener deleteListener = v -> confirmDeleteSection(section);
                    itemHolder.btnDelete.setOnClickListener(deleteListener);
                    if (itemHolder.colDelete != null) itemHolder.colDelete.setOnClickListener(deleteListener);
                }
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

                if (itemHolder.colDelete != null) itemHolder.colDelete.setVisibility(View.GONE);
                if (itemHolder.colEdit != null) itemHolder.colEdit.setVisibility(View.VISIBLE);
                if (itemHolder.colPreview != null) itemHolder.colPreview.setVisibility(View.VISIBLE);
                if (itemHolder.colAddContent != null) itemHolder.colAddContent.setVisibility(View.VISIBLE);

                itemHolder.btnAddContent.setImageResource(R.drawable.editar);
                if (itemHolder.tvBtnAddContent != null) itemHolder.tvBtnAddContent.setText("Borrador");
                if (itemHolder.tvBtnEdit != null) itemHolder.tvBtnEdit.setText("Diseñar");
                if (itemHolder.tvBtnPreview != null) itemHolder.tvBtnPreview.setText("Vista previa");

                // Botón 1: Guardar Borrador local
                View.OnClickListener draftListener = v -> {
                    getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_unpublished_changes", true)
                            .apply();
                    Toast.makeText(AdminActivity.this, "Borrador de '" + section.title + "' guardado localmente.", Toast.LENGTH_SHORT).show();
                    loadSyncStatusInfo();
                };
                itemHolder.btnAddContent.setOnClickListener(draftListener);
                if (itemHolder.colAddContent != null) itemHolder.colAddContent.setOnClickListener(draftListener);

                // Botón 2: Editar (Modo Diseño)
                View.OnClickListener designListener = v -> {
                    Intent intent = new Intent(AdminActivity.this, AdminContentListActivity.class);
                    intent.putExtra(AdminContentListActivity.EXTRA_SECTION_ID, section.id);
                    startActivity(intent);
                };
                itemHolder.btnEdit.setOnClickListener(designListener);
                if (itemHolder.colEdit != null) itemHolder.colEdit.setOnClickListener(designListener);

                // Botón 3: Ver como Usuario (Vista Normal)
                View.OnClickListener previewListener = v -> {
                    Class<?> target = DynamicSectionActivity.class;
                    if ("sec_home".equals(section.id)) target = MainActivity.class;
                    else if ("sec_agencias".equals(section.id)) target = AgenciasActivity.class;

                    Intent intent = new Intent(AdminActivity.this, target);
                    intent.putExtra("extra_section_id", section.id);
                    intent.putExtra("extra_visual_edit_mode", false);
                    intent.putExtra("extra_is_preview_mode", true);
                    startActivity(intent);
                };
                itemHolder.btnPreview.setOnClickListener(previewListener);
                if (itemHolder.colPreview != null) itemHolder.colPreview.setOnClickListener(previewListener);
            }
        }
    }

    private void confirmDeleteSection(SectionEntity section) {
        if ("sec_home".equalsIgnoreCase(section.id)) {
            Toast.makeText(this, "La pantalla 'Inicio' es un componente esencial del sistema y no puede ser eliminada.", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            List<ContentItemEntity> childItems = repository.getItemsBySection(section.id);
            int count = childItems.size();

            runOnUiThread(() -> {
                String warningMsg = "PANTALLA A ARCHIVAR:\n" +
                        "• Título: " + section.title + "\n" +
                        "• Ruta: /" + (section.slug != null && !section.slug.isEmpty() ? section.slug : section.id) + "\n" +
                        "• Elementos contenidos: " + count + " elementos\n\n" +
                        (count > 0 ? "ADVERTENCIA: Esta pantalla contiene " + count + " elementos internos. Se archivará lógicamente y sus referencias del menú serán removidas." : "¿Confirma que desea archivar la pantalla?");

                DialogHelper.showLightReportDialog(AdminActivity.this, "Archivar Pantalla: " + section.title, warningMsg, "ARCHIVAR", () -> {
                    showMasterPasswordVerificationDialog(section, false);
                }, "CANCELAR");
            });
        }).start();
    }

    private void confirmPurgeSection(SectionEntity section) {
        DialogHelper.showLightReportDialog(AdminActivity.this, "Eliminación Permanente: " + section.title,
                "¿Desea eliminar definitivamente la pantalla '" + section.title + "' de la base de datos?\n\nEsta acción NO se puede deshacer.",
                "ELIMINAR DEFINITIVAMENTE",
                () -> showMasterPasswordVerificationDialog(section, true),
                "CANCELAR"
        );
    }

    private void showMasterPasswordVerificationDialog(SectionEntity section, boolean purgePermanently) {
        View dialogView = LayoutInflater.from(AdminActivity.this).inflate(R.layout.dialog_admin_login, null);
        EditText etPassword = dialogView.findViewById(R.id.et_admin_password);
        Button btnLogin = dialogView.findViewById(R.id.btn_login);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        if (btnLogin != null) btnLogin.setText(purgePermanently ? "Confirmar Eliminación Permanente" : "Confirmar Archivado");

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
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                    String auditDate = sdf.format(new Date());
                    if (purgePermanently) {
                        Log.i("AUDIT_ADMIN", "[" + auditDate + "] ELIMINACIÓN PERMANENTE de pantalla " + section.id + " por admin.");
                        repository.purgeSectionPermanently(section.id);
                    } else {
                        Log.i("AUDIT_ADMIN", "[" + auditDate + "] ARCHIVADO LÓGICO de pantalla " + section.id + " por admin.");
                        repository.archiveSection(section.id);
                    }
                    runOnUiThread(() -> {
                        String msg = purgePermanently ? "Pantalla '" + section.title + "' eliminada permanentemente." : "Pantalla '" + section.title + "' archivada correctamente.";
                        Toast.makeText(AdminActivity.this, msg, Toast.LENGTH_SHORT).show();
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
        View colAddContent, colEdit, colPreview, colDelete;
        ImageView btnAddContent, btnEdit, btnPreview, btnDelete;
        TextView tvBtnAddContent, tvBtnEdit, tvBtnPreview, tvBtnDelete;

        ItemViewHolder(View v) {
            super(v);
            sideBorder = v.findViewById(R.id.side_border_menu);
            ivIcon = v.findViewById(R.id.iv_menu_icon);
            tvLabel = v.findViewById(R.id.tv_menu_label);
            tvPath = v.findViewById(R.id.tv_menu_path);
            tvOrder = v.findViewById(R.id.tv_menu_order);

            colAddContent = v.findViewById(R.id.col_add_content);
            colEdit = v.findViewById(R.id.col_edit_section);
            colPreview = v.findViewById(R.id.col_preview_section);
            colDelete = v.findViewById(R.id.col_delete_section);

            btnAddContent = v.findViewById(R.id.btn_add_content);
            btnEdit = v.findViewById(R.id.btn_edit_section);
            btnPreview = v.findViewById(R.id.btn_preview_section);
            btnDelete = v.findViewById(R.id.btn_delete_section);

            tvBtnAddContent = v.findViewById(R.id.tv_btn_add_content);
            tvBtnEdit = v.findViewById(R.id.tv_btn_edit_section);
            tvBtnPreview = v.findViewById(R.id.tv_btn_preview_section);
            tvBtnDelete = v.findViewById(R.id.tv_btn_delete_section);
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

    private String formatUserTimestamp(Object obj) {
        if (obj == null) return "No registrada";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            if (obj instanceof Timestamp) {
                return sdf.format(((Timestamp) obj).toDate());
            } else if (obj instanceof Long) {
                long val = (Long) obj;
                if (val <= 0) return "No registrada";
                return sdf.format(new Date(val));
            } else if (obj instanceof Double) {
                long val = ((Double) obj).longValue();
                if (val <= 0) return "No registrada";
                return sdf.format(new Date(val));
            } else if (obj instanceof Date) {
                return sdf.format((Date) obj);
            } else if (obj instanceof String) {
                String str = (String) obj;
                if (str.trim().isEmpty()) return "No registrada";
                try {
                    long l = Long.parseLong(str);
                    return sdf.format(new Date(l));
                } catch (Exception e) {
                    return str;
                }
            }
        } catch (Exception e) {
            return String.valueOf(obj);
        }
        return String.valueOf(obj);
    }

    private void setupUserFilterWidget(List rawUserList) {
        EditText etFilterName = findViewById(R.id.et_filter_name);
        MaterialButton btnFilterType = findViewById(R.id.btn_filter_type);
        MaterialButton btnFilterSort = findViewById(R.id.btn_filter_sort);
        LinearLayout containerUsers = findViewById(R.id.container_filtered_users);
        TextView tvCounter = findViewById(R.id.tv_filter_counter);

        if (etFilterName == null || containerUsers == null) return;
        final List userList = rawUserList != null ? rawUserList : new ArrayList<>();

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
                currentFilterSort = "Antiguos";
                btnFilterSort.setText("Orden: Antiguos");
            } else if ("Antiguos".equals(currentFilterSort)) {
                currentFilterSort = "Nombre A-Z";
                btnFilterSort.setText("Orden: Nombre A-Z");
            } else {
                currentFilterSort = "Recientes";
                btnFilterSort.setText("Orden: Recientes");
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

    @SuppressWarnings("unchecked")
    private void filterAndDisplayUsers(List userList, String query, String typeFilter, String sortFilter, LinearLayout container, TextView tvCounter) {
        container.removeAllViews();
        int matched = 0;

        List<Map<String, Object>> filteredList = new ArrayList<>();

        for (Object obj : userList) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> u = (Map<String, Object>) obj;

            String name = String.valueOf(u.get("nombre") != null ? u.get("nombre") : "Usuario");
            String tipo = String.valueOf(u.get("tipoUsuario") != null ? u.get("tipoUsuario") : "ASOCIADO");
            String dpi = String.valueOf(u.get("dpi") != null ? u.get("dpi") : "");
            String phone = String.valueOf(u.get("telefono") != null ? u.get("telefono") : (u.get("telefonoCompleto") != null ? u.get("telefonoCompleto") : ""));
            String userId = String.valueOf(u.get("userId") != null ? u.get("userId") : (u.get("idNumerico") != null ? u.get("idNumerico") : ""));

            String fechaRegistroStr = formatUserTimestamp(u.get("fechaRegistro"));
            String ultimaActividadStr = formatUserTimestamp(u.get("ultimaActividad"));

            boolean matchesType = "Todos".equals(typeFilter) ||
                ("Asociados".equals(typeFilter) && ("ASOCIADO".equalsIgnoreCase(tipo) || "MEMBER".equalsIgnoreCase(tipo) || "ADMIN".equalsIgnoreCase(tipo))) ||
                ("Invitados".equals(typeFilter) && !("ASOCIADO".equalsIgnoreCase(tipo) || "MEMBER".equalsIgnoreCase(tipo) || "ADMIN".equalsIgnoreCase(tipo)));

            String q = query.trim().toLowerCase(Locale.getDefault());

            boolean matchesQuery = q.isEmpty()
                || name.toLowerCase(Locale.getDefault()).contains(q)
                || dpi.toLowerCase(Locale.getDefault()).contains(q)
                || phone.toLowerCase(Locale.getDefault()).contains(q)
                || userId.toLowerCase(Locale.getDefault()).contains(q)
                || fechaRegistroStr.toLowerCase(Locale.getDefault()).contains(q)
                || ultimaActividadStr.toLowerCase(Locale.getDefault()).contains(q);

            if (matchesType && matchesQuery) {
                filteredList.add(u);
            }
        }

        // Ordenamiento
        if ("Nombre A-Z".equals(sortFilter)) {
            Collections.sort(filteredList, (u1, u2) -> {
                String n1 = String.valueOf(u1.get("nombre") != null ? u1.get("nombre") : "");
                String n2 = String.valueOf(u2.get("nombre") != null ? u2.get("nombre") : "");
                return n1.compareToIgnoreCase(n2);
            });
        } else if ("Antiguos".equals(sortFilter)) {
            Collections.sort(filteredList, (u1, u2) -> {
                long t1 = parseTimestampToLong(u1.get("fechaRegistro"));
                long t2 = parseTimestampToLong(u2.get("fechaRegistro"));
                return Long.compare(t1, t2);
            });
        } else { // "Recientes" (predeterminado)
            Collections.sort(filteredList, (u1, u2) -> {
                long t1 = parseTimestampToLong(u1.get("ultimaActividad"));
                if (t1 == 0) t1 = parseTimestampToLong(u1.get("fechaRegistro"));
                long t2 = parseTimestampToLong(u2.get("ultimaActividad"));
                if (t2 == 0) t2 = parseTimestampToLong(u2.get("fechaRegistro"));
                return Long.compare(t2, t1); // Descendente
            });
        }

        int itemIndex = 0;
        for (Map<String, Object> u : filteredList) {
            itemIndex++;
            matched++;
            String name = String.valueOf(u.get("nombre") != null ? u.get("nombre") : "Usuario");
            String tipo = String.valueOf(u.get("tipoUsuario") != null ? u.get("tipoUsuario") : "ASOCIADO");
            String dpi = String.valueOf(u.get("dpi") != null ? u.get("dpi") : "N/A");
            String phone = String.valueOf(u.get("telefono") != null ? u.get("telefono") : (u.get("telefonoCompleto") != null ? u.get("telefonoCompleto") : "N/A"));
            String userId = String.valueOf(u.get("userId") != null ? u.get("userId") : (u.get("idNumerico") != null ? u.get("idNumerico") : "N/A"));

            String fechaRegistroStr = formatUserTimestamp(u.get("fechaRegistro"));
            String ultimaActividadStr = formatUserTimestamp(u.get("ultimaActividad"));

            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.VERTICAL);
            itemRow.setPadding(16, 12, 16, 12);
            itemRow.setBackgroundColor(Color.parseColor("#FFFFFF"));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 8);
            itemRow.setLayoutParams(params);

            TextView tvNameRole = new TextView(this);
            tvNameRole.setText("#" + itemIndex + ". " + name + " (" + tipo + ") - ID: " + userId);
            tvNameRole.setTextColor(Color.parseColor("#173789"));
            tvNameRole.setTextSize(13f);
            tvNameRole.setTypeface(null, Typeface.BOLD);

            TextView tvRow1 = new TextView(this);
            tvRow1.setText("DPI: " + dpi + " | Teléfono: " + phone);
            tvRow1.setTextColor(Color.parseColor("#334155"));
            tvRow1.setTextSize(11f);

            TextView tvRow2 = new TextView(this);
            tvRow2.setText("Registro: " + fechaRegistroStr + " | Última Actividad: " + ultimaActividadStr);
            tvRow2.setTextColor(Color.parseColor("#64748B"));
            tvRow2.setTextSize(11f);

            itemRow.addView(tvNameRole);
            itemRow.addView(tvRow1);
            itemRow.addView(tvRow2);
            container.addView(itemRow);
        }

        if (tvCounter != null) {
            tvCounter.setText("Mostrando " + matched + " usuarios coincidentes");
        }
    }

    private long parseTimestampToLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Timestamp) {
            return ((Timestamp) obj).toDate().getTime();
        } else if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Double) {
            return ((Double) obj).longValue();
        } else if (obj instanceof Date) {
            return ((Date) obj).getTime();
        } else if (obj instanceof String) {
            try { return Long.parseLong((String) obj); } catch (Exception ignored) {}
        }
        return 0L;
    }
}
