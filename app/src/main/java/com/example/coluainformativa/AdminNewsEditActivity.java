package com.example.coluainformativa;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.coluainformativa.database.ContentItemEntity;
import com.example.coluainformativa.repository.ColuaRepository;
import com.example.coluainformativa.security.AdminAuthManager;
import com.example.coluainformativa.utils.DialogHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import android.view.LayoutInflater;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminNewsEditActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    private static final int REQUEST_PICK_MULTI_IMAGES = 101;

    private ColuaRepository repository;
    private String itemId;
    private ContentItemEntity newsItem;

    private EditText etTitle, etDescription, etTags, etButtonText, etButtonAction, etShareAppUrl;
    private CheckBox cbIsFeatured;
    private TextView tvCharCounter, tvPhotosBadge, tvIssuerName, tvIssuerRole;
    private ImageView ivMainPreview, ivIssuerAvatar;
    private LinearLayout layoutThumbnailsContainer;

    private List<String> photoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_news_edit);

        repository = new ColuaRepository(this);
        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);

        etTitle = findViewById(R.id.et_news_title);
        etDescription = findViewById(R.id.et_news_description);
        etTags = findViewById(R.id.et_news_tags);
        etButtonText = findViewById(R.id.et_news_button_text);
        etButtonAction = findViewById(R.id.et_news_button_action);
        etShareAppUrl = findViewById(R.id.et_share_app_url);
        cbIsFeatured = findViewById(R.id.cb_is_featured);
        tvCharCounter = findViewById(R.id.tv_char_counter);
        tvPhotosBadge = findViewById(R.id.tv_photos_selected_badge);
        ivMainPreview = findViewById(R.id.iv_main_photo_preview);
        layoutThumbnailsContainer = findViewById(R.id.layout_thumbnails_container);

        if (etShareAppUrl != null) {
            new Thread(() -> {
                String gShareUrl = repository.getGlobalConfig("share_app_url");
                if (gShareUrl.isEmpty()) gShareUrl = "https://colua.com.gt/noticias";
                final String finalUrl = gShareUrl;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        etShareAppUrl.setText(finalUrl);
                    }
                });
            }).start();
        }

        tvIssuerName = findViewById(R.id.tv_issuer_name);
        tvIssuerRole = findViewById(R.id.tv_issuer_role);
        ivIssuerAvatar = findViewById(R.id.iv_issuer_avatar);

        View cardIssuer = findViewById(R.id.card_issuer_profile);
        if (cardIssuer != null) {
            cardIssuer.setOnClickListener(v -> showEditIssuerProfileDialog());
        }

        findViewById(R.id.btn_cancel_news_edit).setOnClickListener(v -> finish());
        findViewById(R.id.btn_top_publish).setOnClickListener(v -> confirmAndPublishNews());
        findViewById(R.id.btn_save_news_draft).setOnClickListener(v -> saveNews(true));
        findViewById(R.id.btn_publish_news_now).setOnClickListener(v -> confirmAndPublishNews());

        findViewById(R.id.btn_upload_new_photo).setOnClickListener(v -> openMultiImagePicker());
        findViewById(R.id.btn_camera_picker).setOnClickListener(v -> openMultiImagePicker());

        setupCharCounter();

        if (itemId != null) {
            loadNewsItem();
        } else {
            newsItem = new ContentItemEntity(UUID.randomUUID().toString(), "sec_noticias", "", "Ver detalles completos", "", "#E42A67", 1);
            newsItem.tags = "#COLUAVerde #MICOOPE #Asociados";
            newsItem.imagePath = "";
            newsItem.photosJson = "";
            newsItem.isDraft = true;
            newsItem.isFeatured = true;
            newsItem.likesCount = 0;
            newsItem.sharesCount = 0;
            newsItem.issuerName = "Cooperativa COLUA";
            newsItem.issuerRole = "Oficial";

            etTitle.setText("");
            etDescription.setText("");

            photoList.clear();
            renderPhotoThumbnails();
        }
    }

    private String currentIssuerAvatarPath = "";
    private ImageView ivDialogIssuerAvatar = null;
    private static final int REQUEST_PICK_ISSUER_AVATAR = 106;

    private void showEditIssuerProfileDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_issuer_profile, null);

        EditText etName = view.findViewById(R.id.et_dialog_issuer_name);
        EditText etRole = view.findViewById(R.id.et_dialog_issuer_role);
        ivDialogIssuerAvatar = view.findViewById(R.id.iv_dialog_issuer_avatar);
        View btnAvatarCircle = view.findViewById(R.id.btn_change_issuer_avatar_circle);
        Button btnSave = view.findViewById(R.id.btn_dialog_issuer_save);
        Button btnCancel = view.findViewById(R.id.btn_dialog_issuer_cancel);

        if (etName != null) {
            etName.setText(tvIssuerName != null && tvIssuerName.getText() != null ? tvIssuerName.getText().toString() : "Cooperativa COLUA");
        }
        if (etRole != null) {
            String roleText = newsItem != null && newsItem.issuerRole != null && !newsItem.issuerRole.isEmpty()
                    ? newsItem.issuerRole
                    : (tvIssuerRole != null && tvIssuerRole.getText() != null ? tvIssuerRole.getText().toString().replace(" • Toca para editar", "") : "Publicando como cuenta oficial");
            etRole.setText(roleText);
        }

        currentIssuerAvatarPath = newsItem != null && newsItem.iconName != null && !newsItem.iconName.isEmpty()
                ? newsItem.iconName
                : repository.getGlobalConfig("issuer_avatar");
        if (currentIssuerAvatarPath.isEmpty()) currentIssuerAvatarPath = "distintivo_colua";

        if (ivDialogIssuerAvatar != null) {
            loadNewsImageIntoView(this, ivDialogIssuerAvatar, currentIssuerAvatarPath);
        }

        if (btnAvatarCircle != null) {
            btnAvatarCircle.setOnClickListener(v -> {
                DialogHelper.showResourcePickerDialog(this, (resName, displayLabel) -> {
                    if (resName != null && !resName.isEmpty()) {
                        currentIssuerAvatarPath = resName;
                        if (ivDialogIssuerAvatar != null) {
                            loadNewsImageIntoView(this, ivDialogIssuerAvatar, resName);
                        }
                    }
                }, () -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        startActivityForResult(Intent.createChooser(intent, "Seleccionar foto de perfil del emisor"), REQUEST_PICK_ISSUER_AVATAR);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error al abrir selector de fotos", Toast.LENGTH_SHORT).show();
                    }
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
                String name = etName != null ? etName.getText().toString().trim() : "Cooperativa COLUA";
                String role = etRole != null ? etRole.getText().toString().trim() : "Publicando como cuenta oficial";

                if (name.isEmpty()) name = "Cooperativa COLUA";
                if (role.isEmpty()) role = "Publicando como cuenta oficial";

                final String finalName = name;
                final String finalRole = role;
                final String finalAvatar = currentIssuerAvatarPath;

                dialog.dismiss();

                // Confirmación con contraseña del administrador
                DialogHelper.showPasswordConfirmationDialog(
                        this,
                        new AdminAuthManager(this),
                        "Confirmar Cambios de Perfil",
                        "Para actualizar el Perfil Emisor oficial e ingresar su firma en la noticia, ingrese su clave de administrador:",
                        () -> {
                            tvIssuerName.setText(finalName);
                            tvIssuerRole.setText(finalRole + " • Toca para editar");
                            if (ivIssuerAvatar != null) {
                                loadNewsImageIntoView(this, ivIssuerAvatar, finalAvatar);
                            }

                            if (newsItem != null) {
                                newsItem.issuerName = finalName;
                                newsItem.issuerRole = finalRole;
                                newsItem.iconName = finalAvatar;
                            }

                            new Thread(() -> {
                                repository.setGlobalConfig("issuer_name", finalName);
                                repository.setGlobalConfig("issuer_role", finalRole);
                                repository.setGlobalConfig("issuer_avatar", finalAvatar);
                            }).start();

                            Toast.makeText(this, "Perfil de emisor actualizado", Toast.LENGTH_SHORT).show();
                        }
                );
            });
        }

        dialog.show();
    }

    private void setupCharCounter() {
        if (etDescription != null && tvCharCounter != null) {
            etDescription.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    tvCharCounter.setText(s.length() + " / 500 car.");
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void openMultiImagePicker() {
        DialogHelper.showResourcePickerDialog(this, (resName, displayLabel) -> {
            if (resName != null && !resName.isEmpty()) {
                if (!photoList.contains(resName)) {
                    photoList.add(0, resName);
                }
                if (newsItem != null) newsItem.imagePath = resName;
                renderPhotoThumbnails();
            }
        }, () -> {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, "Seleccionar fotos (Múltiple)"), REQUEST_PICK_MULTI_IMAGES);
            } catch (Exception e) {
                Toast.makeText(this, "Error al abrir selector de fotos", Toast.LENGTH_SHORT).show();
            }
        });
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
                    loadNewsImageIntoView(this, ivDialogIssuerAvatar, localPath);
                }
                if (ivIssuerAvatar != null) {
                    loadNewsImageIntoView(this, ivIssuerAvatar, localPath);
                }
                if (newsItem != null) {
                    newsItem.iconName = localPath;
                }
                Toast.makeText(this, "Foto de perfil cargada. Presiona 'Guardar' para aplicar.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == REQUEST_PICK_MULTI_IMAGES && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) {
                        String localPath = saveImageToInternalStorage(uri);
                        if (localPath != null && !photoList.contains(localPath)) {
                            photoList.add(0, localPath);
                        }
                    }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    String localPath = saveImageToInternalStorage(uri);
                    if (localPath != null && !photoList.contains(localPath)) {
                        photoList.add(0, localPath);
                    }
                }
            }
            renderPhotoThumbnails();
        }
    }

    private String saveImageToInternalStorage(Uri sourceUri) {
        if (sourceUri == null) return null;
        try {
            File dir = new File(getFilesDir(), "news_images");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "img_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ".jpg";
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
            Log.e("COLUA_CMS", "Error copiando imagen a almacenamiento interno", e);
            return null;
        }
    }

    public static void loadNewsImageIntoView(Context context, ImageView imageView, String path) {
        if (imageView == null || context == null) return;
        if (path == null) path = "";
        path = path.trim();

        boolean loaded = false;

        if (path.startsWith("/") || path.startsWith("file://")) {
            try {
                String cleanPath = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
                if (cleanPath != null) {
                    File imgFile = new File(cleanPath);
                    if (imgFile.exists() && imgFile.length() > 0) {
                        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                            loaded = true;
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e("COLUA_IMAGE", "Error leyendo archivo local: " + path, e);
            }
        }

        if (!loaded && path.startsWith("content://")) {
            try {
                Uri uri = Uri.parse(path);
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                            loaded = true;
                        }
                    }
                }
            } catch (Throwable e) {
                Log.e("COLUA_IMAGE", "Error leyendo content uri: " + path, e);
            }
        }

        if (!loaded && !path.isEmpty()) {
            try {
                int resId = context.getResources().getIdentifier(path, "drawable", context.getPackageName());
                if (resId != 0) {
                    imageView.setImageResource(resId);
                    loaded = true;
                }
            } catch (Throwable ignored) {}
        }

        if (!loaded) {
            imageView.setImageResource(R.drawable.noticias_colua);
        }
    }

    private void renderPhotoThumbnails() {
        if (tvPhotosBadge != null) {
            tvPhotosBadge.setText("📷 " + photoList.size() + " seleccionada" + (photoList.size() == 1 ? "" : "s"));
        }

        if (layoutThumbnailsContainer == null) return;

        // Limpiar miniaturas anteriores exceptuando el botón de cámara (índice 0)
        while (layoutThumbnailsContainer.getChildCount() > 1) {
            layoutThumbnailsContainer.removeViewAt(1);
        }

        // Cargar vista previa principal con la primera foto
        if (!photoList.isEmpty()) {
            displayPhotoInPreview(photoList.get(0));
        }

        int dp64 = (int) (64 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        for (String photoKey : photoList) {
            if (photoKey == null || photoKey.trim().isEmpty()) continue;
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp64, dp64);
            lp.setMarginStart(dp8);
            card.setLayoutParams(lp);
            card.setRadius(10 * getResources().getDisplayMetrics().density);
            card.setStrokeWidth(1);
            card.setStrokeColor(Color.parseColor("#CBD5E1"));

            ImageView ivThumb = new ImageView(this);
            ivThumb.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);

            loadNewsImageIntoView(this, ivThumb, photoKey);

            card.addView(ivThumb);

            card.setOnClickListener(v -> displayPhotoInPreview(photoKey));
            card.setOnLongClickListener(v -> {
                if (photoList.size() > 1) {
                    photoList.remove(photoKey);
                    renderPhotoThumbnails();
                    Toast.makeText(this, "Foto removida de la publicación", Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            layoutThumbnailsContainer.addView(card);
        }
    }

    private void displayPhotoInPreview(String photoKey) {
        if (ivMainPreview == null) return;
        if (newsItem != null) newsItem.imagePath = photoKey;
        loadNewsImageIntoView(this, ivMainPreview, photoKey);
    }

    private void loadNewsItem() {
        new Thread(() -> {
            try {
                newsItem = repository.getItemById(itemId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (newsItem != null) {
                        etTitle.setText(newsItem.title != null ? newsItem.title : "");
                        etDescription.setText(newsItem.description != null && !newsItem.description.isEmpty() ? newsItem.description : (newsItem.shortDescription != null ? newsItem.shortDescription : ""));
                        etTags.setText(newsItem.tags != null ? newsItem.tags : "#COLUAVerde #MICOOPE");
                        etButtonText.setText(newsItem.subtitle != null ? newsItem.subtitle : "Ver detalles completos");
                        etButtonAction.setText(newsItem.targetSectionId != null ? newsItem.targetSectionId : "");
                        cbIsFeatured.setChecked(newsItem.isFeatured);

                        if (newsItem.issuerName != null && !newsItem.issuerName.isEmpty()) {
                            tvIssuerName.setText(newsItem.issuerName);
                        }
                        if (newsItem.issuerRole != null && !newsItem.issuerRole.isEmpty()) {
                            tvIssuerRole.setText(newsItem.issuerRole + " • Toca para editar");
                        }

                        photoList.clear();
                        if (newsItem.photosJson != null && !newsItem.photosJson.isEmpty()) {
                            String raw = newsItem.photosJson.replace("[", "").replace("]", "").replace("\"", "");
                            String[] parts = raw.split(",");
                            for (String p : parts) {
                                String cleanP = p.trim();
                                if (!cleanP.isEmpty() && !photoList.contains(cleanP)) {
                                    photoList.add(cleanP);
                                }
                            }
                        }

                        if (newsItem.imagePath != null && !newsItem.imagePath.isEmpty()) {
                            String[] parts = newsItem.imagePath.split(",");
                            for (String p : parts) {
                                String cleanP = p.trim();
                                if (!cleanP.isEmpty() && !photoList.contains(cleanP)) {
                                    photoList.add(cleanP);
                                }
                            }
                        }

                        renderPhotoThumbnails();
                    } else {
                        Toast.makeText(this, "No se encontró la publicación para editar", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Error al cargar la publicación: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void confirmAndPublishNews() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) title = "Noticia Oficial";

        DialogHelper.showLightReportDialog(this, "Confirmar Publicación Oficial",
                "¿Desea publicar inmediatamente la noticia '" + title + "' en la nube? Todos los asociados y usuarios podrán verla de inmediato.",
                "PUBLICAR NOTICIA",
                () -> saveNews(false),
                "CANCELAR"
        );
    }

    private void saveNews(boolean isDraft) {
        String title = etTitle != null ? etTitle.getText().toString().trim() : "";
        String desc = etDescription != null ? etDescription.getText().toString().trim() : "";

        if (!isDraft) {
            if (title.isEmpty() || desc.isEmpty()) {
                DialogHelper.showLightReportDialog(this, "Publicación Incompleta",
                        "Para publicar oficialmente la noticia debes ingresar un Título y una Descripción o Comunicado.\n\nPuedes guardarla como 'Borrador' mientras completas la información.",
                        "ENTENDIDO", null, null);
                return;
            }
        } else {
            if (title.isEmpty()) {
                title = "Borrador de Noticia";
            }
        }

        if (newsItem == null) {
            newsItem = new ContentItemEntity(
                    UUID.randomUUID().toString(),
                    "sec_noticias",
                    title,
                    etButtonText != null ? etButtonText.getText().toString().trim() : "Ver detalles completos",
                    desc,
                    "#E42A67",
                    1
            );
        }

        newsItem.title = title;
        newsItem.description = desc;
        newsItem.shortDescription = desc.length() > 80 ? desc.substring(0, 80) + "..." : desc;
        newsItem.tags = etTags != null ? etTags.getText().toString().trim() : "#COLUAVerde";
        newsItem.subtitle = etButtonText != null ? etButtonText.getText().toString().trim() : "Ver detalles completos";
        newsItem.targetSectionId = etButtonAction != null ? etButtonAction.getText().toString().trim() : "";
        newsItem.isFeatured = cbIsFeatured != null && cbIsFeatured.isChecked();
        newsItem.isDraft = isDraft;
        newsItem.isVisible = true;

        if (etShareAppUrl != null) {
            String customShareUrl = etShareAppUrl.getText().toString().trim();
            if (!customShareUrl.isEmpty()) {
                final String finalShareUrl = customShareUrl;
                new Thread(() -> repository.setGlobalConfig("share_app_url", finalShareUrl)).start();
            }
        }
        newsItem.updatedAt = System.currentTimeMillis();

        if (!photoList.isEmpty()) {
            newsItem.imagePath = photoList.get(0);
            newsItem.photosJson = photoList.toString();
        } else {
            newsItem.imagePath = "";
            newsItem.photosJson = "";
        }

        if (!isDraft) {
            newsItem.publicationDate = System.currentTimeMillis();
        }

        getSharedPreferences("ConfigSyncPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_unpublished_changes", true)
                .apply();

        new Thread(() -> {
            try {
                repository.insertItem(newsItem);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String msg = isDraft ? "Borrador de noticia guardado" : "¡Noticia publicada exitosamente en el muro!";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Error al guardar noticia: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
