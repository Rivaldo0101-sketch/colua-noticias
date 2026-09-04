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

import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentItemEntity;
import com.google.android.material.button.MaterialButton;

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
        private final MaterialButton serviceButton;
        private final TextView serviceDescription;
        private final TextView serviceFooter;
        private final View btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            sideBorder = itemView.findViewById(R.id.side_border);
            iconBackground = itemView.findViewById(R.id.icon_background);
            serviceIcon = itemView.findViewById(R.id.service_icon);
            serviceButton = itemView.findViewById(R.id.service_button);
            serviceDescription = itemView.findViewById(R.id.service_description);
            serviceFooter = itemView.findViewById(R.id.service_footer);
            btnEdit = itemView.findViewById(R.id.btn_edit_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            int color = Color.GRAY;
            try {
                if (item.accentColor != null && !item.accentColor.isEmpty()) {
                    color = Color.parseColor(item.accentColor);
                }
            } catch (Exception e) {
                // Use default gray
            }

            sideBorder.setBackgroundColor(color);
            iconBackground.setBackgroundTintList(ColorStateList.valueOf(color));
            
            // Cargar icono según iconName
            if (item.iconName != null) {
                int iconRes = itemView.getContext().getResources().getIdentifier(item.iconName, "drawable", itemView.getContext().getPackageName());
                if (iconRes != 0) {
                    serviceIcon.setImageResource(iconRes);
                }
            }

            serviceButton.setText(item.subtitle); // Usamos subtitle para la etiqueta tipo cápsula
            serviceDescription.setText(item.title); // Usamos title para la descripción principal
            serviceFooter.setText(item.shortDescription); // Usamos shortDescription para el texto auxiliar

            if (item.shortDescription == null || item.shortDescription.isEmpty()) {
                serviceFooter.setVisibility(View.GONE);
            } else {
                serviceFooter.setVisibility(View.VISIBLE);
            }

            btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), com.example.coluainformativa.AdminContentEditActivity.class);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(com.example.coluainformativa.AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
