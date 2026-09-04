package com.example.coluainformativa.ui.agencias;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coluainformativa.R;
import com.example.coluainformativa.database.AgenciaEntity;

import java.util.List;

public class AgenciaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items;
    private boolean isEditMode = false;

    public AgenciaAdapter(List<Object> items) {
        this.items = items;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agencia_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agencia_card, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText((String) items.get(position));
        } else {
            AgenciaEntity agencia = (AgenciaEntity) items.get(position);
            ItemViewHolder vh = (ItemViewHolder) holder;
            vh.tvNombre.setText(agencia.nombre != null ? agencia.nombre : "Agencia");
            vh.tvDepto.setText(agencia.departamento != null ? agencia.departamento : "");
            vh.tvDepto.setTextColor(vh.itemView.getContext().getResources().getColor(R.color.colua_pink));
            vh.tvDepto.setVisibility(View.VISIBLE);
            vh.tvDireccion.setText(agencia.direccion != null ? agencia.direccion : "Sin dirección");
            vh.tvTelefono.setText(agencia.telefono != null ? agencia.telefono : "---");
            
            vh.btnEdit.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            vh.btnEdit.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(vh.itemView.getContext(), com.example.coluainformativa.AdminAgenciaEditActivity.class);
                intent.putExtra("extra_agencia_id", agencia.id);
                vh.itemView.getContext().startActivity(intent);
            });

            // Acción de Google Maps
            vh.itemView.findViewById(R.id.btn_google_maps).setOnClickListener(v -> {
                if (agencia.mapUrl != null && !agencia.mapUrl.isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(agencia.mapUrl));
                    vh.itemView.getContext().startActivity(intent);
                } else {
                    // Fallback: buscar nombre + direccion
                    String nombre = agencia.nombre != null ? agencia.nombre : "";
                    String direccion = agencia.direccion != null ? agencia.direccion : "";
                    String query = android.net.Uri.encode(nombre + " " + direccion);
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + query));
                    vh.itemView.getContext().startActivity(intent);
                }
            });
            
            try {
                int color = Color.parseColor(agencia.colorHex);
                vh.sideBorder.setBackgroundColor(color);
                vh.tvDepto.setTextColor(color);
            } catch (Exception e) {
                // Default color
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_agencia_group_header);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        View sideBorder, btnEdit;
        TextView tvNombre, tvDepto, tvDireccion, tvTelefono;

        ItemViewHolder(View v) {
            super(v);
            sideBorder = v.findViewById(R.id.side_border_agencia);
            btnEdit = v.findViewById(R.id.btn_edit_agencia);
            tvNombre = v.findViewById(R.id.tv_agencia_nombre);
            tvDepto = v.findViewById(R.id.tv_agencia_depto);
            tvDireccion = v.findViewById(R.id.tv_agencia_direccion);
            tvTelefono = v.findViewById(R.id.tv_agencia_telefono);
        }
    }
}
