package com.ss.aianki;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
/**
 * @ProjectName: ankihelper
 * @Package: com.mmjang.ankihelper.util
 * @ClassName: DarkModeUtils
 * @Description: java类作用描述
 * @Author: ss
 * @CreateDate: 2022/10/26 10:42 PM
 * @UpdateUser: 更新者
 * @UpdateDate: 2022/10/26 10:42 PM
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */
public class DarkModeUtils {

    public static final String KEY_CURRENT_MODEL = "night_mode_state_sp";

    private static int getNightModel(Context context) {
        Settings settings = Settings.getInstance(MyApplication.getContext());
        return settings.get(KEY_CURRENT_MODEL, AppCompatDelegate.MODE_NIGHT_YES);
    }

    public static void setNightModel(Context context, int nightMode) {
        Settings settings = Settings.getInstance(context);
        settings.put(KEY_CURRENT_MODEL, nightMode);
    }

    /**
     * ths method should be called in Application onCreate method
     *
     * @param application application
     */
    public static void init(Application application) {
        int nightMode = getNightModel(application);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /**
     * 应用夜间模式
     */
    public static void applyNightMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setNightModel(context, AppCompatDelegate.MODE_NIGHT_YES);
    }

    /**
     * 应用日间模式
     */
    public static void applyDayMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setNightModel(context, AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * 跟随系统主题时需要动态切换
     */
    public static void applySystemMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setNightModel(context, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * 判断App当前是否处于暗黑模式状态
     *
     * @param context 上下文
     * @return 返回
     */
    public static boolean isDarkMode(Context context) {
        int nightMode = getNightModel(context);
        if (nightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            int applicationUiMode = context.getResources().getConfiguration().uiMode;
            int systemMode = applicationUiMode & Configuration.UI_MODE_NIGHT_MASK;
            return systemMode == Configuration.UI_MODE_NIGHT_YES;
        } else {
            return nightMode == AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public static void darkModeSettingDialog(Context activityContext) {
//        LinkedHashMap<String, Integer> nightModeMap = new LinkedHashMap<>();
//        nightModeMap.put("MODE_NIGHT_FOLLOW_SYSTEM", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
//        nightModeMap.put("MODE_NIGHT_NO", AppCompatDelegate.MODE_NIGHT_NO);
//        nightModeMap.put("MODE_NIGHT_FOLLOW_SYSTEM", AppCompatDelegate.MODE_NIGHT_YES);

        Settings settings = Settings.getInstance(activityContext);
        int checkedIndex = settings.get(Settings.DARK_MODE_INDEX, 0);

        String[] modeNameArr = new String[Constant.DarkMode.values().length];
        for(int index=0; index < Constant.DarkMode.values().length; index++) {
            modeNameArr[index] = activityContext.getResources().getString(Constant.DarkMode.values()[index].getNameId());
        }

        boolean[] isCheckedArr = new boolean[modeNameArr.length];

        for(int i = 0; i < isCheckedArr.length; i++) {
            if(i == checkedIndex)
                isCheckedArr[i] = true;
            else
                isCheckedArr[i] = false;
        }

        MaterialAlertDialogBuilder multiChoiceDialog = new MaterialAlertDialogBuilder(activityContext);
        multiChoiceDialog.setTitle("暗黑模式");
        multiChoiceDialog.setSingleChoiceItems(modeNameArr, checkedIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Constant.DarkMode mode = Constant.DarkMode.values()[which];
                        switch(mode) {
                            case MODE_NIGHT_FOLLOW_SYSTEM:
                                applySystemMode(activityContext);
                                break;
                            case MODE_NIGHT_NO:
                                applyDayMode(activityContext);
                                break;
                            case MODE_NIGHT_YES:
                                applyNightMode(activityContext);
                                break;
                        }
                        settings.put(Settings.DARK_MODE_INDEX, which);
                        Intent intent = new Intent(activityContext, MainActivity.class);
                        ComponentName cn = intent.getComponent();
                        Intent mainIntent = Intent.makeRestartActivityTask(cn);
                        ((Activity) activityContext).startActivity(mainIntent);
                        dialog.dismiss();
                    }
                });
        multiChoiceDialog.show();
    }


    /**
     * 设置沉浸式状态栏/导航栏。
     *
     * 对于有 Toolbar 的 Activity，状态栏背景使用 colorPrimary 与 Toolbar 融为一体。
     * 对于全屏 Activity（如 MainActivity），使用透明状态栏实现 Edge-to-Edge。
     *
     * @param activity   目标 Activity
     * @param transparent true = 透明状态栏（Edge-to-Edge），false = colorPrimary 背景
     */
    public static void applyImmersiveStatusBar(Activity activity, boolean transparent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean isDark = isDarkMode(activity);
            int statusBarColor;
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

            if (transparent) {
                // Edge-to-Edge：透明状态栏，布局延伸到状态栏后面
                statusBarColor = android.graphics.Color.TRANSPARENT;
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                // 注意：不使用 LAYOUT_HIDE_NAVIGATION，否则 adjustResize 会失效
            } else {
                // 有 Toolbar：使用 colorPrimary 与 Toolbar 保持一致
                android.util.TypedValue typedValue = new android.util.TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
                statusBarColor = typedValue.data;
            }

            activity.getWindow().setStatusBarColor(statusBarColor);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isDark) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!isDark) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** 重载：默认不透明（有 Toolbar 的 Activity 使用） */
    public static void applyImmersiveStatusBar(Activity activity) {
        applyImmersiveStatusBar(activity, false);
    }

    public static void initDarkMode(Context activityContext) {
        Settings settings = Settings.getInstance(activityContext);
        Constant.DarkMode mode = Constant.DarkMode.values()[settings.get(Settings.DARK_MODE_INDEX, 0)];

        switch(mode) {
            case MODE_NIGHT_FOLLOW_SYSTEM:
                applySystemMode(activityContext);
                break;
            case MODE_NIGHT_NO:
                applyDayMode(activityContext);
                break;
            case MODE_NIGHT_YES:
                applyNightMode(activityContext);
                break;
        }
    }
}

