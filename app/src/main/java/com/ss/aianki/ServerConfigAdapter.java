package com.ss.aianki;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ServerConfigAdapter extends RecyclerView.Adapter<ServerConfigAdapter.ViewHolder> {
    private List<AIServerConfig> configs;
    private final OnConfigClickListener editListener;
    private final OnConfigClickListener deleteListener;

    public interface OnConfigClickListener {
        void onConfigClick(AIServerConfig config);
    }

    public ServerConfigAdapter(List<AIServerConfig> configs, 
                             OnConfigClickListener editListener,
                             OnConfigClickListener deleteListener) {
        this.configs = configs;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AIServerConfig config = configs.get(position);
        holder.text1.setText(config.getName());
        holder.text2.setText(config.getBaseUrl());
        
        holder.itemView.setOnClickListener(v -> editListener.onConfigClick(config));
        holder.itemView.setOnLongClickListener(v -> {
            deleteListener.onConfigClick(config);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    public void updateData(List<AIServerConfig> newConfigs) {
        this.configs = newConfigs;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1;
        TextView text2;

        ViewHolder(View view) {
            super(view);
            text1 = view.findViewById(android.R.id.text1);
            text2 = view.findViewById(android.R.id.text2);
        }
    }
} 