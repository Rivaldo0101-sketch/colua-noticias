package com.example.coluainformativa.ui.content;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.AdminContentEditActivity;
import com.example.coluainformativa.AdminNewsEditActivity;
import com.example.coluainformativa.ProfileActivity;
import com.example.coluainformativa.R;
import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.utils.DialogHelper;

import android.content.ClipboardManager;
import android.content.ClipData;
import java.io.FileOutputStream;
import com.example.coluainformativa.security.AdminAuthManager;

import java.util.ArrayList;

import java.io.File;
import java.io.FileOutputStream;
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
        if ("sec_noticias".equalsIgnoreCase(item.sectionId) || "noticias".equalsIgnoreCase(item.sectionId)) {
            return TYPE_NEWS;
        }
        if ("BENEFIT".equalsIgnoreCase(item.shortDescription)) {
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
                Intent intent = new Intent(itemView.getContext(), AdminContentEditActivity.class);
                intent.putExtra(AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
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
                    ivIcon.setBackgroundTintList(ColorStateList.valueOf(color));
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
                Intent intent = new Intent(itemView.getContext(), AdminContentEditActivity.class);
                intent.putExtra(AdminContentEditActivity.EXTRA_ITEM_ID, item.id);
                intent.putExtra(AdminContentEditActivity.EXTRA_SECTION_ID, item.sectionId);
                itemView.getContext().startActivity(intent);
            });

            if (item.accentColor != null) {
                try {
                    int color = Color.parseColor(item.accentColor);
                    sideBorder.setBackgroundColor(color);
                    ivIcon.setBackgroundTintList(ColorStateList.valueOf(color));
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

    public static List<String> parsePhotosList(ContentItemEntity item) {
        List<String> list = new ArrayList<>();
        if (item == null) return list;

        if (item.photosJson != null && !item.photosJson.trim().isEmpty()) {
            String raw = item.photosJson.replace("[", "").replace("]", "").replace("\"", "");
            String[] parts = raw.split(",");
            for (String p : parts) {
                String cleanP = p.trim();
                if (!cleanP.isEmpty() && !list.contains(cleanP)) {
                    list.add(cleanP);
                }
            }
        }

        if (item.imagePath != null && !item.imagePath.trim().isEmpty()) {
            String[] parts = item.imagePath.split(",");
            for (String p : parts) {
                String cleanP = p.trim();
                if (!cleanP.isEmpty() && !list.contains(cleanP)) {
                    list.add(cleanP);
                }
            }
        }

        return list;
    }

    static class NewsViewHolder extends RecyclerView.ViewHolder {
        private final View layoutPhotoContainer;
        private final ImageView ivPhoto, ivAuthorAvatar;
        private final TextView tvAuthor, tvDate, tvLikesBtn, tvShareBtn, tvTitle, tvDesc, tvHashtags, tvCarouselBadge, tvCarouselDots, tvPhotoTagPill, btnCta;
        private final View btnEdit;

        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutPhotoContainer = itemView.findViewById(R.id.layout_public_news_photo_container);
            ivPhoto = itemView.findViewById(R.id.iv_public_news_photo);
            tvCarouselBadge = itemView.findViewById(R.id.tv_photo_carousel_badge);
            tvCarouselDots = itemView.findViewById(R.id.tv_carousel_dots_indicator);
            tvPhotoTagPill = itemView.findViewById(R.id.tv_news_photo_tag_pill);

            ivAuthorAvatar = itemView.findViewById(R.id.iv_public_news_author_avatar);
            tvAuthor = itemView.findViewById(R.id.tv_public_news_author);
            tvDate = itemView.findViewById(R.id.tv_public_news_date_str);
            tvLikesBtn = itemView.findViewById(R.id.tv_public_likes_btn);
            tvShareBtn = itemView.findViewById(R.id.tv_public_share_btn);
            tvTitle = itemView.findViewById(R.id.tv_public_news_title);
            tvDesc = itemView.findViewById(R.id.tv_public_news_description);
            tvHashtags = itemView.findViewById(R.id.tv_public_news_hashtags);
            btnCta = itemView.findViewById(R.id.btn_public_news_cta);
            btnEdit = itemView.findViewById(R.id.btn_edit_content_item);
        }

        public void bind(ContentItemEntity item, OnItemClickListener listener, boolean isEditMode) {
            if (tvTitle != null) tvTitle.setText(item.title != null ? item.title : "Noticia");
            if (tvDesc != null) {
                String fullDesc = item.description != null && !item.description.trim().isEmpty() ? item.description : (item.shortDescription != null ? item.shortDescription : "");
                tvDesc.setText(fullDesc);
                tvDesc.setMaxLines(3);
                tvDesc.setEllipsize(TextUtils.TruncateAt.END);
            }
            if (tvHashtags != null) tvHashtags.setText(item.tags != null ? item.tags : "#COLUAVerde #MICOOPE");
            if (tvAuthor != null) tvAuthor.setText(item.issuerName != null && !item.issuerName.isEmpty() ? item.issuerName : "Cooperativa COLUA");

            if (tvDate != null) {
                String role = item.issuerRole != null && !item.issuerRole.isEmpty() ? item.issuerRole : "Oficial";
                tvDate.setText(role);
            }

            if (ivAuthorAvatar != null) {
                String avatarPath = item.iconName != null && !item.iconName.isEmpty() ? item.iconName : "distintivo_colua";
                AdminNewsEditActivity.loadNewsImageIntoView(itemView.getContext(), ivAuthorAvatar, avatarPath);
            }

            // Etiqueta píldora sobre la foto según los hashtags de la noticia
            if (tvPhotoTagPill != null) {
                String tagStr = item.tags != null ? item.tags.trim() : "";
                if (tagStr.toLowerCase().contains("verde") || tagStr.toLowerCase().contains("reforestacion") || tagStr.toLowerCase().contains("ambiente")) {
                    tvPhotoTagPill.setText("🌲 COLUA Verde");
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                } else if (tagStr.toLowerCase().contains("ahorro") || tagStr.toLowerCase().contains("cuenta")) {
                    tvPhotoTagPill.setText("💰 COLUA Ahorros");
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                } else if (tagStr.toLowerCase().contains("credito") || tagStr.toLowerCase().contains("prestamo")) {
                    tvPhotoTagPill.setText("💳 COLUA Crédito");
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                } else if (tagStr.toLowerCase().contains("seguro")) {
                    tvPhotoTagPill.setText("🛡️ COLUA Seguro");
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                } else if (!tagStr.isEmpty()) {
                    String firstTag = tagStr.split(" ")[0].replace("#", "");
                    tvPhotoTagPill.setText("📢 " + firstTag);
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                } else {
                    tvPhotoTagPill.setText("📢 Noticia Oficial");
                    tvPhotoTagPill.setVisibility(View.VISIBLE);
                }
            }

            // Manejo dinámico de fotos y carrusel táctil con gestos de deslizar
            List<String> photos = parsePhotosList(item);
            if (layoutPhotoContainer != null) {
                if (photos.isEmpty()) {
                    layoutPhotoContainer.setVisibility(View.GONE);
                } else {
                    layoutPhotoContainer.setVisibility(View.VISIBLE);
                    int total = photos.size();
                    final int[] currentIndex = new int[]{0};

                    Runnable updatePhotoDisplay = () -> {
                        if (ivPhoto != null && currentIndex[0] >= 0 && currentIndex[0] < photos.size()) {
                            AdminNewsEditActivity.loadNewsImageIntoView(itemView.getContext(), ivPhoto, photos.get(currentIndex[0]));
                        }
                        if (tvCarouselBadge != null) {
                            tvCarouselBadge.setText((currentIndex[0] + 1) + "/" + total);
                        }
                        if (tvCarouselDots != null) {
                            StringBuilder dots = new StringBuilder();
                            for (int i = 0; i < total; i++) {
                                if (i == currentIndex[0]) {
                                    dots.append("●");
                                } else {
                                    dots.append("○");
                                }
                                if (i < total - 1) dots.append("  ");
                            }
                            tvCarouselDots.setText(dots.toString());
                        }
                    };

                    updatePhotoDisplay.run();

                    if (total <= 1) {
                        if (tvCarouselBadge != null) tvCarouselBadge.setVisibility(View.GONE);
                        if (tvCarouselDots != null) tvCarouselDots.setVisibility(View.GONE);
                        if (ivPhoto != null) {
                            ivPhoto.setOnTouchListener(null);
                            ivPhoto.setOnClickListener(null);
                        }
                    } else {
                        if (tvCarouselBadge != null) tvCarouselBadge.setVisibility(View.VISIBLE);
                        if (tvCarouselDots != null) tvCarouselDots.setVisibility(View.VISIBLE);

                        Runnable nextPhotoAction = () -> {
                            currentIndex[0] = (currentIndex[0] + 1) % total;
                            updatePhotoDisplay.run();
                        };

                        Runnable prevPhotoAction = () -> {
                            currentIndex[0] = (currentIndex[0] - 1 + total) % total;
                            updatePhotoDisplay.run();
                        };

                        OnSwipeTouchListener swipeListener = new OnSwipeTouchListener(
                                itemView.getContext(),
                                nextPhotoAction,
                                prevPhotoAction,
                                nextPhotoAction
                        );

                        if (ivPhoto != null) {
                            ivPhoto.setOnTouchListener(swipeListener);
                        }
                    }
                }
            }

            if (tvLikesBtn != null) {
                SharedPreferences prefs = itemView.getContext().getSharedPreferences("LikedPostsPrefs", Context.MODE_PRIVATE);
                boolean isLiked = prefs.getBoolean("liked_" + item.id, false);

                tvLikesBtn.setText((isLiked ? "❤️ " : "🤍 ") + item.likesCount + " me gusta");

                tvLikesBtn.setOnClickListener(v -> {
                    SharedPreferences userPrefs = itemView.getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    String userName = userPrefs.getString("user_name", "");
                    String userRole = userPrefs.getString("user_role", "GUEST");
                    boolean isRegisteredUser = !userName.isEmpty() 
                            && !"Invitado".equalsIgnoreCase(userName) 
                            && !"GUEST".equalsIgnoreCase(userRole);

                    if (!isRegisteredUser) {
                        new AlertDialog.Builder(itemView.getContext(), androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
                                .setTitle("Perfil de Asociado Requerido")
                                .setMessage("Para dar me gusta o reaccionar a las publicaciones debes registrar tu perfil de asociado.\n\n¿Deseas registrar o editar tu perfil de asociado ahora?")
                                .setPositiveButton("REGISTRAR / MI PERFIL", (dialog, which) -> {
                                    Intent intent = new Intent(itemView.getContext(), ProfileActivity.class);
                                    itemView.getContext().startActivity(intent);
                                })
                                .setNegativeButton("Más Tarde", null)
                                .show();
                        return;
                    }

                    boolean currentlyLiked = prefs.getBoolean("liked_" + item.id, false);
                    if (currentlyLiked) {
                        item.likesCount = Math.max(0, item.likesCount - 1);
                        prefs.edit().putBoolean("liked_" + item.id, false).apply();
                        tvLikesBtn.setText("🤍 " + item.likesCount + " me gusta");
                        Toast.makeText(itemView.getContext(), "Ya no te gusta esta publicación", Toast.LENGTH_SHORT).show();
                    } else {
                        item.likesCount++;
                        prefs.edit().putBoolean("liked_" + item.id, true).apply();
                        tvLikesBtn.setText("❤️ " + item.likesCount + " me gusta");
                        Toast.makeText(itemView.getContext(), "¡Te gusta esta publicación!", Toast.LENGTH_SHORT).show();
                    }

                    // Guardar estadísticas de Me Gusta en la BD local y nube
                    new Thread(() -> {
                        ColuaRepository repo = new ColuaRepository(itemView.getContext());
                        repo.insertItem(item);
                    }).start();
                });
            }

            if (tvShareBtn != null) {
                tvShareBtn.setText("🔁 Compartir");

                tvShareBtn.setOnClickListener(v -> {
                    SharedPreferences userPrefs = itemView.getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    String userName = userPrefs.getString("user_name", "");
                    String userRole = userPrefs.getString("user_role", "GUEST");
                    boolean isRegisteredUser = !userName.isEmpty() 
                            && !"Invitado".equalsIgnoreCase(userName) 
                            && !"GUEST".equalsIgnoreCase(userRole);

                    if (!isRegisteredUser) {
                        DialogHelper.showLightReportDialog(
                                itemView.getContext(),
                                "Perfil de Asociado Requerido",
                                "Para compartir publicaciones debes registrar tu perfil de asociado.\n\n¿Deseas registrar o editar tu perfil de asociado ahora?",
                                "REGISTRAR / MI PERFIL",
                                () -> {
                                    Intent intent = new Intent(itemView.getContext(), ProfileActivity.class);
                                    itemView.getContext().startActivity(intent);
                                },
                                "MÁS TARDE"
                        );
                        return;
                    }

                    // Diálogo de Compartido con diseño personalizado y botones Material
                    View dialogView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_light_report, null);
                    TextView tvTitle = dialogView.findViewById(R.id.tv_report_title);
                    TextView tvContent = dialogView.findViewById(R.id.tv_report_content);
                    Button btnLink = dialogView.findViewById(R.id.btn_report_primary);
                    Button btnImage = dialogView.findViewById(R.id.btn_report_secondary);

                    if (tvTitle != null) tvTitle.setText("Compartir Publicación");
                    if (tvContent != null) tvContent.setText("Selecciona el formato en el que deseas compartir esta noticia:");

                    AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                            .setView(dialogView)
                            .create();

                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }

                    if (btnLink != null) {
                        btnLink.setText("🔗 Copiar Enlace y Texto");
                        btnLink.setOnClickListener(vLink -> {
                            dialog.dismiss();
                            item.sharesCount++;
                            new Thread(() -> {
                                ColuaRepository repo = new ColuaRepository(itemView.getContext());
                                repo.insertItem(item);
                            }).start();

                            String shareUrl = new ColuaRepository(itemView.getContext()).getGlobalConfig("share_app_url");
                            if (shareUrl.isEmpty()) shareUrl = "https://colua.com.gt/noticias";

                            String shareBody = item.title + "\n\n" + (item.description != null && !item.description.isEmpty() ? item.description : item.shortDescription) + "\n\n" + (item.tags != null ? item.tags : "#COLUAVerde") + "\n\nLee la noticia completa en la App COLUA MICOOPE:\n" + shareUrl;

                            try {
                                ClipboardManager clipboard = (ClipboardManager) itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("Noticia COLUA", shareBody);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(itemView.getContext(), "¡Enlace y contenido copiados al portapapeles!", Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception ignored) {}

                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("text/plain");
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.title);
                            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                            itemView.getContext().startActivity(Intent.createChooser(shareIntent, "Compartir noticia vía:"));
                        });
                    }

                    if (btnImage != null) {
                        btnImage.setText("🖼️ Compartir como Imagen");
                        btnImage.setOnClickListener(vImg -> {
                            dialog.dismiss();
                            item.sharesCount++;
                            new Thread(() -> {
                                ColuaRepository repo = new ColuaRepository(itemView.getContext());
                                repo.insertItem(item);
                            }).start();

                            shareNewsAsImage(itemView.getContext(), item);
                        });
                    }

                    dialog.show();
                });
            }

            if (btnCta != null) {
                String ctaLabel = item.subtitle != null && !item.subtitle.trim().isEmpty() ? item.subtitle : "Leer artículo completo ›";
                btnCta.setText(ctaLabel);

                btnCta.setOnClickListener(v -> {
                    String action = item.targetSectionId != null ? item.targetSectionId.trim() : "";
                    if (action.startsWith("http")) {
                        try {
                            itemView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(action)));
                        } catch (Exception ignored) {}
                    } else if (action.startsWith("tel:")) {
                        try {
                            itemView.getContext().startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(action)));
                        } catch (Exception ignored) {}
                    } else {
                        showNewsDialog(item);
                    }
                });
            }

            if (ivPhoto != null) {
                String path = item.imagePath != null ? item.imagePath.trim() : "";
                AdminNewsEditActivity.loadNewsImageIntoView(itemView.getContext(), ivPhoto, path);
            }

            if (btnEdit != null) {
                btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
                btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), AdminNewsEditActivity.class);
                    intent.putExtra(AdminNewsEditActivity.EXTRA_ITEM_ID, item.id);
                    itemView.getContext().startActivity(intent);
                });
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        private void showNewsDialog(ContentItemEntity item) {
            View dialogView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_account_details, null);
            TextView title = dialogView.findViewById(R.id.dialog_title);
            TextView content = dialogView.findViewById(R.id.dialog_content);
            Button btnCall = dialogView.findViewById(R.id.btn_dialog_call);

            if (title != null) title.setText(item.title);
            if (content != null) content.setText(item.description != null ? item.description : item.shortDescription);
            if (btnCall != null) {
                btnCall.setText("Llamar para más información");
                btnCall.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:77957795"));
                        itemView.getContext().startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(itemView.getContext(), "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            AlertDialog dialog = new AlertDialog.Builder(itemView.getContext())
                    .setView(dialogView)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            View closeBtn = dialogView.findViewById(R.id.btn_close_dialog);
            if (closeBtn != null) closeBtn.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }

        private void shareNewsAsImage(Context context, ContentItemEntity item) {
            try {
                View cardView = LayoutInflater.from(context).inflate(R.layout.item_news_card, null);

                TextView tvTitle = cardView.findViewById(R.id.tv_public_news_title);
                TextView tvDesc = cardView.findViewById(R.id.tv_public_news_description);
                TextView tvHashtags = cardView.findViewById(R.id.tv_public_news_hashtags);
                TextView tvAuthor = cardView.findViewById(R.id.tv_public_news_author);
                TextView tvDate = cardView.findViewById(R.id.tv_public_news_date_str);
                TextView tvLikes = cardView.findViewById(R.id.tv_public_likes_btn);
                TextView tvShare = cardView.findViewById(R.id.tv_public_share_btn);
                ImageView ivPhoto = cardView.findViewById(R.id.iv_public_news_photo);
                ImageView ivAuthorAvatar = cardView.findViewById(R.id.iv_public_news_author_avatar);
                View photoContainer = cardView.findViewById(R.id.layout_public_news_photo_container);

                if (tvTitle != null) tvTitle.setText(item.title != null ? item.title : "");
                if (tvDesc != null) tvDesc.setText(item.description != null && !item.description.isEmpty() ? item.description : item.shortDescription);
                if (tvHashtags != null) tvHashtags.setText(item.tags != null ? item.tags : "#COLUAVerde");
                if (tvAuthor != null) tvAuthor.setText(item.issuerName != null ? item.issuerName : "Cooperativa COLUA");
                if (tvDate != null) tvDate.setText(item.issuerRole != null ? item.issuerRole : "Oficial");
                if (tvLikes != null) tvLikes.setText("❤️ " + item.likesCount + " me gusta");
                if (tvShare != null) tvShare.setText("🔁 Compartir");

                if (ivAuthorAvatar != null) {
                    String avatarPath = item.iconName != null && !item.iconName.isEmpty() ? item.iconName : "distintivo_colua";
                    AdminNewsEditActivity.loadNewsImageIntoView(context, ivAuthorAvatar, avatarPath);
                }

                List<String> photos = parsePhotosList(item);
                if (photoContainer != null) {
                    if (photos.isEmpty()) {
                        photoContainer.setVisibility(View.GONE);
                    } else {
                        photoContainer.setVisibility(View.VISIBLE);
                        if (ivPhoto != null) {
                            AdminNewsEditActivity.loadNewsImageIntoView(context, ivPhoto, photos.get(0));
                        }
                    }
                }

                int specWidth = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                int specHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                cardView.measure(specWidth, specHeight);
                cardView.layout(0, 0, cardView.getMeasuredWidth(), cardView.getMeasuredHeight());

                Bitmap bitmap = Bitmap.createBitmap(
                        cardView.getMeasuredWidth(),
                        cardView.getMeasuredHeight(),
                        Bitmap.Config.ARGB_8888
                );
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                cardView.draw(canvas);

                File cachePath = new File(context.getCacheDir(), "images");
                cachePath.mkdirs();
                File imageFile = new File(cachePath, "noticia_colua_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream stream = new FileOutputStream(imageFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.flush();
                }

                Uri contentUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        imageFile
                );

                String shareUrl = new ColuaRepository(context).getGlobalConfig("share_app_url");
                if (shareUrl.isEmpty()) shareUrl = "https://colua.com.gt/noticias";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, item.title + " - COLUA Noticias\n" + shareUrl);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(shareIntent, "Compartir imagen de la noticia vía:"));

            } catch (Exception e) {
                String shareUrl = new ColuaRepository(context).getGlobalConfig("share_app_url");
                if (shareUrl.isEmpty()) shareUrl = "https://colua.com.gt/noticias";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, item.title + "\n\n" + item.shortDescription + "\n\n" + shareUrl);
                context.startActivity(Intent.createChooser(shareIntent, "Compartir noticia vía:"));
            }
        }
    }

    public static class OnSwipeTouchListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;

        public OnSwipeTouchListener(Context ctx, Runnable onSwipeNext, Runnable onSwipePrev, Runnable onClick) {
            gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_THRESHOLD = 30;
                private static final int SWIPE_VELOCITY_THRESHOLD = 30;

                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (onClick != null) onClick.run();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX < 0) {
                                if (onSwipeNext != null) onSwipeNext.run();
                            } else {
                                if (onSwipePrev != null) onSwipePrev.run();
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v != null && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (event != null && event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return gestureDetector != null && event != null && gestureDetector.onTouchEvent(event);
        }
    }
}
