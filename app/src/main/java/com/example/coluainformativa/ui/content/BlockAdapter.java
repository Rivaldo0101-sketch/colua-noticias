package com.example.coluainformativa.ui.content;

import android.content.Intent;
import android.content.res.ColorStateList;
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

import com.example.coluainformativa.AdminNewsEditActivity;
import com.example.coluainformativa.DynamicSectionActivity;
import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentBlockEntity;

import java.util.List;
import java.util.Locale;

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
        switch (type.toUpperCase(Locale.getDefault())) {
            case "IMAGE":
            case "BANNER":
            case "ICON":
                return TYPE_IMAGE;
            case "HERO":
                return TYPE_HERO;
            case "BUTTON":
                return TYPE_BUTTON;
            case "CONTACT":
                return TYPE_CONTACT;
            case "SAVINGS_HERO":
            case "CARD":
            case "PRODUCT_CARD":
            case "CONTAINER":
                return TYPE_SAVINGS_HERO;
            default:
                return TYPE_TEXT;
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
            if (tvTitle != null) {
                if (block.title != null && !block.title.trim().isEmpty()) {
                    tvTitle.setVisibility(View.VISIBLE);
                    tvTitle.setText(block.title);
                } else {
                    tvTitle.setVisibility(View.GONE);
                }
            }

            if (tvContent != null) {
                StringBuilder fullText = new StringBuilder();

                // 1. Párrafo / Contenido Principal
                if (block.content != null && !block.content.trim().isEmpty()) {
                    fullText.append(block.content.trim());
                }

                // 2. Lista de Viñetas / Beneficios (BENEFITS:...)
                if (block.fontSize != null && block.fontSize.startsWith("BENEFITS:")) {
                    String benefitsRaw = block.fontSize.replace("BENEFITS:", "").trim();
                    if (!benefitsRaw.isEmpty()) {
                        if (fullText.length() > 0) fullText.append("\n\n");
                        String[] lines = benefitsRaw.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                if (line.startsWith("•") || line.startsWith("-")) {
                                    fullText.append(line);
                                } else {
                                    fullText.append("• ").append(line);
                                }
                                if (i < lines.length - 1) fullText.append("\n");
                            }
                        }
                    }
                }

                // 3. Monto / Tasa / Destacado (MOUNT:...)
                if (block.textColor != null && block.textColor.startsWith("MOUNT:")) {
                    String mountRaw = block.textColor.replace("MOUNT:", "").trim();
                    if (!mountRaw.isEmpty()) {
                        if (fullText.length() > 0) fullText.append("\n\n");
                        if (!mountRaw.toLowerCase().contains("monto") && !mountRaw.toLowerCase().contains("tasa") && !mountRaw.toLowerCase().contains("destacado")) {
                            fullText.append("Monto / Destacado: ").append(mountRaw);
                        } else {
                            fullText.append(mountRaw);
                        }
                    }
                }

                tvContent.setText(fullText.toString());

                if ("CENTER".equalsIgnoreCase(block.alignment)) {
                    if (tvTitle != null) tvTitle.setGravity(Gravity.CENTER);
                    tvContent.setGravity(Gravity.CENTER);
                } else if ("RIGHT".equalsIgnoreCase(block.alignment)) {
                    if (tvTitle != null) tvTitle.setGravity(Gravity.END);
                    tvContent.setGravity(Gravity.END);
                } else {
                    if (tvTitle != null) tvTitle.setGravity(Gravity.START);
                    tvContent.setGravity(Gravity.START);
                }

                if ("BOLD".equalsIgnoreCase(block.fontWeight)) {
                    tvContent.setTypeface(null, Typeface.BOLD);
                } else {
                    tvContent.setTypeface(null, Typeface.NORMAL);
                }

                if (block.textColor != null && !block.textColor.isEmpty() && !block.textColor.startsWith("MOUNT:")) {
                    try { tvContent.setTextColor(Color.parseColor(block.textColor)); } catch (Exception ignored) {}
                } else {
                    tvContent.setTextColor(Color.parseColor("#334155"));
                }
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
            String path = block.mediaPath != null && !block.mediaPath.trim().isEmpty() ? block.mediaPath.trim() : "distintivo_colua";
            String type = block.type != null ? block.type.trim().toUpperCase(Locale.getDefault()) : "IMAGE";

            if (iv != null) {
                boolean isSmallIcon = path.startsWith("ic_")
                        || "distintivo_colua".equalsIgnoreCase(path)
                        || "public_service".equalsIgnoreCase(path)
                        || "grupo".equalsIgnoreCase(path)
                        || "ICON".equalsIgnoreCase(type);

                if (isSmallIcon) {
                    // Ícono o logotipo de cabecera: Mediano-pequeño centrado
                    ViewGroup.LayoutParams lp = iv.getLayoutParams();
                    if (lp != null) {
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        iv.setLayoutParams(lp);
                    }
                    iv.setMaxHeight(120);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    // Imagen subida o banner promocional: Ancho completo casi tocando los márgenes
                    ViewGroup.LayoutParams lp = iv.getLayoutParams();
                    if (lp != null) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        iv.setLayoutParams(lp);
                    }
                    iv.setMaxHeight(360);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }

                AdminNewsEditActivity.loadNewsImageIntoView(itemView.getContext(), iv, path);
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
            if (tv != null) tv.setText(block.title != null ? block.title : "");
            if (iv != null && block.mediaPath != null) {
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
            String label = block.buttonText != null && !block.buttonText.trim().isEmpty() ? block.buttonText : block.title;
            if (label == null || label.trim().isEmpty()) label = "Aceptar";
            btn.setText(label);

            if (block.backgroundColor != null && !block.backgroundColor.isEmpty()) {
                try { btn.setBackgroundColor(Color.parseColor(block.backgroundColor)); } catch (Exception ignored) {}
            }

            btn.setOnClickListener(v -> {
                String action = block.buttonAction != null && !block.buttonAction.trim().isEmpty() ? block.buttonAction : block.content;
                if (action != null && !action.trim().isEmpty()) {
                    if (action.startsWith("tel:")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(action));
                            v.getContext().startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(v.getContext(), "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show();
                        }
                    } else if (action.startsWith("http://") || action.startsWith("https://") || action.contains("wa.me")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action));
                            v.getContext().startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(v.getContext(), "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
                        }
                    } else if (action.startsWith("/") || action.startsWith("sec_")) {
                        String targetSec = action.replace("/", "");
                        Intent intent = new Intent(v.getContext(), DynamicSectionActivity.class);
                        intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, targetSec);
                        v.getContext().startActivity(intent);
                    } else {
                        Toast.makeText(v.getContext(), "Acción: " + action, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        Button btn;
        ContactViewHolder(View v) {
            super(v);
            btn = v.findViewById(R.id.block_button);
        }
        void bind(ContentBlockEntity block) {
            String label = block.buttonText != null && !block.buttonText.isEmpty() ? block.buttonText : "Llamar";
            btn.setText(label);
            btn.setOnClickListener(v -> {
                try {
                    String phone = block.buttonAction != null ? block.buttonAction : block.content;
                    if (!phone.startsWith("tel:")) phone = "tel:" + phone;
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(phone));
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
            if (tvTitle != null) {
                if (block.title != null && !block.title.isEmpty()) {
                    tvTitle.setVisibility(View.VISIBLE);
                    tvTitle.setText(block.title);
                } else {
                    tvTitle.setVisibility(View.GONE);
                }
            }

            String descText = block.content != null ? block.content : "";
            if (block.fontSize != null && block.fontSize.startsWith("BENEFITS:")) {
                String benefits = block.fontSize.replace("BENEFITS:", "").trim();
                if (!benefits.isEmpty()) {
                    descText = descText.isEmpty() ? benefits : descText + "\n\n" + benefits;
                }
            }
            if (block.textColor != null && block.textColor.startsWith("MOUNT:")) {
                String mount = block.textColor.replace("MOUNT:", "").trim();
                if (!mount.isEmpty()) {
                    descText = descText.isEmpty() ? mount : descText + "\n" + mount;
                }
            }

            if (tvDesc != null) tvDesc.setText(descText);

            if (btn != null) {
                String btnTxt = block.buttonText != null && !block.buttonText.trim().isEmpty() ? block.buttonText : "Solicitar información";
                btn.setText(btnTxt);

                btn.setOnClickListener(v -> {
                    String action = block.buttonAction != null && !block.buttonAction.isEmpty() ? block.buttonAction : "/creditos";
                    if (action.startsWith("tel:")) {
                        try {
                            v.getContext().startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(action)));
                        } catch (Exception ignored) {}
                    } else if (action.startsWith("http")) {
                        try {
                            v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(action)));
                        } catch (Exception ignored) {}
                    } else {
                        String targetSec = action.replace("/", "");
                        Intent intent = new Intent(v.getContext(), DynamicSectionActivity.class);
                        intent.putExtra(DynamicSectionActivity.EXTRA_SECTION_ID, targetSec);
                        v.getContext().startActivity(intent);
                    }
                });
            }

            if (iv != null) {
                String media = block.mediaPath != null && !block.mediaPath.trim().isEmpty() ? block.mediaPath.trim() : "distintivo_colua";
                int resId = itemView.getContext().getResources().getIdentifier(
                        media, "drawable", itemView.getContext().getPackageName());
                if (resId != 0) {
                    iv.setImageResource(resId);
                    if (media.startsWith("ic_")) {
                        iv.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                    } else {
                        iv.setImageTintList(null);
                    }
                } else {
                    iv.setImageResource(R.drawable.distintivo_colua);
                    iv.setImageTintList(null);
                }
            }
        }
    }
}
