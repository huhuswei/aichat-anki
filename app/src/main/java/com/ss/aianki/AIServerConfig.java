package com.ss.aianki;

import android.annotation.SuppressLint;
import android.os.SystemClock;

public class AIServerConfig {
    private String id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String models;  // 多个模型用分号分隔
    private float temperature;
    private String lastSelectedModel;  // 添加最后选择的模型字段

    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    public AIServerConfig() {
        // 修改默认值
        this.id = String.valueOf(System.currentTimeMillis());//System.currentTimeMillis());
        this.name = "硅基流动";
        this.baseUrl = "https://api.siliconflow.cn/";
        this.apiKey = "";
        this.models = "Qwen/QwQ-32B;"
                + "deepseek-ai/DeepSeek-R1;deepseek-ai/DeepSeek-V3;"
                + "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B;deepseek-ai/DeepSeek-R1-Distill-Qwen-14B;"
                + "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B;deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B";
        this.temperature = 0.6f;
        this.lastSelectedModel = null;  // 默认为 null
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    
    public String getModels() { return models; }
    public void setModels(String models) { this.models = models; }
    
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    
    public String getLastSelectedModel() { return lastSelectedModel; }
    public void setLastSelectedModel(String lastSelectedModel) { this.lastSelectedModel = lastSelectedModel; }
    
    public String[] getModelArray() {
        return models.split(";");
    }
} 