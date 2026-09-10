package com.example.coluainformativa.ui.content;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ElementTypeAdapter extends RecyclerView.Adapter<ElementTypeAdapter.ViewHolder> {

    public static class ElementTypeItem {
        public String key;
        public String title;
        public String description;
        public int iconRes;
        public String iconBgColor;
        public String iconTint;
        public String category; // "Básicos", "Multimedia", "Estructura", "Financiero"
        public boolean isPro;

        public ElementTypeItem(String key, String title, String description, int iconRes, String iconBgColor, String iconTint, String category, boolean isPro) {
            this.key = key;
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.iconBgColor = iconBgColor;
            this.iconTint = iconTint;
            this.category = category;
            this.isPro = isPro;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(ElementTypeItem item);
    }

    private final List<ElementTypeItem> allItems;
    private final List<ElementTypeItem> filteredItems;
    private final OnItemClickListener listener;

    private String currentSearchQuery = "";
    private String currentCategoryFilter = "Todos";

    public ElementTypeAdapter(List<ElementTypeItem> items, OnItemClickListener listener) {
        this.allItems = new ArrayList<>(items);
        this.filteredItems = new ArrayList<>(items);
        this.listener = listener;
    }

    public void filter(String query, String category) {
        this.currentSearchQuery = query != null ? query.trim().toLowerCase(Locale.getDefault()) : "";
        if (category != null) this.currentCategoryFilter = category;

        filteredItems.clear();
        for (ElementTypeItem item : allItems) {
            boolean matchesQuery = currentSearchQuery.isEmpty()
                    || item.title.toLowerCase(Locale.getDefault()).contains(currentSearchQuery)
                    || item.description.toLowerCase(Locale.getDefault()).contains(currentSearchQuery);

            boolean matchesCategory = "Todos".equalsIgnoreCase(currentCategoryFilter)
                    || item.category.equalsIgnoreCase(currentCategoryFilter);

            if (matchesQuery && matchesCategory) {
                filteredItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_element_type_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ElementTypeItem item = filteredItems.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout layoutIconBg;
        ImageView ivIcon;
        TextView tvTitle, tvBadgePro, tvDescription;

        ViewHolder(View v) {
            super(v);
            layoutIconBg = v.findViewById(R.id.layout_icon_bg);
            ivIcon = v.findViewById(R.id.iv_element_icon);
            tvTitle = v.findViewById(R.id.tv_element_title);
            tvBadgePro = v.findViewById(R.id.tv_badge_pro);
            tvDescription = v.findViewById(R.id.tv_element_description);
        }

        void bind(ElementTypeItem item, OnItemClickListener listener) {
            tvTitle.setText(item.title);
            tvDescription.setText(item.description);
            tvBadgePro.setVisibility(item.isPro ? View.VISIBLE : View.GONE);

            if (ivIcon != null) {
                ivIcon.setImageResource(item.iconRes);
                try {
                    ivIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor(item.iconTint)));
                } catch (Exception e) {
                    ivIcon.setImageTintList(null);
                }
            }

            if (layoutIconBg != null && item.iconBgColor != null) {
                try {
                    layoutIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(item.iconBgColor)));
                } catch (Exception ignored) {}
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }
}
