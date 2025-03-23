package com.ss.aianki;

//import android.app.Activity;
//import androidx.appcompat.app.AppCompatActivity;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDexApplication;

import com.jakewharton.threetenabp.AndroidThreeTen;

import okhttp3.OkHttpClient;

/**
 * Created by liao on 2017/4/27.
 */

public class MyApplication extends MultiDexApplication {
    private static Context context;
    private static Application application;
    private static OkHttpClient okHttpClient;
    private static MyApplication instance;
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        context = getApplicationContext();
        application = this;
        DarkModeUtils.init(this);
        AndroidThreeTen.init(this);
    }

    public static Context getContext() {
        return context;
    }

    public static Application getApplication(){
        return application;
    }

//    public static AnkiDroidHelper getAnkiDroid() {
//        if (mAnkiDroid == null) {
//            mAnkiDroid = new AnkiDroidHelper(getApplication());
//        }
//        return mAnkiDroid;
//    }


    public static OkHttpClient getOkHttpClient(){
        if(okHttpClient == null){
            okHttpClient = new OkHttpClient();
        }
        return okHttpClient;
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public static void initRunningAndroid() {

    }

}
