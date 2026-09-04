package com.example.coluainformativa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.database.AgenciaEntity;
import com.example.coluainformativa.database.AppDatabase;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.database.SectionEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminContentListActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION_ID = "extra_section_id";
    public static final String EXTRA_MANAGE_AGENCIAS = "extra_manage_agencias";

    private ColuaRepository repository;
    private String sectionId;
    private boolean manageAgencias = false;
    private SectionEntity section;
    private List<Object> allItems = new ArrayList<>();
    private List<Object> filteredList = new ArrayList<>();
    private List<Object> paginatedList = new ArrayList<>();
    private ContentAdapter adapter;
    
    private String searchQuery = "";
    private int currentPage = 1;
    private int itemsPerPage = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_content_list);

        repository = new ColuaRepository(this);
        sectionId = getIntent().getStringExtra(EXTRA_SECTION_ID);
        manageAgencias = getIntent().getBooleanExtra(EXTRA_MANAGE_AGENCIAS, false);

        if (sectionId == null && !manageAgencias) {
            finish();
            return;
        }

        findViewById(R.id.btn_back_content_list).setOnClickListener(v -> finish());

        findViewById(R.id.btn_add_item).setOnClickListener(v -> {
            if (manageAgencias) {
                Intent intent = new Intent(this, AdminAgenciaEditActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, AdminContentEditActivity.class);
                intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, sectionId);
                startActivity(intent);
            }
        });

        View btnEditProps = findViewById(R.id.btn_edit_section_properties);
        if (btnEditProps != null) {
            btnEditProps.setOnClickListener(v -> {
                Intent intent = new Intent(this, AdminSectionEditActivity.class);
                intent.putExtra(AdminSectionEditActivity.EXTRA_SECTION_ID, sectionId);
                startActivity(intent);
            });
        }

        setupSearch();
        setupFilters();

        RecyclerView rv = findViewById(R.id.rv_admin_items);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContentAdapter();
        rv.setAdapter(adapter);

        loadData();
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search_admin);
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
        cg.setOnCheckedStateChangeListener((group, checkedIds) -> {
            // Lógica de filtrado por categoría si fuera necesario
            applyFilters();
        });
    }

    private void loadData() {
        findViewById(R.id.pb_admin_loading).setVisibility(View.VISIBLE);
        findViewById(R.id.rv_admin_items).setVisibility(View.GONE);
        findViewById(R.id.tv_admin_empty).setVisibility(View.GONE);

        new Thread(() -> {
            try {
                if (manageAgencias) {
                    allItems.clear();
                    allItems.addAll(repository.getAllAgencias());
                    runOnUiThread(() -> {
                        ((TextView) findViewById(R.id.tv_admin_section_title)).setText("Gestionar Agencias");
                        applyFilters();
                    });
                } else {
                    section = AppDatabase.getDatabase(this).sectionDao().getSectionById(sectionId);
                    List<ContentItemEntity> items = repository.getItemsBySection(sectionId);
                    List<com.example.coluainformativa.database.ContentBlockEntity> blocks = repository.getBlocksBySection(sectionId);
                    
                    runOnUiThread(() -> {
                        if (section != null) {
                            ((TextView) findViewById(R.id.tv_admin_section_title)).setText("Gestionar " + section.title);
                        }
                        allItems.clear();
                        allItems.addAll(items);
                        allItems.addAll(blocks);
                        applyFilters();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(() -> findViewById(R.id.pb_admin_loading).setVisibility(View.GONE));
            }
        }).start();
    }

    private void applyFilters() {
        if (allItems == null) return;
        
        filteredList = allItems.stream().filter(obj -> {
            if (obj instanceof ContentItemEntity) {
                ContentItemEntity item = (ContentItemEntity) obj;
                return searchQuery.isEmpty() || item.title.toLowerCase().contains(searchQuery);
            } else if (obj instanceof AgenciaEntity) {
                AgenciaEntity a = (AgenciaEntity) obj;
                return searchQuery.isEmpty() || a.nombre.toLowerCase().contains(searchQuery);
            }
            return true;
        }).collect(Collectors.toList());

        runOnUiThread(() -> {
            boolean empty = filteredList.isEmpty();
            findViewById(R.id.tv_admin_empty).setVisibility(empty ? View.VISIBLE : View.GONE);
            findViewById(R.id.rv_admin_items).setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        currentPage = 1;
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = filteredList.size();
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        if (totalPages <= 1) {
            findViewById(R.id.layout_pagination).setVisibility(View.GONE);
            paginatedList.clear();
            paginatedList.addAll(filteredList);
        } else {
            findViewById(R.id.layout_pagination).setVisibility(View.VISIBLE);
            int start = (currentPage - 1) * itemsPerPage;
            int end = Math.min(start + itemsPerPage, totalItems);
            
            paginatedList.clear();
            paginatedList.addAll(filteredList.subList(start, end));
            setupPaginationBar(totalPages);
        }
        adapter.notifyDataSetChanged();
    }

    private void setupPaginationBar(int totalPages) {
        android.widget.LinearLayout layout = findViewById(R.id.layout_page_numbers);
        layout.removeAllViews();
        
        TextView tvItemsPerPage = findViewById(R.id.tv_items_per_page);
        if (tvItemsPerPage != null) {
            tvItemsPerPage.setText(itemsPerPage + " / page");
        }

        List<Integer> pagesToShow = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) pagesToShow.add(i);
        } else {
            pagesToShow.add(1);
            if (currentPage > 3) pagesToShow.add(-1); 

            int start = Math.max(2, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);

            if (currentPage <= 3) end = 4;
            if (currentPage >= totalPages - 2) start = totalPages - 3;

            for (int i = start; i <= end; i++) {
                if (!pagesToShow.contains(i)) pagesToShow.add(i);
            }

            if (currentPage < totalPages - 2) pagesToShow.add(-1);
            if (!pagesToShow.contains(totalPages)) pagesToShow.add(totalPages);
        }

        for (Integer p : pagesToShow) {
            if (p == -1) {
                TextView tv = new TextView(this);
                tv.setText("...");
                tv.setPadding(16, 0, 16, 0);
                tv.setTextColor(getResources().getColor(R.color.text_grey));
                layout.addView(tv);
                continue;
            }

            final int page = p;
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(p));
            tv.setPadding(12, 8, 12, 8);
            tv.setTextSize(14);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setMinWidth(60);
            
            if (p == currentPage) {
                tv.setTextColor(getResources().getColor(R.color.white));
                tv.setBackgroundResource(R.drawable.circle_background);
                tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.colua_navy)));
            } else {
                tv.setTextColor(getResources().getColor(R.color.black));
                tv.setBackground(null);
            }

            tv.setOnClickListener(v -> {
                currentPage = page;
                updatePagination();
            });
            layout.addView(tv);
        }

        findViewById(R.id.btn_pagination_prev).setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePagination();
            }
        });

        findViewById(R.id.btn_pagination_next).setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                updatePagination();
            }
        });
    }

    class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_content, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object obj = paginatedList.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            if (obj instanceof ContentItemEntity) {
                ContentItemEntity item = (ContentItemEntity) obj;
                holder.tvTitle.setText(item.title);
                holder.tvSubtitle.setText(item.shortDescription);
                holder.tvDate.setText("Última edición: " + sdf.format(new Date(item.updatedAt)));
                
                String statusText = "Activo";
                int bgColor = 0xFFE8F5E9;
                int textColor = 0xFF2E7D32;
                
                if (item.isDraft) {
                    statusText = "Borrador";
                    bgColor = 0xFFFFF3E0;
                    textColor = 0xFFE65100;
                } else if (item.publicationDate > System.currentTimeMillis()) {
                    statusText = "Programado";
                    bgColor = 0xFFE3F2FD;
                    textColor = 0xFF1565C0;
                } else if (!item.isVisible) {
                    statusText = "Oculto";
                    bgColor = 0xFFF5F5F5;
                    textColor = 0xFF757575;
                }
                
                holder.tvStatus.setText(statusText);
                holder.tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
                holder.tvStatus.setTextColor(textColor);

                if (item.accentColor != null) {
                    try { holder.sideBorder.setBackgroundColor(android.graphics.Color.parseColor(item.accentColor)); } catch (Exception ignored) {}
                }

                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminContentEditActivity.class);
                    intent.putExtra(AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                    intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, sectionId);
                    startActivity(intent);
                });

                holder.btnOrder.setOnClickListener(v -> showOrderDialog(item));
                holder.btnDelete.setOnClickListener(v -> deleteItem(item));

            } else if (obj instanceof AgenciaEntity) {
                AgenciaEntity a = (AgenciaEntity) obj;
                holder.tvTitle.setText(a.nombre);
                holder.tvSubtitle.setText(a.departamento + " - " + a.tipo);
                holder.tvDate.setText("Última edición: " + sdf.format(new Date(a.updatedAt)));
                holder.tvStatus.setText(a.isVisible ? "Activo" : "Borrador");
                
                if (a.colorHex != null) {
                    try { holder.sideBorder.setBackgroundColor(android.graphics.Color.parseColor(a.colorHex)); } catch (Exception ignored) {}
                }

                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminAgenciaEditActivity.class);
                    intent.putExtra(AdminAgenciaEditActivity.EXTRA_AGENCIA_ID, a.id);
                    startActivity(intent);
                });
                
                holder.btnDelete.setOnClickListener(v -> deleteAgencia(a));
            } else if (obj instanceof com.example.coluainformativa.database.ContentBlockEntity) {
                com.example.coluainformativa.database.ContentBlockEntity block = (com.example.coluainformativa.database.ContentBlockEntity) obj;
                holder.tvTitle.setText("[ESTRUCTURAL] " + (block.title != null ? block.title : block.type));
                holder.tvSubtitle.setText(block.content);
                holder.btnDelete.setVisibility(View.GONE);

                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminContentListActivity.this, AdminBlockEditActivity.class);
                    intent.putExtra(AdminBlockEditActivity.EXTRA_BLOCK_ID, block.id);
                    startActivity(intent);
                });
            }
        }

        private void showOrderDialog(ContentItemEntity item) {
            EditText et = new EditText(AdminContentListActivity.this);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setText(String.valueOf(item.displayOrder));
            et.setHint("Prioridad (ej: 1, 2, 3...)");

            new androidx.appcompat.app.AlertDialog.Builder(AdminContentListActivity.this)
                    .setTitle("Reordenar Elemento")
                    .setMessage("Ingrese un número para cambiar la posición:")
                    .setView(et)
                    .setPositiveButton("CAMBIAR", (d, w) -> {
                        try {
                            item.displayOrder = Integer.parseInt(et.getText().toString());
                            new Thread(() -> {
                                repository.insertItem(item);
                                runOnUiThread(AdminContentListActivity.this::loadData);
                            }).start();
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton("CANCELAR", null)
                    .show();
        }

        private void deleteItem(ContentItemEntity item) {
            confirmWithPassword(() -> {
                new Thread(() -> {
                    repository.deleteItemById(item.id);
                    runOnUiThread(() -> { allItems.remove(item); applyFilters(); });
                }).start();
            });
        }

        private void deleteAgencia(AgenciaEntity a) {
            confirmWithPassword(() -> {
                new Thread(() -> {
                    repository.deleteAgencia(a);
                    runOnUiThread(() -> { allItems.remove(a); applyFilters(); });
                }).start();
            });
        }

        private void confirmWithPassword(Runnable onConfirm) {
            android.widget.EditText et = new android.widget.EditText(AdminContentListActivity.this);
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            et.setHint("Clave de Administrador");
            
            android.widget.FrameLayout container = new android.widget.FrameLayout(AdminContentListActivity.this);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = params.rightMargin = 60;
            et.setLayoutParams(params);
            container.addView(et);

            new androidx.appcompat.app.AlertDialog.Builder(AdminContentListActivity.this)
                    .setTitle("Confirmar Eliminación")
                    .setMessage("ADVERTENCIA: Esta acción es irreversible. Ingrese su clave para continuar:")
                    .setView(container)
                    .setPositiveButton("ELIMINAR", (d, w) -> {
                        if (repository.checkAdminPassword(et.getText().toString())) {
                            onConfirm.run();
                        } else {
                            Toast.makeText(AdminContentListActivity.this, "Clave incorrecta", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("CANCELAR", null)
                    .show();
        }

        @Override
        public int getItemCount() { return paginatedList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle, tvDate, tvStatus;
            View sideBorder;
            Button btnEdit, btnOrder;
            ImageButton btnDelete;

            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_admin_item_title);
                tvSubtitle = v.findViewById(R.id.tv_admin_item_subtitle);
                tvDate = v.findViewById(R.id.tv_admin_item_date);
                tvStatus = v.findViewById(R.id.tv_admin_item_status);
                sideBorder = v.findViewById(R.id.side_border_admin);
                btnEdit = v.findViewById(R.id.btn_edit_item);
                btnOrder = v.findViewById(R.id.btn_order_item);
                btnDelete = v.findViewById(R.id.btn_delete_item);
            }
        }
    }
}
