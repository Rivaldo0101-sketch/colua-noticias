package com.example.coluainformativa.ui.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.AdminContentEditActivity;
import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentItemEntity;

import java.util.List;

public class HomeItemAdapter extends RecyclerView.Adapter<HomeItemAdapter.ViewHolder> {

    private final List<ContentItemEntity> items;
    private final OnItemClickListener listener;
    private boolean isEditMode = false;

    public interface OnItemClickListener {
        void onItemClick(ContentItemEntity item);
    }

    public HomeItemAdapter(List<ContentItemEntity> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ContentItemEntity item = items.get(position);
        holder.bind(item, listener, isEditMode);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View sideBorder;
        private final View iconBackground;
        private final ImageView serviceIcon;
        private final TextView serviceButton;
        private final TextView serviceDescription;
        private final TextView serviceFooter;
        private final View viewDivider;
        private final View btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            sideBorder = itemView.findViewById(R.id.side_border);
            iconBackground = itemView.findViewById(R.id.icon_background);
            serviceIcon = itemView.findViewById(R.id.service_icon);
            serviceButton = itemView.findViewById(R.id.service_button);
            serviceDescription = itemView.findViewById(R.id.service_description);
            serviceFooter = itemView.findViewById(R.id.service_footer);
            viewDivider = itemView.findViewById(R.id.view_divider);
            btnEdit = itemView.findViewById(R.id.btn_edit_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            int color = Color.parseColor("#173789"); // Default Navy
            try {
                if (item.accentColor != null && !item.accentColor.trim().isEmpty()) {
                    color = Color.parseColor(item.accentColor.trim());
                }
            } catch (Exception ignored) {}

            sideBorder.setBackgroundColor(color);

            // Tinte pastel suave (15% de opacidad del color institucional)
            int softBg = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color));
            iconBackground.setBackgroundTintList(ColorStateList.valueOf(softBg));

            // Cargar icono según iconName
            if (item.iconName != null && !item.iconName.trim().isEmpty()) {
                String iconKey = item.iconName.trim();
                int iconRes = itemView.getContext().getResources().getIdentifier(iconKey, "drawable", itemView.getContext().getPackageName());
                if (iconRes != 0) {
                    serviceIcon.setImageResource(iconRes);
                    if (iconKey.startsWith("ic_")) {
                        serviceIcon.setImageTintList(ColorStateList.valueOf(color));
                    } else {
                        serviceIcon.setImageTintList(null);
                    }
                } else {
                    serviceIcon.setImageResource(R.drawable.ic_star);
                    serviceIcon.setImageTintList(ColorStateList.valueOf(color));
                }
            } else {
                serviceIcon.setImageResource(R.drawable.ic_star);
                serviceIcon.setImageTintList(ColorStateList.valueOf(color));
            }

            // Título / Cabecera (ej: ¡Ahorro!, ¡Crédito!)
            String headerText = item.subtitle != null && !item.subtitle.trim().isEmpty() ? item.subtitle : item.title;
            serviceButton.setText(headerText);

            // Descripción de secciones (ej: Cuentas de Ahorro, Ahorro Infantil y Juvenil)
            serviceDescription.setText(item.title != null ? item.title : "");

            // Pie / Frase de Valor (ej: Seguridad para tu futuro)
            if (item.shortDescription == null || item.shortDescription.trim().isEmpty()) {
                serviceFooter.setVisibility(View.GONE);
                if (viewDivider != null) viewDivider.setVisibility(View.GONE);
            } else {
                serviceFooter.setVisibility(View.VISIBLE);
                if (viewDivider != null) viewDivider.setVisibility(View.VISIBLE);
                serviceFooter.setText(item.shortDescription.trim());
            }

            btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), AdminContentEditActivity.class);
                intent.putExtra(AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
