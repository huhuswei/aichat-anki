package com.ss.aianki;

/**
 * Created by liao on 2017/4/13.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 单例，getInstance()得到实例
 */
public class Settings {

    private static Settings settings = null;

    private final static String PREFER_NAME = "settings";    //应用设置名称
    public final static String DARK_MODE_INDEX = "dark_modea_index";


    private SharedPreferences sp;
    private SharedPreferences.Editor editor;


    private Settings(Context context) {
        sp = context.getSharedPreferences(PREFER_NAME, Context.MODE_PRIVATE);
        editor = sp.edit();
    }

    /**
     * 获得单例
     *
     * @return
     */
    public static Settings getInstance(Context context) {
        if (settings == null) {
            settings = new Settings(context);
        }
        return settings;
    }

    public SharedPreferences getSharedPreferences() {
        return sp;
    }
    /*************/

    public boolean put(String TAG, String value) {
        editor.putString(TAG, value);
        editor.commit();
        return true;
    }

    public boolean put(String TAG, boolean value) {
        editor.putBoolean(TAG, value);
        editor.commit();
        return true;
    }

    public boolean put(String TAG, int value) {
        editor.putInt(TAG, value);
        editor.commit();
        return true;
    }

    public String get(String TAG, String defaultValue) {
        return sp.getString(TAG, defaultValue);
    }

    public boolean get(String TAG, boolean defaultValue) {
        return sp.getBoolean(TAG, defaultValue);
    }

    public int get(String TAG, int defaultValue) {
        return sp.getInt(TAG, defaultValue);
    }

}