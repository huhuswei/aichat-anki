package com.ss.aianki;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class AIConfigManager {
    private static final String PREF_NAME = "ai_config";
    private static final String KEY_CONFIGS = "configs";
    private static final String KEY_CURRENT = "current_config";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public AIConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        
        // 如果没有配置，添加默认配置
        if (getAllConfigs().isEmpty()) {
            AIServerConfig defaultConfig = new AIServerConfig();
            defaultConfig.setName("默认服务器");
            saveConfig(defaultConfig);
            setCurrentConfig(defaultConfig.getId());
        }
    }
    
    public void saveConfig(AIServerConfig config) {
        List<AIServerConfig> configs = getAllConfigs();
        // 更新或添加配置
        boolean found = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(config.getId())) {
                configs.set(i, config);
                found = true;
                break;
            }
        }
        if (!found) {
            configs.add(config);
        }
        
        prefs.edit()
            .putString(KEY_CONFIGS, gson.toJson(configs))
            .apply();
    }
    
    public List<AIServerConfig> getAllConfigs() {
        String json = prefs.getString(KEY_CONFIGS, "[]");
        return gson.fromJson(json, new TypeToken<List<AIServerConfig>>(){}.getType());
    }
    
    public void deleteConfig(String configId) {
        List<AIServerConfig> configs = getAllConfigs();
        configs.removeIf(config -> config.getId().equals(configId));
        prefs.edit()
            .putString(KEY_CONFIGS, gson.toJson(configs))
            .apply();
    }
    
    public void setCurrentConfig(String configId) {
        prefs.edit()
            .putString(KEY_CURRENT, configId)
            .apply();
    }
    
    public AIServerConfig getCurrentConfig() {
        String currentId = prefs.getString(KEY_CURRENT, null);
        if (currentId == null) return null;
        
        List<AIServerConfig> configs = getAllConfigs();
        for (AIServerConfig config : configs) {
            if (config.getId().equals(currentId)) {
                return config;
            }
        }
        return null;
    }
} 