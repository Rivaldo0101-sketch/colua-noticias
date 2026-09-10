package com.example.coluainformativa.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.AdminNewsEditActivity;
import com.example.coluainformativa.AdminSectionEditActivity;
import com.example.coluainformativa.R;
import com.example.coluainformativa.security.AdminAuthManager;

public class DialogHelper {

    public interface OnResourceSelectedListener {
        void onResourceSelected(String resourceName, String displayLabel);
    }

    public static void showPasswordConfirmationDialog(Context context, AdminAuthManager authManager, String title, String message, Runnable onSuccess) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_admin_login, null);
        EditText etPassword = view.findViewById(R.id.et_admin_password);
        Button btnConfirm = view.findViewById(R.id.btn_login);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        if (btnConfirm != null) btnConfirm.setText("CONFIRMAR Y ELIMINAR");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String pass = etPassword != null ? etPassword.getText().toString() : "";
                if (authManager != null && authManager.checkPassword(pass)) {
                    dialog.dismiss();
                    onSuccess.run();
                } else {
                    Toast.makeText(context, "Clave de administrador incorrecta", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    public static void showUnsavedChangesDialog(Context context, Runnable onSaveDraft, Runnable onDiscard) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_light_report, null);
        TextView tvTitle = view.findViewById(R.id.tv_report_title);
        TextView tvContent = view.findViewById(R.id.tv_report_content);
        Button btnPrimary = view.findViewById(R.id.btn_report_primary);
        Button btnSecondary = view.findViewById(R.id.btn_report_secondary);

        if (tvTitle != null) tvTitle.setText("Hay cambios sin guardar");
        if (tvContent != null) tvContent.setText("Existen cambios o borradores en el portal que no han sido publicados a la nube. ¿Desea guardarlos como borrador antes de salir?");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnPrimary != null) {
            btnPrimary.setText("Guardar borrador");
            btnPrimary.setOnClickListener(v -> {
                dialog.dismiss();
                if (onSaveDraft != null) onSaveDraft.run();
            });
        }

        if (btnSecondary != null) {
            btnSecondary.setText("Descartar cambios");
            btnSecondary.setOnClickListener(v -> {
                dialog.dismiss();
                if (onDiscard != null) onDiscard.run();
            });
        }

        dialog.show();
    }

    public static void showConfirmExitDialog(Context context, Runnable onExit) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_light_report, null);
        TextView tvTitle = view.findViewById(R.id.tv_report_title);
        TextView tvContent = view.findViewById(R.id.tv_report_content);
        Button btnPrimary = view.findViewById(R.id.btn_report_primary);
        Button btnSecondary = view.findViewById(R.id.btn_report_secondary);

        if (tvTitle != null) tvTitle.setText("¿Cerrar sesión administrativa?");
        if (tvContent != null) tvContent.setText("Saldrás del Portal Administrativo y será necesario iniciar sesión nuevamente para volver a entrar.");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnPrimary != null) {
            btnPrimary.setText("Cerrar sesión");
            btnPrimary.setOnClickListener(v -> {
                dialog.dismiss();
                if (onExit != null) onExit.run();
            });
        }

        if (btnSecondary != null) {
            btnSecondary.setText("Cancelar");
            btnSecondary.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    public static void showResourcePickerDialog(Context context, OnResourceSelectedListener listener, Runnable onUploadDeviceClick) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_select_resource_picker, null);

        View cardImages = dialogView.findViewById(R.id.card_option_app_images);
        View cardIcons = dialogView.findViewById(R.id.card_option_app_icons);
        View cardDevice = dialogView.findViewById(R.id.card_option_upload_device);
        View cardRemove = dialogView.findViewById(R.id.card_option_remove_resource);
        View btnClose = dialogView.findViewById(R.id.btn_close_resource_dialog);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // Opción 1: Galería de imágenes (PNGs con miniatura visual)
        if (cardImages != null) {
            cardImages.setOnClickListener(v -> {
                dialog.dismiss();
                showAppImagesGridGalleryDialog(context, listener);
            });
        }

        // Opción 2: Galería de íconos (Vectores e íconos con miniatura visual)
        if (cardIcons != null) {
            cardIcons.setOnClickListener(v -> {
                dialog.dismiss();
                showAppIconsGridGalleryDialog(context, listener);
            });
        }

        // Opción 3: Subir desde el dispositivo (LOCAL)
        if (cardDevice != null) {
            cardDevice.setOnClickListener(v -> {
                dialog.dismiss();
                if (onUploadDeviceClick != null) {
                    onUploadDeviceClick.run();
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        context.startActivity(Intent.createChooser(intent, "Seleccionar foto local"));
                    } catch (Exception e) {
                        Toast.makeText(context, "No se pudo abrir el selector de fotos local", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // Opción 4: Quitar recurso actual
        if (cardRemove != null) {
            cardRemove.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    listener.onResourceSelected("distintivo_colua", "Por Defecto");
                }
                Toast.makeText(context, "Recurso restablecido por defecto", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.show();
    }

    public static void showAppImagesGridGalleryDialog(Context context, OnResourceSelectedListener listener) {
        String[] imageResources = {
                "logo_composite", "distintivo_colua", "micoope_enlinea", "logo_fri",
                "noticias_colua", "sostenibilidad_cooperativa", "grupo", "public_service",
                "ahorros", "credito", "seguro", "ubicacion", "servicios_digitales",
                "beneficios", "inicio", "perfil", "portal_administrativo",
                "ahorro_infanto_juvenil", "ahorro_programado", "credi_consumo", "credi_vehiculo",
                "credito_consumo", "credito_productivo", "credito_vivienda", "remesa",
                "seguro_de_cancer", "seguro_de_vida_individual_o_familar", "seguro_edad_de_oro"
        };
        showVisualResourceGridDialog(context, "Galería de Imágenes Oficiales", "Selecciona una imagen PNG de la app:", imageResources, listener);
    }

    public static void showAppIconsGridGalleryDialog(Context context, OnResourceSelectedListener listener) {
        String[] iconResources = {
                "ic_newspaper", "ic_star", "ic_person", "ic_verified", "ic_account_balance",
                "ic_savings", "ic_campaign", "ic_handshake", "ic_school", "ic_health",
                "ic_hospital", "ic_phone_android", "ic_groups", "ic_lightbulb", "ic_local_offer",
                "ic_location", "ic_security", "ic_lock", "ic_receipt", "ic_shopping_cart",
                "ic_business", "ic_calculate", "ic_event", "ic_store", "ic_support",
                "ic_trending_up", "ic_volunteer_activism", "ic_work", "ic_info"
        };
        showVisualResourceGridDialog(context, "Galería de Íconos y Vectores", "Selecciona un ícono temático para asignar:", iconResources, listener);
    }

    private static void showVisualResourceGridDialog(Context context, String titleStr, String subtitleStr, String[] resources, OnResourceSelectedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_grid_resource_picker, null);

        TextView tvTitle = view.findViewById(R.id.tv_grid_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_grid_subtitle);
        RecyclerView rvGrid = view.findViewById(R.id.rv_resource_grid);
        View btnClose = view.findViewById(R.id.btn_close_grid_dialog);
        Button btnCancel = view.findViewById(R.id.btn_cancel_grid);

        if (tvTitle != null) tvTitle.setText(titleStr);
        if (tvSubtitle != null) tvSubtitle.setText(subtitleStr);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (rvGrid != null) {
            rvGrid.setLayoutManager(new GridLayoutManager(context, 3));
            rvGrid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_resource_preview, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    String resName = resources[position];
                    ImageView ivThumb = holder.itemView.findViewById(R.id.iv_grid_thumb);
                    TextView tvLabel = holder.itemView.findViewById(R.id.tv_grid_label);

                    if (tvLabel != null) tvLabel.setText(resName);

                    if (ivThumb != null) {
                        int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
                        if (resId != 0) {
                            ivThumb.setImageResource(resId);
                            if (resName.startsWith("ic_")) {
                                ivThumb.setImageTintList(ColorStateList.valueOf(Color.parseColor("#173789")));
                            } else {
                                ivThumb.setImageTintList(null);
                            }
                        } else {
                            ivThumb.setImageResource(R.drawable.distintivo_colua);
                        }
                    }

                    holder.itemView.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onResourceSelected(resName, resName);
                        }
                    });
                }

                @Override
                public int getItemCount() {
                    return resources.length;
                }
            });
        }

        dialog.show();
    }

    private static void showImagePreviewDialog(Context context, String resName, Runnable onConfirm) {
        int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);
        layout.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(context);
        iv.setLayoutParams(new LinearLayout.LayoutParams(200, 200));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (resId != 0) iv.setImageResource(resId);
        else iv.setImageResource(R.drawable.ic_star);

        TextView tvName = new TextView(context);
        tvName.setText("Recurso: " + resName + ".png");
        tvName.setPadding(0, 16, 0, 0);
        tvName.setTextColor(Color.parseColor("#173789"));
        tvName.setTypeface(null, Typeface.BOLD);

        layout.addView(iv);
        layout.addView(tvName);

        new AlertDialog.Builder(context, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setTitle("Previsualizar Recurso")
                .setView(layout)
                .setPositiveButton("Usar este elemento", (dialog, which) -> onConfirm.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public static void showLightReportDialog(Context context, String title, String reportMessage, String primaryBtnLabel, Runnable onPrimaryClick, String secondaryBtnLabel) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_light_report, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_report_title);
        TextView tvContent = dialogView.findViewById(R.id.tv_report_content);
        Button btnPrimary = dialogView.findViewById(R.id.btn_report_primary);
        Button btnSecondary = dialogView.findViewById(R.id.btn_report_secondary);

        if (tvTitle != null) tvTitle.setText(title != null ? title : "Informe del Sistema");
        if (tvContent != null) tvContent.setText(reportMessage != null ? reportMessage : "");

        if (btnPrimary != null) {
            if (primaryBtnLabel != null && !primaryBtnLabel.isEmpty()) {
                btnPrimary.setText(primaryBtnLabel);
                btnPrimary.setVisibility(View.VISIBLE);
            } else {
                btnPrimary.setVisibility(View.GONE);
            }
        }

        if (btnSecondary != null) {
            btnSecondary.setText(secondaryBtnLabel != null ? secondaryBtnLabel : "Cerrar");
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnPrimary != null) {
            btnPrimary.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPrimaryClick != null) onPrimaryClick.run();
            });
        }

        if (btnSecondary != null) {
            btnSecondary.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
