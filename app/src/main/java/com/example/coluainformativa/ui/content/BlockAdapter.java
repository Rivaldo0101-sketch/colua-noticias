package com.example.coluainformativa.ui.content;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentBlockEntity;

import java.util.List;

public class BlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_IMAGE = 1;
    private static final int TYPE_HERO = 2;
    private static final int TYPE_BUTTON = 3;
    private static final int TYPE_CONTACT = 4;
    private static final int TYPE_SAVINGS_HERO = 5;

    private final List<ContentBlockEntity> blocks;

    public BlockAdapter(List<ContentBlockEntity> blocks) {
        this.blocks = blocks;
    }

    @Override
    public int getItemViewType(int position) {
        String type = blocks.get(position).type;
        if (type == null) return TYPE_TEXT;
        switch (type) {
            case "IMAGE": return TYPE_IMAGE;
            case "HERO": return TYPE_HERO;
            case "BUTTON": return TYPE_BUTTON;
            case "CONTACT": return TYPE_CONTACT;
            case "SAVINGS_HERO": return TYPE_SAVINGS_HERO;
            default: return TYPE_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_IMAGE:
                return new ImageViewHolder(inflater.inflate(R.layout.block_image, parent, false));
            case TYPE_HERO:
                return new HeroViewHolder(inflater.inflate(R.layout.block_hero, parent, false));
            case TYPE_BUTTON:
                return new ButtonViewHolder(inflater.inflate(R.layout.block_button, parent, false));
            case TYPE_CONTACT:
                return new ContactViewHolder(inflater.inflate(R.layout.block_button, parent, false));
            case TYPE_SAVINGS_HERO:
                return new SavingsHeroViewHolder(inflater.inflate(R.layout.block_savings_header, parent, false));
            default:
                return new TextViewHolder(inflater.inflate(R.layout.block_text, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ContentBlockEntity block = blocks.get(position);
        if (holder instanceof TextViewHolder) {
            ((TextViewHolder) holder).bind(block);
        } else if (holder instanceof ImageViewHolder) {
            ((ImageViewHolder) holder).bind(block);
        } else if (holder instanceof HeroViewHolder) {
            ((HeroViewHolder) holder).bind(block);
        } else if (holder instanceof ButtonViewHolder) {
            ((ButtonViewHolder) holder).bind(block);
        } else if (holder instanceof ContactViewHolder) {
            ((ContactViewHolder) holder).bind(block);
        } else if (holder instanceof SavingsHeroViewHolder) {
            ((SavingsHeroViewHolder) holder).bind(block);
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvContent;
        TextViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.block_title);
            tvContent = v.findViewById(R.id.block_content);
        }
        void bind(ContentBlockEntity block) {
            tvTitle.setText(block.title);
            tvContent.setText(block.content);
            if (block.textColor != null) {
                try { tvContent.setTextColor(Color.parseColor(block.textColor)); } catch (Exception ignored) {}
            }
            if ("CENTER".equals(block.alignment)) {
                tvTitle.setGravity(Gravity.CENTER);
                tvContent.setGravity(Gravity.CENTER);
            } else if ("RIGHT".equals(block.alignment)) {
                tvTitle.setGravity(Gravity.END);
                tvContent.setGravity(Gravity.END);
            } else {
                tvTitle.setGravity(Gravity.START);
                tvContent.setGravity(Gravity.START);
            }
            if ("BOLD".equals(block.fontWeight)) {
                tvContent.setTypeface(null, Typeface.BOLD);
            } else {
                tvContent.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView iv;
        ImageViewHolder(View v) {
            super(v);
            iv = v.findViewById(R.id.block_image);
        }
        void bind(ContentBlockEntity block) {
            if (block.mediaPath != null && !block.mediaPath.isEmpty()) {
                // 1. Intentar cargar como Recurso Local (Drawable)
                int resId = itemView.getContext().getResources().getIdentifier(
                        block.mediaPath, "drawable", itemView.getContext().getPackageName());
                
                if (resId != 0) {
                    iv.setImageResource(resId);
                } else {
                    iv.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }
    }

    static class HeroViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        ImageView iv;
        HeroViewHolder(View v) {
            super(v);
            tv = v.findViewById(R.id.block_hero_title);
            iv = v.findViewById(R.id.block_hero_image);
        }
        void bind(ContentBlockEntity block) {
            tv.setText(block.title);
            if (block.mediaPath != null) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        block.mediaPath, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) iv.setImageResource(resId);
            }
        }
    }

    static class ButtonViewHolder extends RecyclerView.ViewHolder {
        Button btn;
        ButtonViewHolder(View v) {
            super(v);
            btn = v.findViewById(R.id.block_button);
        }
        void bind(ContentBlockEntity block) {
            btn.setText(block.buttonText);
            if (block.backgroundColor != null) {
                try { btn.setBackgroundColor(Color.parseColor(block.backgroundColor)); } catch (Exception ignored) {}
            }
        }
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        Button btn;
        ContactViewHolder(View v) {
            super(v);
            btn = v.findViewById(R.id.block_button);
        }
        void bind(ContentBlockEntity block) {
            btn.setText(block.buttonText);
            btn.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + block.content));
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "No se pudo realizar la llamada", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    static class SavingsHeroViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc;
        Button btn;
        ImageView iv;
        SavingsHeroViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_savings_header_title);
            tvDesc = v.findViewById(R.id.tv_savings_header_desc);
            btn = v.findViewById(R.id.btn_savings_header);
            iv = v.findViewById(R.id.iv_savings_header);
        }
        void bind(ContentBlockEntity block) {
            // Si el título empieza con 'logo_', ocultamos el texto y mostramos imagen arriba
            if (block.title != null && block.title.startsWith("logo_")) {
                tvTitle.setVisibility(View.GONE);
                // Aquí podrías agregar un ImageView dinámico o usar uno existente si el layout lo permite
            } else {
                tvTitle.setVisibility(View.VISIBLE);
                tvTitle.setText(block.title);
            }
            
            tvDesc.setText(block.content);
            btn.setText(block.buttonText);
            
            btn.setOnClickListener(v -> {
                // Acción para abrir cuenta o servicio
                Toast.makeText(v.getContext(), "Procesando solicitud...", Toast.LENGTH_SHORT).show();
            });

            if (block.mediaPath != null) {
                int resId = itemView.getContext().getResources().getIdentifier(
                        block.mediaPath, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) iv.setImageResource(resId);
            }
        }
    }
}
