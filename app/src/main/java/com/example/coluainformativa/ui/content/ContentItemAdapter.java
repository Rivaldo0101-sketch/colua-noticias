package com.example.coluainformativa.ui.content;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentItemEntity;

import java.util.List;

public class ContentItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ACCOUNT = 0;
    private static final int TYPE_BENEFIT = 1;
    private static final int TYPE_NEWS = 2;

    private final List<ContentItemEntity> items;
    private final OnItemClickListener listener;
    private boolean isEditMode = false;

    public interface OnItemClickListener {
        void onItemClick(ContentItemEntity item);
    }

    public ContentItemAdapter(List<ContentItemEntity> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ContentItemEntity item = items.get(position);
        if ("sec_noticias".equals(item.sectionId)) {
            return TYPE_NEWS;
        }
        if ("BENEFIT".equals(item.shortDescription)) {
            return TYPE_BENEFIT;
        }
        return TYPE_ACCOUNT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_NEWS) {
            View v = inflater.inflate(R.layout.item_news_card, parent, false);
            return new NewsViewHolder(v);
        } else if (viewType == TYPE_BENEFIT) {
            View v = inflater.inflate(R.layout.item_simple_info_card, parent, false);
            return new BenefitViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_account_card, parent, false);
            return new AccountViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ContentItemEntity item = items.get(position);
        if (holder instanceof AccountViewHolder) {
            ((AccountViewHolder) holder).bind(item, listener, isEditMode);
        } else if (holder instanceof BenefitViewHolder) {
            ((BenefitViewHolder) holder).bind(item, listener, isEditMode);
        } else if (holder instanceof NewsViewHolder) {
            ((NewsViewHolder) holder).bind(item, listener, isEditMode);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvTitle, tvDesc;
        private final LinearLayout layoutFeatures;
        private final Button btnMore;
        private final View btnEdit;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_account_icon);
            tvTitle = itemView.findViewById(R.id.tv_account_title);
            tvDesc = itemView.findViewById(R.id.tv_account_description);
            layoutFeatures = itemView.findViewById(R.id.layout_features);
            btnMore = itemView.findViewById(R.id.btn_conocer_mas);
            btnEdit = itemView.findViewById(R.id.btn_edit_content_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            tvTitle.setText(item.title);
            tvDesc.setText(item.shortDescription);

            btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), com.example.coluainformativa.AdminContentEditActivity.class);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });
            
            // Render features (bullets)
            layoutFeatures.removeAllViews();
            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                String[] features = item.subtitle.split("\n");
                for (String feature : features) {
                    View featureView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.item_account_feature, layoutFeatures, false);
                    TextView tvFeature = featureView.findViewById(R.id.tv_feature_text);
                    tvFeature.setText(feature);
                    layoutFeatures.addView(featureView);
                }
            }

            if (item.accentColor != null) {
                try {
                    int color = Color.parseColor(item.accentColor);
                    ivIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    tvTitle.setTextColor(color);
                } catch (Exception ignored) {}
            }
            
            // Cambiar texto del botón si es la sección de créditos
            if ("sec_creditos".equals(item.sectionId)) {
                btnMore.setText("Solicitar Información");
            } else {
                btnMore.setText("Conocer Más");
            }

            // Soporte para títulos como imagen (Si iconName empieza con 'logo_')
            if (item.iconName != null && !item.iconName.isEmpty()) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        item.iconName, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    ivIcon.setImageResource(resId);
                    
                    if (item.iconName.startsWith("logo_")) {
                        ivIcon.setBackground(null);
                        ivIcon.setPadding(0, 0, 0, 0);
                        tvTitle.setVisibility(View.GONE);
                    } else {
                        ivIcon.setBackgroundResource(R.drawable.circle_background);
                        int p = (int) (10 * itemView.getContext().getResources().getDisplayMetrics().density);
                        ivIcon.setPadding(p, p, p, p);
                        tvTitle.setVisibility(View.VISIBLE);
                    }
                }
            }

            btnMore.setOnClickListener(v -> showDetailsDialog(item));
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        private void showDetailsDialog(ContentItemEntity item) {
            View dialogView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_account_details, null);
            TextView title = dialogView.findViewById(R.id.dialog_title);
            TextView content = dialogView.findViewById(R.id.dialog_content);
            Button btnCall = dialogView.findViewById(R.id.btn_dialog_call);

            title.setText(item.title);
            content.setText(item.description);

            AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            btnCall.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:77957795"));
                    itemView.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(itemView.getContext(), "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show();
                }
            });

            View faxHint = dialogView.findViewById(R.id.tv_fax_hint);
            if (faxHint != null) faxHint.setVisibility(View.VISIBLE);

            dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());

            dialog.show();
        }
    }

    static class BenefitViewHolder extends RecyclerView.ViewHolder {
        private final View sideBorder;
        private final ImageView ivIcon;
        private final TextView tvTitle, tvDesc;
        private final View btnEdit;

        public BenefitViewHolder(@NonNull View itemView) {
            super(itemView);
            sideBorder = itemView.findViewById(R.id.side_border_benefit);
            ivIcon = itemView.findViewById(R.id.iv_benefit_icon);
            tvTitle = itemView.findViewById(R.id.tv_benefit_title);
            tvDesc = itemView.findViewById(R.id.tv_benefit_desc);
            btnEdit = itemView.findViewById(R.id.btn_edit_content_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            tvTitle.setText(item.title);
            tvDesc.setText(item.subtitle); // Usamos subtitle para la descripción en beneficios

            btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), com.example.coluainformativa.AdminContentEditActivity.class);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });

            if (item.accentColor != null) {
                try {
                    int color = Color.parseColor(item.accentColor);
                    sideBorder.setBackgroundColor(color);
                    ivIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    // Tint icon background with lighter version or keep original
                } catch (Exception ignored) {}
            }

            // Soporte para títulos como imagen (Si iconName empieza con 'logo_')
            if (item.iconName != null && !item.iconName.isEmpty()) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        item.iconName, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    ivIcon.setImageResource(resId);
                    
                    if (item.iconName.startsWith("logo_")) {
                        // Si el icono es el logo que ya trae el nombre (ej: logo_ahorro_programado)
                        // quitamos el fondo circular y ocultamos el texto del título
                        ivIcon.setBackground(null);
                        ivIcon.setPadding(0, 0, 0, 0);
                        tvTitle.setVisibility(View.GONE);
                    } else {
                        // Restaurar estado normal para iconos estándar
                        ivIcon.setBackgroundResource(R.drawable.circle_background);
                        int p = (int) (10 * itemView.getContext().getResources().getDisplayMetrics().density);
                        ivIcon.setPadding(p, p, p, p);
                        tvTitle.setVisibility(View.VISIBLE);
                    }
                }
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImage;
        private final TextView tvCategory, tvDate, tvTitle, tvSnippet, btnReadMore;
        private final View sideBorder;
        private final View btnEdit;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_news_image);
            tvCategory = itemView.findViewById(R.id.tv_news_category);
            tvDate = itemView.findViewById(R.id.tv_news_date);
            tvTitle = itemView.findViewById(R.id.tv_news_title);
            tvSnippet = itemView.findViewById(R.id.tv_news_snippet);
            btnReadMore = itemView.findViewById(R.id.btn_read_more);
            sideBorder = itemView.findViewById(R.id.side_border_news);
            btnEdit = itemView.findViewById(R.id.btn_edit_content_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            tvTitle.setText(item.title);
            tvSnippet.setText(item.shortDescription);

            btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), com.example.coluainformativa.AdminContentEditActivity.class);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });
            tvCategory.setText(item.subtitle != null ? item.subtitle : "Institucional");
            
            // Formatear fecha
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMMM, yyyy", java.util.Locale.getDefault());
            tvDate.setText(sdf.format(new java.util.Date(item.updatedAt)));

            if (item.accentColor != null) {
                try {
                    int color = Color.parseColor(item.accentColor);
                    sideBorder.setBackgroundColor(color);
                    tvCategory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                } catch (Exception ignored) {}
            }

            if (item.imagePath != null && !item.imagePath.isEmpty()) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        item.imagePath, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) ivImage.setImageResource(resId);
            }

            View.OnClickListener click = v -> {
                // Mostrar detalle de noticia
                showNewsDialog(item);
            };
            
            btnReadMore.setOnClickListener(click);
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        private void showNewsDialog(ContentItemEntity item) {
            View dialogView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_account_details, null);
            TextView title = dialogView.findViewById(R.id.dialog_title);
            TextView content = dialogView.findViewById(R.id.dialog_content);
            Button btnCall = dialogView.findViewById(R.id.btn_dialog_call);

            title.setText(item.title);
            content.setText(item.description != null ? item.description : item.shortDescription);
            btnCall.setText("Llamar para más información");

            AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            btnCall.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:77957795"));
                    itemView.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(itemView.getContext(), "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show();
                }
            });

            dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }
    }
}
