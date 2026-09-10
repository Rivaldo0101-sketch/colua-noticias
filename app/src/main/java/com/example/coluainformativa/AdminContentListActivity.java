package com.example.coluainformativa;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AgenciaEntity;
import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.ContentBlockEntity;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.ui.content.ContentItemAdapter;
import com.example.coluainformativa.ui.content.ScreenSelectorAdapter;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.example.coluainformativa.ui.content.ElementTypeAdapter;
import com.example.coluainformativa.utils.DialogHelper;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminContentListActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_MANAGE_AGENCIAS = "extra_manage_agencias";
    private static final int REQUEST_PICK_ISSUER_AVATAR = 105;

    private ColuaRepository repository;
    private String sectionId;
    private boolean manageAgencias = false;
    private SectionEntity section;
    private List<SectionEntity> allSections = new ArrayList<>();
    private List<Object> allItems = new ArrayList<>();
    private List<Object> filteredList = new ArrayList<>();
    private List<Object> paginatedList = new ArrayList<>();
    private ContentAdapter adapter;
    private Spinner spinnerScreenSelector;

    private String searchQuery = "";
    private int currentPage = 1;
    private int itemsPerPage = 6;

    private String currentIssuerAvatarPath = "";
    private ImageView ivDialogIssuerAvatar = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_content_list);

        repository = new ColuaRepository(this);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);
        manageAgencias = getIntent().getBooleanExtra(EXTRA_MANAGE_AGENCIAS, false);

        if ("sec_agencias".equalsIgnoreCase(sectionId) || "agencias".equalsIgnoreCase(sectionId)) {
            manageAgencias = true;
        }

        if (sectionId == null && !manageAgencias) {
            sectionId = "sec_creditos"; // Por defecto Créditos
        }

        findViewById(R.id.btn_back_content_list).setOnClickListener(v -> finish());

        // Botón Principal: + Agregar Elemento al Canvas / Crear Agencia / Crear Nueva Publicación
        ExtendedFloatingActionButton btnAdd = findViewById(R.id.btn_add_item);
        if (btnAdd != null) {
            if (manageAgencias || "sec_agencias".equalsIgnoreCase(sectionId)) {
                btnAdd.setText("Agregar Agencia / Punto");
            } else if ("sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
                btnAdd.setText("Crear Nueva Publicación");
            } else {
                btnAdd.setText("Agregar Elemento al Canvas");
            }

            btnAdd.setOnClickListener(v -> {
                if (manageAgencias || "sec_agencias".equalsIgnoreCase(sectionId)) {
                    Intent intent = new Intent(this, AdminAgenciaEditActivity.class);
                    startActivity(intent);
                } else if ("sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
                    Intent intent = new Intent(this, AdminNewsEditActivity.class);
                    startActivity(intent);
                } else {
                    showAddElementOptionsDialog();
                }
            });
        }

        // Botón Propiedades de la Sección
        View btnEditProps = findViewById(R.id.btn_edit_section_properties);
        if (btnEditProps != null) {
            btnEditProps.setOnClickListener(v -> {
                if (sectionId != null) {
                    Intent intent = new Intent(this, AdminSectionEditActivity.class);
                    intent.putExtra(AdminSectionEditActivity.EXTRA_SECTION_ID, sectionId);
                    startActivity(intent);
                }
            });
        }

        // Acciones de Canvas: Borrador, Vista Previa, Publicar
        View btnSaveDraft = findViewById(R.id.btn_save_draft_canvas);
        if (btnSaveDraft != null) {
            btnSaveDraft.setOnClickListener(v -> saveCanvasDraft());
        }

        View btnPreview = findViewById(R.id.btn_preview_canvas);
        if (btnPreview != null) {
            btnPreview.setOnClickListener(v -> openCanvasPreview());
        }

        View btnPublish = findViewById(R.id.btn_publish_canvas);
        if (btnPublish != null) {
            btnPublish.setOnClickListener(v -> publishCanvasConfiguration());
        }

        View cardSelectScreen = findViewById(R.id.card_select_screen);
        if (cardSelectScreen != null) {
            cardSelectScreen.setOnClickListener(v -> showSelectScreenDialog());
        }

        setupSearch();
        setupFilters();

        RecyclerView rv = findViewById(R.id.rv_admin_items);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContentAdapter();
        rv.setAdapter(adapter);

        setupScreenSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void setupScreenSelector() {
        new Thread(() -> {
            allSections = repository.getAllSections();
            Collections.sort(allSections, (s1, s2) -> Integer.compare(s1.displayOrder, s2.displayOrder));

            SectionEntity selected = null;
            for (SectionEntity s : allSections) {
                if (s.id.equalsIgnoreCase(sectionId)) {
                    selected = s;
                    break;
                }
            }
            if (selected == null && !allSections.isEmpty()) {
                selected = allSections.get(0);
                sectionId = selected.id;
            }

            final SectionEntity finalSelected = selected;
            runOnUiThread(() -> {
                if (finalSelected != null) {
                    updateSelectedScreenHeader(finalSelected);
                }
                loadData();
            });
        }).start();
    }

    private void updateSelectedScreenHeader(SectionEntity s) {
        if (s == null) return;
        sectionId = s.id;
        manageAgencias = "sec_agencias".equalsIgnoreCase(sectionId) || "agencias".equalsIgnoreCase(sectionId);

        TextView tvLabel = findViewById(R.id.tv_selected_screen_label);
        if (tvLabel != null) {
            tvLabel.setText(s.title + " (/" + (s.slug != null && !s.slug.isEmpty() ? s.slug : s.id) + ")");
        }

        TextView tvTitle = findViewById(R.id.tv_admin_section_title);
        if (tvTitle != null) {
            tvTitle.setText("Canvas de " + s.title);
        }

        ExtendedFloatingActionButton fab = findViewById(R.id.btn_add_item);
        if (fab != null) {
            if (manageAgencias) {
                fab.setText("Agregar Agencia / Punto");
            } else if ("sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
                fab.setText("Crear Nueva Publicación");
            } else {
                fab.setText("Agregar Elemento al Canvas");
            }
        }
    }

    private void showSelectScreenDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_select_screen, null);
        RecyclerView rvScreens = dialogView.findViewById(R.id.rv_screens);
        EditText etSearch = dialogView.findViewById(R.id.et_search_screen);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_screen_dialog);

        List<ScreenSelectorAdapter.ScreenItem> items = new ArrayList<>();
        if (allSections != null) {
            for (SectionEntity s : allSections) {
                int iconRes = getResources().getIdentifier(s.iconName, "drawable", getPackageName());
                if (iconRes == 0) iconRes = R.drawable.ic_home;
                items.add(new ScreenSelectorAdapter.ScreenItem(s.id, s.title, s.slug != null && !s.slug.isEmpty() ? s.slug : s.id, iconRes));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ScreenSelectorAdapter adapterAdapter = new ScreenSelectorAdapter(items, selectedItem -> {
            dialog.dismiss();
            sectionId = selectedItem.sectionId;
            SectionEntity matched = null;
            if (allSections != null) {
                for (SectionEntity s : allSections) {
                    if (s.id.equalsIgnoreCase(sectionId)) {
                        matched = s;
                        break;
                    }
                }
            }
            updateSelectedScreenHeader(matched);
            loadData();
        });

        if (rvScreens != null) {
            rvScreens.setLayoutManager(new LinearLayoutManager(this));
            rvScreens.setAdapter(adapterAdapter);
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapterAdapter.filter(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void saveCanvasDraft() {
        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();
        String title = section != null ? section.title : "pantalla";
        Toast.makeText(this, "Borrador de '" + title + "' guardado localmente", Toast.LENGTH_SHORT).show();
    }

    private void openCanvasPreview() {
        Class<?> target = DynamicSectionActivity.class;
        if ("sec_home".equalsIgnoreCase(sectionId)) target = MainActivity.class;
        else if ("sec_agencias".equalsIgnoreCase(sectionId)) target = AgenciasActivity.class;

        Intent intent = new Intent(this, target);
        intent.putExtra("extra_section_id", sectionId);
        intent.putExtra("extra_visual_edit_mode", false);
        intent.putExtra("extra_is_preview_mode", true);
        startActivity(intent);
    }

    private void publishCanvasConfiguration() {
        String title = section != null ? section.title : "pantalla";
        DialogHelper.showLightReportDialog(
                this,
                "Confirmar Publicación Oficial",
                "¿Desea publicar todos los cambios de esta pantalla a la nube? Los asociados verán la nueva versión de '" + title + "' inmediatamente.",
                "PUBLICAR CAMBIOS",
                () -> {
                    Toast.makeText(this, "Publicando contenido...", Toast.LENGTH_SHORT).show();
                    repository.publishCurrentConfiguration(result -> {
                        if (result.getSuccess()) {
                            Toast.makeText(this, "¡Contenido publicado exitosamente!", Toast.LENGTH_SHORT).show();
                            loadData();
                        } else {
                            Toast.makeText(this, "Error: " + result.getErrorMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                },
                "CANCELAR"
        );
    }

    private void showAddElementOptionsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_element, null);
        RecyclerView rvTypes = dialogView.findViewById(R.id.rv_element_types);
        EditText etSearch = dialogView.findViewById(R.id.et_search_element_type);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_categories);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_dialog);

        List<ElementTypeAdapter.ElementTypeItem> items = new ArrayList<>();
        items.add(new ElementTypeAdapter.ElementTypeItem("TEXT", "Texto", "Título, Subtítulo, Párrafo, Informativo", R.drawable.ic_newspaper, "#EFF6FF", "#1D4ED8", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("IMAGE", "Imagen", "Logo, Banner, Fotografía, Ilustración", R.drawable.contenido_depantallas, "#DCFCE7", "#15803D", "Multimedia", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("ICON", "Ícono", "Ícono de Galería o Vectorial con estilo", R.drawable.ic_star, "#F3E8FF", "#7E22CE", "Multimedia", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("BUTTON", "Botón", "Navegación, PBX / Llamada, Enlace web", R.drawable.ic_campaign, "#FEF3C7", "#B45309", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("CARD", "Tarjeta", "Contenedor visual con borde, elevación y sombra", R.drawable.ic_credit_card, "#E0F2FE", "#0369A1", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("PRODUCT_CARD", "Tarjeta de Producto", "Crédito, Ahorro, Seguro, Remesa COLUA", R.drawable.ic_account_balance, "#0F172A", "#10B981", "Financiero", true));
        items.add(new ElementTypeAdapter.ElementTypeItem("CONTAINER", "Sección / Contenedor", "Agrupa elementos en pantalla sin tarjeta", R.drawable.ic_business, "#F1F5F9", "#475569", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("LIST", "Lista", "Viñetas, Beneficios, Requisitos por puntos", R.drawable.ic_receipt, "#E0F2FE", "#0284C7", "Básicos", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("SEPARATOR", "Separador", "Línea divisora horizontal de sección", R.drawable.ic_calculate, "#F1F5F9", "#64748B", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("SPACER", "Espaciador", "Espaciado vertical entre bloques", R.drawable.ic_calculate, "#F3E8FF", "#9333EA", "Estructura", false));
        items.add(new ElementTypeAdapter.ElementTypeItem("BANNER", "Banner Destacado", "Imagen o bloque promocional ancho completo", R.drawable.ic_trending_up, "#DCFCE7", "#16A34A", "Multimedia", false));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ElementTypeAdapter adapter = new ElementTypeAdapter(items, item -> {
            dialog.dismiss();
            String selectedType = item.key;

            if ("PRODUCT_CARD".equals(selectedType)) {
                Intent intent = new Intent(this, AdminContentEditActivity.class);
                intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, sectionId);
                intent.putExtra("extra_element_type", selectedType);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, AdminBlockEditActivity.class);
                intent.putExtra(AdminBlockEditActivity.EXTRA_SECTION_ID, sectionId);
                intent.putExtra(AdminBlockEditActivity.EXTRA_BLOCK_TYPE, selectedType);
                startActivity(intent);
            }
        });

        if (rvTypes != null) {
            rvTypes.setLayoutManager(new LinearLayoutManager(this));
            rvTypes.setAdapter(adapter);
        }

        final String[] selectedCat = {"Todos"};
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s.toString(), selectedCat[0]);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (chipGroup != null) {
            chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    selectedCat[0] = "Todos";
                } else {
                    int id = checkedIds.get(0);
                    if (id == R.id.chip_cat_basics) selectedCat[0] = "Básicos";
                    else if (id == R.id.chip_cat_media) selectedCat[0] = "Multimedia";
                    else if (id == R.id.chip_cat_structure) selectedCat[0] = "Estructura";
                    else if (id == R.id.chip_cat_financial) selectedCat[0] = "Financiero";
                    else selectedCat[0] = "Todos";
                }
                String q = etSearch != null ? etSearch.getText().toString() : "";
                adapter.filter(q, selectedCat[0]);
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search_admin);
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                applyFilters();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilters() {
        ChipGroup cg = findViewById(R.id.cg_filters_admin);
        if (cg == null) return;
        cg.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
    }

    private void loadData() {
        findViewById(R.id.pb_admin_loading).setVisibility(View.VISIBLE);
        findViewById(R.id.rv_admin_items).setVisibility(View.GONE);
        findViewById(R.id.tv_admin_empty).setVisibility(View.GONE);

        new Thread(() -> {
            try {
                String canonicalId = repository.resolveSectionId(sectionId != null ? sectionId : "sec_noticias");

                if (manageAgencias) {
                    List<AgenciaEntity> rawAgencias = repository.getAllAgencias();
                    Map<String, AgenciaEntity> uniqueAgencias = new LinkedHashMap<>();
                    for (AgenciaEntity a : rawAgencias) {
                        if (a != null && a.nombre != null) {
                            String key = (a.nombre.trim() + "_" + (a.departamento != null ? a.departamento.trim() : "")).toLowerCase(Locale.getDefault());
                            if (!uniqueAgencias.containsKey(key)) {
                                uniqueAgencias.put(key, a);
                            }
                        }
                    }
                    allItems.clear();
                    allItems.addAll(uniqueAgencias.values());

                    repository.purgarAgenciasDuplicadas(() -> null);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        TextView tvTitle = findViewById(R.id.tv_admin_section_title);
                        if (tvTitle != null) tvTitle.setText("Gestionar Agencias (" + uniqueAgencias.size() + ")");
                        applyFilters();
                    });
                } else {
                    section = AppDatabase.getDatabase(this).sectionDao().getSectionById(canonicalId);
                    List<ContentItemEntity> items = repository.getItemsBySection(canonicalId);
                    List<ContentBlockEntity> blocks = repository.getBlocksBySection(canonicalId);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        TextView tvTitle = findViewById(R.id.tv_admin_section_title);
                        if (tvTitle != null) {
                            if (section != null && section.title != null) {
                                tvTitle.setText("Canvas de " + section.title);
                            } else if ("sec_noticias".equalsIgnoreCase(canonicalId) || "noticias".equalsIgnoreCase(canonicalId)) {
                                tvTitle.setText("Canvas de Noticias");
                            } else {
                                tvTitle.setText("Canvas de Contenido");
                            }
                        }
                        allItems.clear();
                        if (items != null) allItems.addAll(items);
                        if (blocks != null) allItems.addAll(blocks);
                        setupIssuerProfileHeader();
                        applyFilters();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Error al cargar canvas", Toast.LENGTH_SHORT).show();
                });
            } finally {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        findViewById(R.id.pb_admin_loading).setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    private void applyFilters() {
        if (allItems == null) return;

        filteredList = allItems.stream().filter(obj -> {
            if (obj == null) return false;
            if (obj instanceof ContentItemEntity) {
                ContentItemEntity item = (ContentItemEntity) obj;
                String title = item.title != null ? item.title.toLowerCase(Locale.getDefault()) : "";
                String shortDesc = item.shortDescription != null ? item.shortDescription.toLowerCase(Locale.getDefault()) : "";
                String desc = item.description != null ? item.description.toLowerCase(Locale.getDefault()) : "";
                return searchQuery.isEmpty() || title.contains(searchQuery) || shortDesc.contains(searchQuery) || desc.contains(searchQuery);
            } else if (obj instanceof AgenciaEntity) {
                AgenciaEntity a = (AgenciaEntity) obj;
                String nombre = a.nombre != null ? a.nombre.toLowerCase(Locale.getDefault()) : "";
                String depto = a.departamento != null ? a.departamento.toLowerCase(Locale.getDefault()) : "";
                return searchQuery.isEmpty() || nombre.contains(searchQuery) || depto.contains(searchQuery);
            } else if (obj instanceof ContentBlockEntity) {
                ContentBlockEntity b = (ContentBlockEntity) obj;
                String title = b.title != null ? b.title.toLowerCase(Locale.getDefault()) : "";
                String content = b.content != null ? b.content.toLowerCase(Locale.getDefault()) : "";
                return searchQuery.isEmpty() || title.contains(searchQuery) || content.contains(searchQuery);
            }
            return true;
        }).collect(Collectors.toList());

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            boolean empty = filteredList.isEmpty();
            TextView tvEmpty = findViewById(R.id.tv_admin_empty);
            if (tvEmpty != null) {
                if ("sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
                    tvEmpty.setText("Aún no hay publicaciones. Crea la primera noticia.");
                } else {
                    tvEmpty.setText("Esta pantalla aún no tiene bloques internos.\n\nToca '+ Agregar Elemento' abajo para diseñar tarjetas, títulos, imágenes o botones.");
                }
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            findViewById(R.id.rv_admin_items).setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        currentPage = 1;
        updatePagination();
    }

    private void setupIssuerProfileHeader() {
        View cardIssuer = findViewById(R.id.card_admin_issuer_profile);
        if (cardIssuer == null) return;

        boolean isNoticias = "sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId);
        cardIssuer.setVisibility(isNoticias ? View.VISIBLE : View.GONE);

        if (!isNoticias) return;

        new Thread(() -> {
            String issuerName = repository.getGlobalConfig("issuer_name");
            if (issuerName.isEmpty()) issuerName = "Cooperativa COLUA";

            String issuerRole = repository.getGlobalConfig("issuer_role");
            if (issuerRole.isEmpty()) issuerRole = "Publicando como cuenta oficial";

            String issuerAvatar = repository.getGlobalConfig("issuer_avatar");
            if (issuerAvatar.isEmpty()) issuerAvatar = "distintivo_colua";

            final String finalName = issuerName;
            final String finalRole = issuerRole;
            final String finalAvatar = issuerAvatar;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                TextView tvName = findViewById(R.id.tv_admin_issuer_name);
                TextView tvRole = findViewById(R.id.tv_admin_issuer_role);
                ImageView ivAvatar = findViewById(R.id.iv_admin_issuer_avatar);

                if (tvName != null) tvName.setText(finalName);
                if (tvRole != null) tvRole.setText(finalRole);

                if (ivAvatar != null) {
                    boolean loaded = false;
                    if (finalAvatar.startsWith("content://") || finalAvatar.startsWith("file://")) {
                        try {
                            ivAvatar.setImageURI(Uri.parse(finalAvatar));
                            if (ivAvatar.getDrawable() != null) loaded = true;
                        } catch (Throwable ignored) {}
                    }
                    if (!loaded) {
                        int resId = getResources().getIdentifier(finalAvatar, "drawable", getPackageName());
                        if (resId != 0) {
                            ivAvatar.setImageResource(resId);
                            loaded = true;
                        }
                    }
                    if (!loaded) {
                        ivAvatar.setImageResource(R.drawable.distintivo_colua);
                    }
                }

                View btnEdit = findViewById(R.id.btn_edit_admin_issuer_profile);
                if (btnEdit != null) {
                    btnEdit.setOnClickListener(v -> showEditIssuerProfileDialogGlobal());
                }
                cardIssuer.setOnClickListener(v -> showEditIssuerProfileDialogGlobal());
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_ISSUER_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String localPath = saveImageToInternalStorage(uri);
            if (localPath != null && !localPath.isEmpty()) {
                currentIssuerAvatarPath = localPath;
                if (ivDialogIssuerAvatar != null) {
                    AdminNewsEditActivity.loadNewsImageIntoView(this, ivDialogIssuerAvatar, localPath);
                }
                ImageView ivHeaderAvatar = findViewById(R.id.iv_admin_issuer_avatar);
                if (ivHeaderAvatar != null) {
                    AdminNewsEditActivity.loadNewsImageIntoView(this, ivHeaderAvatar, localPath);
                }
                Toast.makeText(this, "Foto de perfil cargada. Presiona 'Guardar' para aplicar.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String saveImageToInternalStorage(Uri sourceUri) {
        if (sourceUri == null) return null;
        try {
            File dir = new File(getFilesDir(), "news_images");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "avatar_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(dir, fileName);

            try (InputStream in = getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("COLUA_CMS", "Error copiando avatar a almacenamiento interno", e);
            return null;
        }
    }

    private void launchDevicePhotoPickerForIssuerProfile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Seleccionar foto de perfil del emisor"), REQUEST_PICK_ISSUER_AVATAR);
        } catch (Exception e) {
            Toast.makeText(this, "Error al abrir selector de fotos", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditIssuerProfileDialogGlobal() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_issuer_profile, null);

        EditText etName = view.findViewById(R.id.et_dialog_issuer_name);
        EditText etRole = view.findViewById(R.id.et_dialog_issuer_role);
        ivDialogIssuerAvatar = view.findViewById(R.id.iv_dialog_issuer_avatar);
        View btnAvatarCircle = view.findViewById(R.id.btn_change_issuer_avatar_circle);
        Button btnSave = view.findViewById(R.id.btn_dialog_issuer_save);
        Button btnCancel = view.findViewById(R.id.btn_dialog_issuer_cancel);

        TextView tvCurrentName = findViewById(R.id.tv_admin_issuer_name);
        TextView tvCurrentRole = findViewById(R.id.tv_admin_issuer_role);

        if (etName != null) {
            etName.setText(tvCurrentName != null && tvCurrentName.getText() != null ? tvCurrentName.getText().toString() : "Cooperativa COLUA");
        }
        if (etRole != null) {
            etRole.setText(tvCurrentRole != null && tvCurrentRole.getText() != null ? tvCurrentRole.getText().toString() : "Publicando como cuenta oficial");
        }

        currentIssuerAvatarPath = repository.getGlobalConfig("issuer_avatar");
        if (currentIssuerAvatarPath.isEmpty()) currentIssuerAvatarPath = "distintivo_colua";

        if (ivDialogIssuerAvatar != null) {
            AdminNewsEditActivity.loadNewsImageIntoView(this, ivDialogIssuerAvatar, currentIssuerAvatarPath);
        }

        if (btnAvatarCircle != null) {
            btnAvatarCircle.setOnClickListener(v -> {
                DialogHelper.showResourcePickerDialog(this, (resName, displayLabel) -> {
                    if (resName != null && !resName.isEmpty()) {
                        currentIssuerAvatarPath = resName;
                        if (ivDialogIssuerAvatar != null) {
                            AdminNewsEditActivity.loadNewsImageIntoView(this, ivDialogIssuerAvatar, resName);
                        }
                    }
                }, () -> {
                    launchDevicePhotoPickerForIssuerProfile();
                });
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String newName = etName != null ? etName.getText().toString().trim() : "Cooperativa COLUA";
                String newRole = etRole != null ? etRole.getText().toString().trim() : "Publicando como cuenta oficial";

                if (newName.isEmpty()) newName = "Cooperativa COLUA";
                if (newRole.isEmpty()) newRole = "Publicando como cuenta oficial";

                final String finalName = newName;
                final String finalRole = newRole;
                final String finalAvatar = currentIssuerAvatarPath;

                dialog.dismiss();

                // Confirmar con contraseña de administrador antes de aplicar cambios
                DialogHelper.showPasswordConfirmationDialog(
                        this,
                        new AdminAuthManager(this),
                        "Confirmar Cambios de Perfil",
                        "Para actualizar el Perfil Emisor oficial e ingresar su firma en todas las noticias, ingrese su clave de administrador:",
                        () -> {
                            new Thread(() -> {
                                repository.setGlobalConfig("issuer_name", finalName);
                                repository.setGlobalConfig("issuer_role", finalRole);
                                repository.setGlobalConfig("issuer_avatar", finalAvatar);

                                List<ContentItemEntity> allNews = repository.getItemsBySection("sec_noticias");
                                for (ContentItemEntity news : allNews) {
                                    news.issuerName = finalName;
                                    news.issuerRole = finalRole;
                                    news.iconName = finalAvatar;
                                    repository.insertItem(news);
                                }

                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    Toast.makeText(this, "¡Perfil Emisor actualizado con éxito!", Toast.LENGTH_SHORT).show();
                                    setupIssuerProfileHeader();
                                    loadData();
                                });
                            }).start();
                        }
                );
            });
        }

        dialog.show();
    }

    private void updatePagination() {
        int totalItems = filteredList.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        if (totalPages <= 1) {
            View layoutPag = findViewById(R.id.layout_pagination);
            if (layoutPag != null) layoutPag.setVisibility(View.GONE);
            paginatedList.clear();
            paginatedList.addAll(filteredList);
        } else {
            View layoutPag = findViewById(R.id.layout_pagination);
            if (layoutPag != null) layoutPag.setVisibility(View.VISIBLE);
            int start = (currentPage - 1) * itemsPerPage;
            int end = Math.min(start + itemsPerPage, totalItems);

            paginatedList.clear();
            paginatedList.addAll(filteredList.subList(start, end));
            setupPaginationBar(totalPages);
        }
        adapter.notifyDataSetChanged();
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

    class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {
        private static final int TYPE_STANDARD = 0;
        private static final int TYPE_NEWS = 1;

        @Override
        public int getItemViewType(int position) {
            Object obj = paginatedList.get(position);
            if (obj instanceof ContentItemEntity) {
                ContentItemEntity item = (ContentItemEntity) obj;
                if ("sec_noticias".equalsIgnoreCase(item.sectionId) || "noticias".equalsIgnoreCase(item.sectionId) || "sec_noticias".equalsIgnoreCase(sectionId) || "noticias".equalsIgnoreCase(sectionId)) {
                    return TYPE_NEWS;
                }
            }
            return TYPE_STANDARD;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v;
            if (viewType == TYPE_NEWS) {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_news_card, parent, false);
            } else {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_content, parent, false);
            }
            return new ViewHolder(v, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object obj = paginatedList.get(position);

            if (obj instanceof ContentItemEntity) {
                ContentItemEntity item = (ContentItemEntity) obj;

                if (getItemViewType(position) == TYPE_NEWS) {
                    if (holder.tvNewsFeaturedTag != null) {
                        holder.tvNewsFeaturedTag.setText(item.isFeatured ? "🟢 NOTICIA DESTACADA #" + (position + 1) : "⚫ NOTICIA #" + (position + 1));
                    }

                    if (holder.tvNewsStatusBadge != null) {
                        String statusText = item.isDraft ? "Borrador" : "✔ Publicado";
                        int bgColor = item.isDraft ? 0xFFFFF3E0 : 0xFFE8F5E9;
                        int textColor = item.isDraft ? 0xFFE65100 : 0xFF2E7D32;
                        holder.tvNewsStatusBadge.setText(statusText);
                        holder.tvNewsStatusBadge.setBackgroundTintList(ColorStateList.valueOf(bgColor));
                        holder.tvNewsStatusBadge.setTextColor(textColor);
                    }

                    if (holder.tvNewsCardTitle != null) {
                        holder.tvNewsCardTitle.setText(item.title != null && !item.title.trim().isEmpty() ? item.title : "Noticia sin título");
                    }

                    if (holder.tvNewsCardDesc != null) {
                        String desc = item.description != null && !item.description.trim().isEmpty() ? item.description : (item.shortDescription != null ? item.shortDescription : "");
                        holder.tvNewsCardDesc.setText(desc);
                    }

                    if (holder.tvNewsCardDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());
                        holder.tvNewsCardDate.setText("📅 " + sdf.format(new Date(item.updatedAt)));
                    }

                    if (holder.tvNewsLikesCount != null) {
                        holder.tvNewsLikesCount.setText("❤️ " + item.likesCount + " me gusta  •  🔁 " + item.sharesCount + " compartidos");
                    }

                    if (holder.tvNewsActionTarget != null) {
                        String actionText = item.subtitle != null && !item.subtitle.trim().isEmpty() ? item.subtitle : "Ver detalles completos";
                        holder.tvNewsActionTarget.setText("🔗 Acción: " + actionText + " (INFORMATIVO)");
                    }

                    List<String> photos = ContentItemAdapter.parsePhotosList(item);
                    View thumbContainer = holder.itemView.findViewById(R.id.layout_admin_news_thumb_container);
                    TextView tvBadge = holder.itemView.findViewById(R.id.tv_photos_count_badge);

                    if (thumbContainer != null) {
                        if (photos.isEmpty()) {
                            thumbContainer.setVisibility(View.GONE);
                        } else {
                            thumbContainer.setVisibility(View.VISIBLE);
                            if (holder.ivNewsThumb != null) {
                                AdminNewsEditActivity.loadNewsImageIntoView(AdminContentListActivity.this, holder.ivNewsThumb, photos.get(0));
                            }
                            if (tvBadge != null) {
                                if (photos.size() > 1) {
                                    tvBadge.setVisibility(View.VISIBLE);
                                    tvBadge.setText("📷 " + photos.size() + " fotos");
                                } else {
                                    tvBadge.setVisibility(View.GONE);
                                }
                            }
                        }
                    }

                    // Subir / Bajar
                    if (holder.btnMoveUp != null) {
                        holder.btnMoveUp.setOnClickListener(v -> moveItemOrder(item, -1));
                    }
                    if (holder.btnMoveDown != null) {
                        holder.btnMoveDown.setOnClickListener(v -> moveItemOrder(item, 1));
                    }

                    // ✏️ Editar (Abre AdminNewsEditActivity)
                    if (holder.btnEdit != null) {
                        holder.btnEdit.setOnClickListener(v -> {
                            Intent intent = new Intent(AdminContentListActivity.this, AdminNewsEditActivity.class);
                            intent.putExtra(AdminNewsEditActivity.EXTRA_ITEM_ID, item.id);
                            startActivity(intent);
                        });
                    }

                    // 📋 Duplicar
                    if (holder.btnDuplicate != null) {
                        holder.btnDuplicate.setOnClickListener(v -> duplicateItem(item));
                    }

                    // 👁️ Vista previa
                    if (holder.btnToggleVisibility != null) {
                        holder.btnToggleVisibility.setOnClickListener(v -> openCanvasPreview());
                    }

                    // 🗑️ Eliminar
                    if (holder.btnDelete != null) {
                        holder.btnDelete.setOnClickListener(v -> deleteItem(item));
                    }
                    return;
                }

                if (holder.tvType != null) {
                    holder.tvType.setText("[ TARJETA DE PRODUCTO ]");
                }

                holder.tvTitle.setText(item.title);
                holder.tvSubtitle.setText(item.shortDescription != null && !item.shortDescription.isEmpty() ? item.shortDescription : item.subtitle);

                if (holder.tvTargetPreview != null) {
                    String target = item.targetSectionId != null && !item.targetSectionId.isEmpty() ? item.targetSectionId : "Ninguno (Informativo)";
                    holder.tvTargetPreview.setText("Destino botón: " + target);
                }

                String statusText = "Publicado";
                int bgColor = 0xFFE8F5E9;
                int textColor = 0xFF2E7D32;

                if (item.isDraft) {
                    statusText = "Borrador";
                    bgColor = 0xFFFFF3E0;
                    textColor = 0xFFE65100;
                } else if (!item.isVisible) {
                    statusText = "Oculto";
                    bgColor = 0xFFF5F5F5;
                    textColor = 0xFF757575;
                }

                holder.tvStatus.setText(statusText);
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
                holder.tvStatus.setTextColor(textColor);

                if (item.accentColor != null && !item.accentColor.isEmpty()) {
                    try { holder.sideBorder.setBackgroundColor(Color.parseColor(item.accentColor)); } catch (Exception ignored) {}
                }

                int iconRes = getResources().getIdentifier(item.iconName, "drawable", getPackageName());
                if (iconRes != 0) {
                    holder.ivIcon.setImageResource(iconRes);
                    if (item.iconName != null && item.iconName.startsWith("ic_")) {
                        holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                    } else {
                        holder.ivIcon.setImageTintList(null);
                    }
                } else {
                    holder.ivIcon.setImageResource(R.drawable.ic_star);
                    holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                }

                // Subir / Bajar
                if (holder.btnMoveUp != null) {
                    holder.btnMoveUp.setOnClickListener(v -> moveItemOrder(item, -1));
                }
                if (holder.btnMoveDown != null) {
                    holder.btnMoveDown.setOnClickListener(v -> moveItemOrder(item, 1));
                }

                // Editar (Propiedades)
                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminContentEditActivity.class);
                    intent.putExtra(AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                    intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, sectionId);
                    startActivity(intent);
                });

                // Duplicar
                if (holder.btnDuplicate != null) {
                    holder.btnDuplicate.setOnClickListener(v -> duplicateItem(item));
                }

                // Ocultar / Mostrar
                if (holder.btnToggleVisibility != null) {
                    holder.btnToggleVisibility.setOnClickListener(v -> toggleVisibility(item));
                }

                // Eliminar
                holder.btnDelete.setOnClickListener(v -> deleteItem(item));

            } else if (obj instanceof AgenciaEntity) {
                AgenciaEntity a = (AgenciaEntity) obj;
                if (holder.tvType != null) holder.tvType.setText("[ AGENCIA / PUNTO ]");
                holder.tvTitle.setText(a.nombre);
                holder.tvSubtitle.setText(a.departamento + " - " + a.tipo);

                if (holder.tvTargetPreview != null) {
                    holder.tvTargetPreview.setText("Teléfono: " + (a.telefono != null && !a.telefono.isEmpty() ? a.telefono : "Sin número"));
                }

                String statusText = a.isVisible ? "Activo" : "Oculto";
                int bgColor = a.isVisible ? 0xFFE8F5E9 : 0xFFF5F5F5;
                int textColor = a.isVisible ? 0xFF2E7D32 : 0xFF757575;

                holder.tvStatus.setText(statusText);
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
                holder.tvStatus.setTextColor(textColor);

                if (a.colorHex != null && !a.colorHex.isEmpty()) {
                    try { holder.sideBorder.setBackgroundColor(Color.parseColor(a.colorHex)); } catch (Exception ignored) {}
                }

                // Subir / Bajar
                if (holder.btnMoveUp != null) {
                    holder.btnMoveUp.setOnClickListener(v -> moveAgenciaOrder(a, -1));
                }
                if (holder.btnMoveDown != null) {
                    holder.btnMoveDown.setOnClickListener(v -> moveAgenciaOrder(a, 1));
                }

                // 1. Editar
                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminAgenciaEditActivity.class);
                    intent.putExtra(AdminAgenciaEditActivity.EXTRA_AGENCIA_ID, a.id);
                    startActivity(intent);
                });

                // 2. Duplicar
                if (holder.btnDuplicate != null) {
                    holder.btnDuplicate.setOnClickListener(v -> duplicateAgencia(a));
                }

                // 3. Ocultar / Mostrar (Ojo)
                if (holder.btnToggleVisibility != null) {
                    holder.btnToggleVisibility.setOnClickListener(v -> toggleAgenciaVisibility(a));
                }

                // 4. Eliminar
                holder.btnDelete.setOnClickListener(v -> deleteAgencia(a));
            } else if (obj instanceof ContentBlockEntity) {
                ContentBlockEntity block = (ContentBlockEntity) obj;

                String typeTag = "[ BLOQUE / ELEMENTO ]";
                String blockType = block.type != null ? block.type.toUpperCase(Locale.getDefault()) : "TEXT";

                if ("BUTTON".equalsIgnoreCase(blockType)) {
                    typeTag = "[ BOTÓN / ACCIÓN CTA ]";
                } else if ("IMAGE".equalsIgnoreCase(blockType) || "BANNER".equalsIgnoreCase(blockType)) {
                    typeTag = "[ IMAGEN / LOGOTIPO ]";
                } else if ("ICON".equalsIgnoreCase(blockType)) {
                    typeTag = "[ ÍCONO DE GALERÍA ]";
                } else if ("CARD".equalsIgnoreCase(blockType)) {
                    typeTag = "[ TARJETA CONTENEDOR ]";
                } else if ("CONTAINER".equalsIgnoreCase(blockType)) {
                    typeTag = "[ SECCIÓN / CONTENEDOR ]";
                } else if ("LIST".equalsIgnoreCase(blockType)) {
                    typeTag = "[ LISTA DE BENEFICIOS ]";
                } else if ("PRODUCT_CARD".equalsIgnoreCase(blockType)) {
                    typeTag = "[ TARJETA DE PRODUCTO ]";
                } else if ("TEXT".equalsIgnoreCase(blockType)) {
                    typeTag = "[ TEXTO LIBRE / ENCABEZADO ]";
                }

                if (holder.tvType != null) {
                    holder.tvType.setText(typeTag);
                }

                // Título del bloque
                String displayTitle = "Elemento libre";
                if (block.title != null && !block.title.trim().isEmpty()) {
                    displayTitle = block.title;
                } else if (block.buttonText != null && !block.buttonText.trim().isEmpty()) {
                    displayTitle = "Botón: " + block.buttonText;
                } else if (block.content != null && !block.content.trim().isEmpty()) {
                    displayTitle = block.content;
                }
                holder.tvTitle.setText(displayTitle);

                // Subtítulo / Párrafo
                String displaySubtitle = block.content != null ? block.content : "";
                if (displaySubtitle.isEmpty() && block.buttonText != null) {
                    displaySubtitle = "Texto del botón: " + block.buttonText;
                }
                holder.tvSubtitle.setText(displaySubtitle);

                // Enlace / Destino
                if (holder.tvTargetPreview != null) {
                    String action = block.buttonAction != null && !block.buttonAction.trim().isEmpty() ? block.buttonAction : "Ninguno (Informativo)";
                    holder.tvTargetPreview.setText("Acción / Destino: " + action);
                }

                // Estado dinámico: Borrador, Publicado o Oculto
                String statusText = "Publicado";
                int bgColor = 0xFFE8F5E9;
                int textColor = 0xFF2E7D32;

                if (block.isDraft) {
                    statusText = "Borrador";
                    bgColor = 0xFFFFF3E0;
                    textColor = 0xFFE65100;
                } else if (!block.isVisible) {
                    statusText = "Oculto";
                    bgColor = 0xFFF5F5F5;
                    textColor = 0xFF757575;
                }

                holder.tvStatus.setText(statusText);
                holder.tvStatus.setBackgroundTintList(ColorStateList.valueOf(bgColor));
                holder.tvStatus.setTextColor(textColor);

                if (holder.sideBorder != null) {
                    holder.sideBorder.setBackgroundColor(Color.parseColor("#173789"));
                }

                // Ícono
                String media = block.mediaPath != null && !block.mediaPath.isEmpty() ? block.mediaPath : "editar";
                int iconRes = getResources().getIdentifier(media, "drawable", getPackageName());
                if (iconRes != 0) {
                    holder.ivIcon.setImageResource(iconRes);
                    if (media.startsWith("ic_")) {
                        holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                    } else {
                        holder.ivIcon.setImageTintList(null);
                    }
                } else {
                    holder.ivIcon.setImageResource(R.drawable.editar);
                    holder.ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                }

                // Subir / Bajar
                if (holder.btnMoveUp != null) {
                    holder.btnMoveUp.setOnClickListener(v -> moveBlockOrder(block, -1));
                }
                if (holder.btnMoveDown != null) {
                    holder.btnMoveDown.setOnClickListener(v -> moveBlockOrder(block, 1));
                }

                // Editar (Abre AdminBlockEditActivity)
                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminBlockEditActivity.class);
                    intent.putExtra(AdminBlockEditActivity.EXTRA_BLOCK_ID, block.id);
                    intent.putExtra(AdminBlockEditActivity.EXTRA_SECTION_ID, sectionId);
                    startActivity(intent);
                });

                // Duplicar
                if (holder.btnDuplicate != null) {
                    holder.btnDuplicate.setOnClickListener(v -> duplicateBlock(block));
                }

                // Vista Previa
                if (holder.btnToggleVisibility != null) {
                    holder.btnToggleVisibility.setOnClickListener(v -> openCanvasPreview());
                }

                // Eliminar
                holder.btnDelete.setOnClickListener(v -> deleteBlock(block));
            }
        }

        private void moveBlockOrder(ContentBlockEntity block, int delta) {
            block.displayOrder = Math.max(1, block.displayOrder + delta);
            new Thread(() -> {
                repository.insertBlock(block);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(AdminContentListActivity.this::loadData);
            }).start();
        }

        private void duplicateBlock(ContentBlockEntity block) {
            ContentBlockEntity clone = new ContentBlockEntity(
                    UUID.randomUUID().toString(),
                    block.contentItemId,
                    block.type,
                    block.content,
                    block.displayOrder + 1,
                    block.mediaPath,
                    (block.title != null ? block.title : "Bloque") + " (Copia)",
                    block.buttonText,
                    block.buttonAction,
                    block.sectionId,
                    block.backgroundColor,
                    block.textColor,
                    block.fontSize,
                    block.fontWeight,
                    block.alignment
            );

            new Thread(() -> {
                repository.insertBlock(clone);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(() -> {
                    Toast.makeText(AdminContentListActivity.this, "Bloque duplicado como borrador", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }).start();
        }

        private void deleteBlock(ContentBlockEntity block) {
            confirmWithPassword(() -> {
                new Thread(() -> {
                    repository.deleteBlockById(block.id);
                    getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_unpublished_changes", true)
                            .apply();
                    runOnUiThread(() -> {
                        allItems.remove(block);
                        applyFilters();
                        Toast.makeText(AdminContentListActivity.this, "Bloque eliminado del canvas", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            });
        }

        private void moveItemOrder(ContentItemEntity item, int delta) {
            item.displayOrder = Math.max(1, item.displayOrder + delta);
            new Thread(() -> {
                repository.insertItem(item);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(AdminContentListActivity.this::loadData);
            }).start();
        }

        private void duplicateItem(ContentItemEntity item) {
            ContentItemEntity clone = new ContentItemEntity(
                    UUID.randomUUID().toString(),
                    item.sectionId,
                    item.title + " (Copia)",
                    item.shortDescription,
                    item.description,
                    item.accentColor,
                    item.displayOrder + 1
            );
            clone.subtitle = item.subtitle;
            clone.iconName = item.iconName;
            clone.imagePath = item.imagePath;
            clone.targetSectionId = item.targetSectionId;
            clone.isDraft = true;
            clone.isVisible = true;

            new Thread(() -> {
                repository.insertItem(clone);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(() -> {
                    Toast.makeText(AdminContentListActivity.this, "Bloque duplicado como borrador", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }).start();
        }

        private void toggleAgenciaVisibility(AgenciaEntity a) {
            a.isVisible = !a.isVisible;
            new Thread(() -> {
                repository.insertAgencias(a);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(() -> {
                    Toast.makeText(AdminContentListActivity.this, a.isVisible ? "Agencia activada" : "Agencia ocultada", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }).start();
        }

        private void duplicateAgencia(AgenciaEntity a) {
            AgenciaEntity clone = new AgenciaEntity(
                    UUID.randomUUID().toString(),
                    a.nombre + " (Copia)",
                    a.departamento,
                    a.direccion,
                    a.telefono,
                    a.colorHex,
                    a.tipo,
                    a.mapUrl,
                    false,
                    System.currentTimeMillis()
            );

            new Thread(() -> {
                repository.insertAgencias(clone);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(() -> {
                    Toast.makeText(AdminContentListActivity.this, "Agencia duplicada correctamente", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }).start();
        }

        private void moveAgenciaOrder(AgenciaEntity a, int delta) {
            Toast.makeText(AdminContentListActivity.this, "Agencia " + a.nombre + " reordenada", Toast.LENGTH_SHORT).show();
            loadData();
        }

        private void toggleVisibility(ContentItemEntity item) {
            item.isVisible = !item.isVisible;
            new Thread(() -> {
                repository.insertItem(item);
                getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unpublished_changes", true)
                        .apply();
                runOnUiThread(() -> {
                    Toast.makeText(AdminContentListActivity.this, item.isVisible ? "Bloque visible" : "Bloque oculto", Toast.LENGTH_SHORT).show();
                    loadData();
                });
            }).start();
        }

        private void deleteItem(ContentItemEntity item) {
            confirmWithPassword(() -> {
                new Thread(() -> {
                    repository.deleteItemById(item.id);
                    getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_unpublished_changes", true)
                            .apply();
                    runOnUiThread(() -> { allItems.remove(item); applyFilters(); });
                }).start();
            });
        }

        private void deleteAgencia(AgenciaEntity a) {
            confirmWithPassword(() -> {
                new Thread(() -> {
                    repository.deleteAgencia(a);
                    getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("has_unpublished_changes", true)
                            .apply();
                    runOnUiThread(() -> { allItems.remove(a); applyFilters(); });
                }).start();
            });
        }

        private void confirmWithPassword(Runnable onConfirm) {
            DialogHelper.showPasswordConfirmationDialog(AdminContentListActivity.this, new AdminAuthManager(AdminContentListActivity.this), "Confirmar Eliminación", "ADVERTENCIA: Esta acción eliminará el bloque del canvas. Ingrese su clave para continuar:", onConfirm);
        }

        @Override
        public int getItemCount() { return paginatedList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvType, tvTitle, tvSubtitle, tvTargetPreview, tvStatus;
            ImageView ivIcon;
            View sideBorder;
            View btnEdit;
            ImageButton btnMoveUp, btnMoveDown, btnDuplicate, btnToggleVisibility, btnDelete;

            TextView tvNewsFeaturedTag, tvNewsStatusBadge, tvNewsCardTitle, tvNewsCardDesc, tvNewsCardDate, tvNewsLikesCount, tvNewsActionTarget;
            ImageView ivNewsThumb;

            ViewHolder(View v, int viewType) {
                super(v);
                if (viewType == TYPE_NEWS) {
                    tvNewsFeaturedTag = v.findViewById(R.id.tv_news_featured_tag);
                    tvNewsStatusBadge = v.findViewById(R.id.tv_news_status_badge);
                    tvNewsCardTitle = v.findViewById(R.id.tv_news_card_title);
                    tvNewsCardDesc = v.findViewById(R.id.tv_news_card_desc);
                    tvNewsCardDate = v.findViewById(R.id.tv_news_card_date);
                    tvNewsLikesCount = v.findViewById(R.id.tv_news_likes_count);
                    tvNewsActionTarget = v.findViewById(R.id.tv_news_action_target);
                    ivNewsThumb = v.findViewById(R.id.iv_news_thumb);

                    btnMoveUp = v.findViewById(R.id.btn_move_up);
                    btnMoveDown = v.findViewById(R.id.btn_move_down);
                    btnEdit = v.findViewById(R.id.btn_edit_item);
                    btnDuplicate = v.findViewById(R.id.btn_duplicate_item);
                    btnToggleVisibility = v.findViewById(R.id.btn_toggle_visibility);
                    btnDelete = v.findViewById(R.id.btn_delete_item);
                } else {
                    tvType = v.findViewById(R.id.tv_admin_item_type);
                    tvTitle = v.findViewById(R.id.tv_admin_item_title);
                    tvSubtitle = v.findViewById(R.id.tv_admin_item_subtitle);
                    tvTargetPreview = v.findViewById(R.id.tv_admin_item_target_preview);
                    tvStatus = v.findViewById(R.id.tv_admin_item_status);
                    ivIcon = v.findViewById(R.id.iv_admin_item_icon);
                    sideBorder = v.findViewById(R.id.side_border_admin);

                    btnMoveUp = v.findViewById(R.id.btn_move_up);
                    btnMoveDown = v.findViewById(R.id.btn_move_down);
                    btnEdit = v.findViewById(R.id.btn_edit_item);
                    btnDuplicate = v.findViewById(R.id.btn_duplicate_item);
                    btnToggleVisibility = v.findViewById(R.id.btn_toggle_visibility);
                    btnDelete = v.findViewById(R.id.btn_delete_item);
                }
            }
        }
    }
}
