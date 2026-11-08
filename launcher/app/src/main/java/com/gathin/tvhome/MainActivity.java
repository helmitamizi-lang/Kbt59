package com.gathin.tvhome;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.app.AlertDialog;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.content.SharedPreferences;
import java.io.InputStream;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static MainActivity sInstance;
    private View btnAllApps;
    private static final int PICK_WALLPAPER = 501;
    private TextView mediaTitle, mediaArtist;
    private HeadUnitRemote remote;
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
        loadWallpaper();
        setupMedia();
        TextView prayer = findViewById(R.id.prayerText);
        prayer.setText("Memuat waktu solat…");
        Switch azan = findViewById(R.id.azanSwitch);
        azan.setChecked(Azan.isMasterEnabled(this));
        azan.setOnCheckedChangeListener((button, enabled) -> {
            Azan.setMasterEnabled(this, enabled);
            Azan.schedule(this, PrayerTimes.getJakimZone(this));
        });
        refreshPrayers();
        PrayerTimes.syncIfDue(this, this::refreshPrayers);
        remote = new HeadUnitRemote(this);
        remote.start();
    }

    @Override
    protected void onDestroy() {
        if (remote != null) remote.stop();
        super.onDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    private void refreshPrayers() {
        PrayerTimes.Day day = PrayerTimes.day(this, PrayerTimes.getJakimZone(this), java.util.Calendar.getInstance());
        TextView prayer = findViewById(R.id.prayerText);
        if (day == null) {
            prayer.setText("Jadual tidak tersedia");
            return;
        }
        int[] indices = {0, 2, 3, 4, 5};
        StringBuilder text = new StringBuilder();
        for (int index : indices) {
            int m = day.minutes[index];
            text.append(PrayerTimes.NAMES[index]).append("     ")
                .append(String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60))
                .append("\\n");
        }
        prayer.setText(text.toString().trim());
        Azan.schedule(this, PrayerTimes.getJakimZone(this));
    }

    public void remoteAction(String command) {
        runOnUiThread(() -> {
            switch (command) {
                case "home": break;
                case "apps": startActivity(new Intent(this, AppsActivity.class)); break;
                case "settings": startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); break;
                case "previous": mediaAction(0); break;
                case "play": mediaAction(1); break;
                case "next": mediaAction(2); break;
                default: break;
            }
        });
    }

    private void setupDock() {
        btnAllApps = findViewById(R.id.btnAllApps);
        btnAllApps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        bindApp(R.id.dockApp1, "com.netflix.mediaclient");
        bindApp(R.id.dockApp2, "com.spotify.music");
        bindApp(R.id.dockApp3, "com.android.fmradio");
        bindApp(R.id.dockApp4, "com.google.android.youtube");
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                new AlertDialog.Builder(this).setTitle("MEKXZ DRIVE X")
                .setItems(new String[]{"Tukar Wallpaper", "Android Settings", "Notification Access", "Safari Remote"}, (d, which) -> {
                    if (which == 0) pickWallpaper();
                    if (which == 1) startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    if (which == 2) startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    if (which == 3) new AlertDialog.Builder(this).setTitle("Safari Remote")
                        .setMessage("Buka http://" + WallpaperUploadServer.getLocalIpAddress() + ":" + remote.getPort() + "\\nPIN: " + remote.getPin() + "\\nTelefon dan player mesti pada Wi-Fi sama.")
                        .setPositiveButton("OK", null).show();
                }).show());
    }

    private void bindApp(int id, String pkg) {
        findViewById(id).setOnClickListener(v -> launchAppByPackage(pkg));
    }

    private void launchAppByPackage(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Toast.makeText(this, "Aplikasi belum dipasang: " + pkg, Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(launch); }
        catch (Exception e) { Toast.makeText(this, "Tidak dapat buka aplikasi", Toast.LENGTH_SHORT).show(); }
    }

    private void pickWallpaper() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_WALLPAPER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences("mekxz", MODE_PRIVATE).edit().putString("wallpaper", uri.toString()).apply();
                loadWallpaper();
            } catch (Exception e) {
                Toast.makeText(this, "Wallpaper tidak dapat dibaca", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadWallpaper() {
        ImageView view = findViewById(R.id.homeWallpaper);
        String uri = getSharedPreferences("mekxz", MODE_PRIVATE).getString("wallpaper", "");
        if (uri.isEmpty()) return;
        try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap != null) view.setImageBitmap(bitmap);
        } catch (Exception ignored) { }
    }

    private void setupMedia() {
        mediaTitle = findViewById(R.id.mediaTitle);
        mediaArtist = findViewById(R.id.mediaArtist);
        NowPlayingManager.init(this);
        NowPlayingManager.registerListener(info -> runOnUiThread(() -> {
            mediaTitle.setText(info.title.isEmpty() ? "Tiada media dimainkan" : info.title);
            mediaArtist.setText(info.artist.isEmpty() ? "Buka aplikasi muzik" : info.artist);
            ((android.widget.Button)findViewById(R.id.mediaPlayPause)).setText(info.isPlaying ? "Ⅱ" : "▶");
        }));
        findViewById(R.id.mediaPrevious).setOnClickListener(v -> mediaAction(0));
        findViewById(R.id.mediaPlayPause).setOnClickListener(v -> mediaAction(1));
        findViewById(R.id.mediaNext).setOnClickListener(v -> mediaAction(2));
    }

    private void mediaAction(int action) {
        MediaController controller = NowPlayingManager.getCurrentInfo().controller;
        if (controller == null) {
            Toast.makeText(this, "Benarkan Notification Access untuk kawalan muzik", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == 0) controller.getTransportControls().skipToPrevious();
        if (action == 2) controller.getTransportControls().skipToNext();
        if (action == 1) {
            if (NowPlayingManager.getCurrentInfo().isPlaying) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
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
    public void showCustomWallpaperManagerDialog() { pickWallpaper(); }
    public boolean isIslamicShelfEnabled() { return false; }
    public void setIslamicShelfEnabled(boolean enabled) {}
    public void showAyah(Object ayah) {}
    public void updateAyah() {}
    public boolean isWeatherWidgetEnabled() { return true; }
    public void setWeatherWidgetEnabled(boolean enabled) {}
    public void showSoundEffectsDialog() {}
    public void showAzanSettingsDialog() { refreshPrayers(); }
    public void showGeminiAssistantDialog() {}
    public void showCleanTvCacheDialog() {}
    public void showWallpaperDialog() { pickWallpaper(); }
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
