package com.gathin.tvhome;

import android.graphics.drawable.Drawable;

public class AppInfo {
    CharSequence name;
    CharSequence packageName;
    Drawable icon;

    public AppInfo(CharSequence name, CharSequence packageName, Drawable icon) {
        this.name = name;
        this.packageName = packageName;
        this.icon = icon;
    }
}
