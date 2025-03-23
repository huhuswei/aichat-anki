package com.ss.aianki;

/**
 * Created by liao on 2017/4/27.
 */

public class Constant {

    public enum DarkMode {
        MODE_NIGHT_FOLLOW_SYSTEM(R.string.dark_mode_system),
        MODE_NIGHT_NO(R.string.dark_mode_light),
        MODE_NIGHT_YES(R.string.dark_mode_dark);

        private int nameId;
        DarkMode(int nameId) {
            this.nameId = nameId;
        }

        public int getNameId() {
            return nameId;
        }
    }

    public static final int REQUEST_CODE_ANKI = 0;
}
