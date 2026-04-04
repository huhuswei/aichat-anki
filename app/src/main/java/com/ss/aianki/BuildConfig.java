package com.ss.aianki;

/**
 * @ProjectName: aianki
 * @Package: com.ss.aianki
 * @ClassName: BuildConfig
 * @Description: 模拟编译宏
 * @Author: ss
 * @CreateDate: 2025/8/27 20:21 AM
 * @UpdateUser: 更新者
 * @UpdateDate: 2025/8/27 20:21 AM
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 */

public final class BuildConfig {
    public final static boolean isDebug = false;
    public final static boolean isTracing = true;

    public static final boolean DEBUG = Boolean.parseBoolean("false");
    public static final String APPLICATION_ID = Constant.AIANKI_PACKAGE_NAME;
    public static final String BUILD_TYPE = "debug";
    public static final int VERSION_CODE = 1;
    public static final String VERSION_NAME = "1.0";
}
