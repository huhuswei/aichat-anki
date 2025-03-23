package com.ss.aianki;

import static com.ichi2.anki.api.AddContentApi.READ_WRITE_PERMISSION;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class AnkiDroidHelper {
    private static final String ANKI_PACKAGE_NAME = "com.ichi2.anki";
    private static final String ANKI_CONTENT_PROVIDER_AUTHORITY = "com.ichi2.anki.flashcards";
    
    /**
     * 删除指定ID的笔记
     * @param context 上下文
     * @param noteId 笔记ID
     * @return 是否删除成功
     */
    public static boolean deleteNote(Context context, long noteId) {
        try {
            // 先检查 AnkiDroid 是否已安装
            if (!isAnkiDroidInstalled(context)) {
                Toast.makeText(context, "请先安装 AnkiDroid", Toast.LENGTH_LONG).show();
                openPlayStore(context, ANKI_PACKAGE_NAME);
                return false;
            }
            
            // 检查是否有权限
            if (!hasPermission(context)) {
                Toast.makeText(context, "请授予 AnkiDroid 权限", Toast.LENGTH_LONG).show();
                return false;
            }
            
            ContentResolver cr = context.getContentResolver();
            if (cr == null) return false;
            
            Uri noteUri = Uri.parse("content://" + ANKI_CONTENT_PROVIDER_AUTHORITY + "/notes/" + noteId);
            int result = cr.delete(noteUri, null, null);
            return result == 1;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    
    /**
     * 检查是否安装了AnkiDroid
     * @param context 上下文
     * @return 是否已安装
     */
    public static boolean isAnkiDroidInstalled(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(ANKI_PACKAGE_NAME, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查是否有权限访问AnkiDroid
     * @param context 上下文
     * @return 是否有权限
     */
    public static boolean hasPermission(Context context) {
        return context.checkCallingOrSelfPermission("com.ichi2.anki.permission.READ_WRITE_DATABASE") 
            == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 打开Google Play商店安装AnkiDroid
     * @param context 上下文
     * @param packageName 包名
     */
    public static void openPlayStore(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // 如果没有安装Google Play，则打开浏览器
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    
    /**
     * 获取 AnkiDroid 版本号
     * @param context 上下文
     * @return 版本号，如果获取失败则返回 -1
     */
    public static int getAnkiDroidVersion(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo = pm.getPackageInfo(ANKI_PACKAGE_NAME, 0);
            return pInfo.versionCode;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Request permission from the user to access the AnkiDroid API (for SDK 23+)
     *
     * @param callbackActivity An Activity which implements onRequestPermissionsResult()
     * @param callbackCode     The callback code to be used in onRequestPermissionsResult()
     */
    public static void requestPermission(Activity callbackActivity, int callbackCode) {
        ActivityCompat.requestPermissions(callbackActivity, new String[]{READ_WRITE_PERMISSION}, callbackCode);
    }
} 