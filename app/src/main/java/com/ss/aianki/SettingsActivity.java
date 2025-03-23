package com.ss.aianki;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SettingsActivity extends AppCompatActivity {
    private AIConfigManager configManager;
    private ServerConfigAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        configManager = new AIConfigManager(this);

        RecyclerView serverList = findViewById(R.id.serverList);
        serverList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ServerConfigAdapter(configManager.getAllConfigs(), this::showEditDialog, this::deleteConfig);
        serverList.setAdapter(adapter);

        findViewById(R.id.addServerBtn).setOnClickListener(v -> showEditDialog(new AIServerConfig()));
    }

    private void showEditDialog(AIServerConfig config) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_config, null);
        
        EditText nameEdit = dialogView.findViewById(R.id.serverName);
        EditText urlEdit = dialogView.findViewById(R.id.baseUrl);
        EditText keyEdit = dialogView.findViewById(R.id.apiKey);
        EditText modelsEdit = dialogView.findViewById(R.id.models);
        EditText tempEdit = dialogView.findViewById(R.id.temperature);

        nameEdit.setText(config.getName());
        urlEdit.setText(config.getBaseUrl());
        keyEdit.setText(config.getApiKey());
        modelsEdit.setText(config.getModels());
        tempEdit.setText(String.valueOf(config.getTemperature()));

        new AlertDialog.Builder(this)
            .setTitle(config.getId() == null ? "添加服务器" : "编辑服务器")
            .setView(dialogView)
            .setPositiveButton("保存", (dialog, which) -> {
                config.setName(nameEdit.getText().toString());
                config.setBaseUrl(urlEdit.getText().toString());
                config.setApiKey(keyEdit.getText().toString());
                config.setModels(modelsEdit.getText().toString());
                try {
                    config.setTemperature(Float.parseFloat(tempEdit.getText().toString()));
                } catch (NumberFormatException e) {
                    config.setTemperature(0.7f);
                }
                
                configManager.saveConfig(config);
                updateList();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteConfig(AIServerConfig config) {
        new AlertDialog.Builder(this)
            .setTitle("删除服务器")
            .setMessage("确定要删除这个服务器配置吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                configManager.deleteConfig(config.getId());
                updateList();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void updateList() {
        adapter.updateData(configManager.getAllConfigs());
    }
} 