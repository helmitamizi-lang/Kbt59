package com.gathin.tvhome;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static MainActivity sInstance;
    private ImageView btnAllApps;
    public String testWeatherOverride = null;

    public static MainActivity getInstance() {
        return sInstance;
    }

    public static boolean isForeground() {
        return sInstance != null;
    }

    public static void openSettingsFromGlobal() {
        if (sInstance != null) {
            // Open settings layout or dialog
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        setContentView(R.layout.activity_main);
        setupDock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    private void setupDock() {
        btnAllApps = findViewById(R.id.btnAllApps);
        if (btnAllApps != null) {
            btnAllApps.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AppsActivity.class);
                startActivity(intent);
            });
        }

        View dock1 = findViewById(R.id.dockApp1);
        if (dock1 != null) {
            dock1.setOnClickListener(v -> launchAppByPackage("com.google.android.apps.maps"));
        }
        View dock2 = findViewById(R.id.dockApp2);
        if (dock2 != null) {
            dock2.setOnClickListener(v -> launchAppByPackage("com.spotify.music"));
        }
    }

    private void launchAppByPackage(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    @Override
    public void onBackPressed() {
        // Prevent launcher from exiting on back press
    }

    // ------------------------------------------------------------------------
    // COMPATIBILITY STUBS REQUIRED BY TV HOME HELPER CLASSES
    // ------------------------------------------------------------------------
    public void reloadTiles() {}
    public void updateTopBarUserIcon() {}
    public void showCustomWallpaperManagerDialog() {}
    public boolean isIslamicShelfEnabled() { return false; }
    public void showAyah(Object ayah) {}
    public void updateAyah() {}
    public boolean isWeatherWidgetEnabled() { return true; }
    public void showSoundEffectsDialog() {}
    public void showAzanSettingsDialog() {}
    public void showGeminiAssistantDialog() {}
    public void showCleanTvCacheDialog() {}
    public void showWallpaperDialog() {}
    public void showWallpaperIntervalDialog() {}
    public void showLanguageDialog() {}
    public void updateWeatherOverlay(Object line) {}
    public void showNightstandScreensaver() {}
    public void showRestartDialog() {}
    public void showTurnOffDialog() {}
    public String getAppVersionName() { return "2.0-MEKXZ"; }
    public void showAutoUpdateDialog() {}
    public void setTestWeatherOverride(String cond) { this.testWeatherOverride = cond; }
    public void refreshLocationDependentData() {}
    public void updateSettingsBadge() {}
}
