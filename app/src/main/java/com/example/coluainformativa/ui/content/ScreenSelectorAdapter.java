package com.example.coluainformativa.ui.content;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScreenSelectorAdapter extends RecyclerView.Adapter<ScreenSelectorAdapter.ViewHolder> {

    public static class ScreenItem {
        public String sectionId;
        public String title;
        public String slug;
        public int iconRes;

        public ScreenItem(String sectionId, String title, String slug, int iconRes) {
            this.sectionId = sectionId;
            this.title = title;
            this.slug = slug;
            this.iconRes = iconRes;
        }
    }

    public interface OnScreenSelectedListener {
        void onScreenSelected(ScreenItem item);
    }

    private final List<ScreenItem> allItems;
    private final List<ScreenItem> filteredItems;
    private final OnScreenSelectedListener listener;

    public ScreenSelectorAdapter(List<ScreenItem> items, OnScreenSelectedListener listener) {
        this.allItems = new ArrayList<>(items);
        this.filteredItems = new ArrayList<>(items);
        this.listener = listener;
    }

    public void filter(String query) {
        String q = query != null ? query.trim().toLowerCase(Locale.getDefault()) : "";
        filteredItems.clear();
        for (ScreenItem item : allItems) {
            if (q.isEmpty()
                    || item.title.toLowerCase(Locale.getDefault()).contains(q)
                    || item.slug.toLowerCase(Locale.getDefault()).contains(q)) {
                filteredItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screen_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScreenItem item = filteredItems.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvSlug;

        ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_screen_icon);
            tvTitle = v.findViewById(R.id.tv_screen_title);
            tvSlug = v.findViewById(R.id.tv_screen_slug);
        }

        void bind(ScreenItem item, OnScreenSelectedListener listener) {
            if (tvTitle != null) tvTitle.setText(item.title);
            if (tvSlug != null) tvSlug.setText("(/" + item.slug + ")");

            if (ivIcon != null) {
                ivIcon.setImageResource(item.iconRes != 0 ? item.iconRes : R.drawable.ic_home);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onScreenSelected(item);
            });
        }
    }
}
