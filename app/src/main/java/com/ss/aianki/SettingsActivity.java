package com.ss.aianki;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SettingsActivity extends AppCompatActivity {
    private AIConfigManager configManager;
    private ServerConfigAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        DarkModeUtils.applyImmersiveStatusBar(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
        Spinner providerSpinner = dialogView.findViewById(R.id.providerSpinner);

        nameEdit.setText(config.getName());
        urlEdit.setText(config.getBaseUrl());
        keyEdit.setText(config.getApiKey());
        modelsEdit.setText(config.getModels());
        tempEdit.setText(String.valueOf(config.getTemperature()));

        // 设置服务商选择器
        String[] providers = {"openai", "azure", "gemini", "claude", "deepseek", "custom"};
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, providers);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(providerAdapter);

        String currentProvider = config.getProvider();
        for (int i = 0; i < providers.length; i++) {
            if (providers[i].equals(currentProvider)) {
                providerSpinner.setSelection(i);
                break;
            }
        }

        // 选择服务商时，如果是新增服务器则自动填充官方地址
        boolean isNewConfig = config.getId() == null;
        providerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!isNewConfig) return; // 编辑已有服务器时不自动填充
                String provider = providers[position];
                if (!provider.equals("custom")) {
                    String baseUrl = getDefaultBaseUrl(provider);
                    if (baseUrl != null) {
                        urlEdit.setText(baseUrl);
                    }
                } else {
                    urlEdit.setText("");
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(config.getId() == null ? "添加服务器" : "编辑服务器")
                .setView(dialogView)
                .create();

        dialog.show();

        // 使用布局中的按钮
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSave);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnFetchModels = dialogView.findViewById(R.id.btnFetchModels);

        btnSave.setOnClickListener(v -> {
            config.setName(nameEdit.getText().toString());
            String baseUrl = urlEdit.getText().toString();
            config.setBaseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
            config.setApiKey(keyEdit.getText().toString());
            config.setModels(modelsEdit.getText().toString());
            config.setProvider(providerSpinner.getSelectedItem().toString());
            try {
                config.setTemperature(Float.parseFloat(tempEdit.getText().toString()));
            } catch (NumberFormatException e) {
                config.setTemperature(0.7f);
            }

            configManager.saveConfig(config);
            updateList();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnFetchModels.setOnClickListener(v -> {
            // 拉取模型清单的逻辑
            String baseUrl = urlEdit.getText().toString().trim();
            String apiKey = keyEdit.getText().toString().trim();

            if (baseUrl.isEmpty()) {
                ToastUtil.show(this, "请先填写 API 地址");
                return;
            }

            // 禁用按钮，防止重复点击
            btnFetchModels.setEnabled(false);
            btnFetchModels.setText("拉取");

            // 用变量在闭包内持有引用，确保 finally 里能恢复
            final MaterialButton fetchBtn = btnFetchModels;

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
                                if (i > 0) modelsBuilder.append(";\n");
                                modelsBuilder.append(model.getString("id"));
                            }

                            String modelsList = modelsBuilder.toString();
                            runOnUiThread(() -> {
                                modelsEdit.setText(modelsList);
                                ToastUtil.show(this, "获取成功");
                            });
                        } else {
                            runOnUiThread(() -> {
                                ToastUtil.show(this, "获取失败: " + response.code());
                            });
                        }
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        ToastUtil.show(this, "错误: " + e.getMessage());
                    });
                } finally {
                    runOnUiThread(() -> {
                        fetchBtn.setEnabled(true);
                        fetchBtn.setText("模型");
                    });
                }
            }).start();
        });
    }

    private void deleteConfig(AIServerConfig config) {
        new MaterialAlertDialogBuilder(this)
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

    private String getDefaultBaseUrl(String provider) {
        switch (provider) {
            case "openai":
                return "https://api.openai.com";
            case "azure":
                return "https://YOUR_RESOURCE_NAME.openai.azure.com";
            case "gemini":
                return "https://generativelanguage.googleapis.com";
            case "claude":
                return "https://api.anthropic.com";
            case "deepseek":
                return "https://api.deepseek.com";
            default:
                return null;
        }
    }
} 