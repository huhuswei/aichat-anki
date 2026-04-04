package com.ss.aianki;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

        configManager = AIConfigManager.getInstance(MyApplication.getInstance());

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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(config.getId() == null ? "添加服务器" : "编辑服务器")
                .setView(dialogView)
                .setPositiveButton("保存", (dialogInterface, which) -> {
                    config.setName(nameEdit.getText().toString());
                    String baseUrl = urlEdit.getText().toString();
                    config.setBaseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
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
                .setNeutralButton("获取模型列表", null)  // 先设为 null，不设置监听
                .create();

        dialog.show();

        // 获取 NeutralButton 并手动设置点击事件
        Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        neutralButton.setOnClickListener(v -> {
            // 拉取模型清单的逻辑
            String baseUrl = urlEdit.getText().toString().trim();
            String apiKey = keyEdit.getText().toString().trim();

            if (baseUrl.isEmpty()) {
                ToastUtil.show(this, "请先填写 API 地址");
                return;
            }

            // 禁用按钮，防止重复点击
            neutralButton.setEnabled(false);
            neutralButton.setText("加载中...");

            // 在新线程中请求
            new Thread(() -> {
                try {
                    String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "v1/models" : baseUrl + "/v1/models";

                    okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                            .url(modelsUrl)
                            .get();

                    if (apiKey != null && !apiKey.isEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
                    }

                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build();

                    try (okhttp3.Response response = client.newCall(requestBuilder.build()).execute()) {
                        if (response.isSuccessful()) {
                            String body = response.body() != null ? response.body().string() : "";
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            org.json.JSONArray data = json.getJSONArray("data");

                            StringBuilder modelsBuilder = new StringBuilder();
                            for (int i = 0; i < data.length(); i++) {
                                org.json.JSONObject model = data.getJSONObject(i);
                                if (i > 0) modelsBuilder.append(";");
                                modelsBuilder.append(model.getString("id"));
                            }

                            String modelsList = modelsBuilder.toString();
                            runOnUiThread(() -> {
                                modelsEdit.setText(modelsList);
                                ToastUtil.show(this, "获取成功");
                                neutralButton.setEnabled(true);
                                neutralButton.setText("拉模型清单");
                            });
                        } else {
                            runOnUiThread(() -> {
                                ToastUtil.show(this, "获取失败: " + response.code());
                                neutralButton.setEnabled(true);
                                neutralButton.setText("拉模型清单");
                            });
                        }
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        ToastUtil.show(this, "错误: " + e.getMessage());
                        neutralButton.setEnabled(true);
                        neutralButton.setText("拉模型清单");
                    });
                }
            }).start();
        });
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