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
    private android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && !isDestroyed()) {
                refreshPrayers();
                refreshWeather();
                refreshHandler.postDelayed(this, 30 * 60 * 1000L);
            }
        }
    };
    public String testWeatherOverride = null;

    public static MainActivity getInstance() {
        return sInstance;
    }

    public static boolean isForeground() {
        return sInstance != null;
    }

    public static void openSettingsFromGlobal() {
        if (sInstance != null) sInstance.runOnUiThread(() -> sInstance.showSettingsMenu());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        setContentView(R.layout.activity_main);
        if (!getSharedPreferences("mekxz", MODE_PRIVATE).getBoolean("prayer_zone_initialized", false)) {
            if (!getSharedPreferences("prayer_times_cache", MODE_PRIVATE).contains("manual_prayer_zone"))
                PrayerTimes.setZone(this, "KDH05", "Kulim, Bandar Baharu", 5.365, 100.561);
            getSharedPreferences("mekxz", MODE_PRIVATE).edit().putBoolean("prayer_zone_initialized", true).apply();
        }
        setupDock();
        loadWallpaper();
        applyThemeStudio();
        setupMedia();
        refreshWeather();
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
        refreshHandler.postDelayed(refreshTask, 30 * 60 * 1000L);
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTask);
        if (remote != null) remote.stop();
        super.onDestroy();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    private void refreshWeather() {
        TextView weather = findViewById(R.id.weatherText);
        weather.setText("Memuat cuaca Kulim…");
        new Thread(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=5.365&longitude=100.561&current=temperature_2m,relative_humidity_2m,weather_code&timezone=Asia%2FKuala_Lumpur");
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
                StringBuilder json = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                }
                org.json.JSONObject current = new org.json.JSONObject(json.toString()).getJSONObject("current");
                double temp = current.getDouble("temperature_2m");
                int humidity = current.getInt("relative_humidity_2m");
                int code = current.getInt("weather_code");
                String condition = code == 0 ? "Cerah" : code <= 3 ? "Berawan" :
                        code <= 48 ? "Berkabus" : code <= 67 ? "Hujan" :
                        code <= 77 ? "Salji" : code <= 82 ? "Hujan" : "Ribut";
                String display = String.format(java.util.Locale.US, "Kulim  %.1f°C  •  %s  •  %d%% lembap", temp, condition, humidity);
                runOnUiThread(() -> weather.setText(display));
            } catch (Exception e) {
                runOnUiThread(() -> weather.setText("Cuaca tidak tersedia • Semak internet"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "mekxz-weather").start();
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
                .append("\n");
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
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettingsMenu());
    }

    private void showSettingsMenu() {
                new AlertDialog.Builder(this).setTitle("MEKXZ DRIVE X")
                .setItems(new String[]{"Tukar Wallpaper", "Android Settings", "Notification Access", "Safari Remote", "Susun App Dock", "Zon Waktu Solat", "Screensaver", "Theme Studio"}, (d, which) -> {
                    if (which == 0) pickWallpaper();
                    if (which == 1) startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    if (which == 2) startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    if (which == 4) chooseDockSlot();
                    if (which == 5) choosePrayerZone();
                    if (which == 6) showNightstandScreensaver();
                    if (which == 7) showThemeStudio();
                    if (which == 3) new AlertDialog.Builder(this).setTitle("Safari Remote")
                        .setMessage("Buka http://" + WallpaperUploadServer.getLocalIpAddress() + ":" + remote.getPort() + "\\nPIN: " + remote.getPin() + "\\nTelefon dan player mesti pada Wi-Fi sama.")
                        .setPositiveButton("OK", null).show();
                }).show();
    }

    private void showThemeStudio() {
        String[] options = {"Midnight Blue", "Graphite", "Emerald", "Amber", "Laraskan kelegapan glass"};
        new AlertDialog.Builder(this).setTitle("MEKXZ Theme Studio")
            .setItems(options, (dialog, selected) -> {
                if (selected == 4) { chooseGlassOpacity(); return; }
                getSharedPreferences("theme_studio", MODE_PRIVATE).edit().putInt("accent", selected).apply();
                applyThemeStudio();
            }).show();
    }

    private void chooseGlassOpacity() {
        android.widget.SeekBar slider = new android.widget.SeekBar(this);
        slider.setMax(90);
        slider.setProgress(getSharedPreferences("theme_studio", MODE_PRIVATE).getInt("opacity", 65));
        new AlertDialog.Builder(this).setTitle("Kelegapan glass")
            .setView(slider).setPositiveButton("Simpan", (d, w) -> {
                getSharedPreferences("theme_studio", MODE_PRIVATE).edit()
                    .putInt("opacity", Math.max(15, slider.getProgress())).apply();
                applyThemeStudio();
            }).setNegativeButton("Batal", null).show();
    }

    private void applyThemeStudio() {
        SharedPreferences prefs = getSharedPreferences("theme_studio", MODE_PRIVATE);
        int selection = prefs.getInt("accent", 0);
        int[] rgb = {0x10253C, 0x272A31, 0x123A35, 0x4A321A};
        int base = rgb[Math.max(0, Math.min(selection, rgb.length - 1))];
        int alpha = (int)(255 * prefs.getInt("opacity", 65) / 100.0);
        int color = (alpha << 24) | base;
        for (int id : new int[]{R.id.prayerText, R.id.weatherText, R.id.mediaTitle,
                R.id.mediaArtist}) {
            View v = findViewById(id);
            if (v != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(color);
                bg.setCornerRadius(18 * getResources().getDisplayMetrics().density);
                bg.setStroke(1, 0x44FFFFFF);
                v.setBackground(bg);
                int pad = (int)(8 * getResources().getDisplayMetrics().density);
                v.setPadding(pad, pad, pad, pad);
            }
        }
    }

    private void choosePrayerZone() {
        java.util.List<PrayerTimes.PrayerZone> zones = PrayerTimes.getPresetZones();
        String[] names = new String[zones.size()];
        for (int i = 0; i < zones.size(); i++) names[i] = zones.get(i).getDisplayName();
        new AlertDialog.Builder(this).setTitle("Zon Waktu Solat")
            .setItems(names, (dialog, selected) -> {
                PrayerTimes.PrayerZone zone = zones.get(selected);
                PrayerTimes.setZone(this, zone.code, zone.name, zone.lat, zone.lon);
                ((TextView)findViewById(R.id.prayerText)).setText("Memuat waktu solat…");
                PrayerTimes.sync(this, this::refreshPrayers);
                refreshPrayers();
            }).show();
    }

    private final int[] dockIds = {R.id.dockApp1, R.id.dockApp2, R.id.dockApp3, R.id.dockApp4};
    private final String[] dockLabels = {"Netflix", "Music", "Radio", "YouTube"};

    private void bindApp(int id, String defaultPackage) {
        String pkg = getSharedPreferences("dock", MODE_PRIVATE).getString("slot_" + id, defaultPackage);
        View button = findViewById(id);
        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                getPackageManager().getApplicationInfo(pkg, 0));
            if (button instanceof android.widget.Button) {
                android.widget.Button dockButton = (android.widget.Button) button;
                dockButton.setText(label);
                android.graphics.drawable.Drawable appIcon = getPackageManager().getApplicationIcon(pkg);
                int size = (int)(30 * getResources().getDisplayMetrics().density);
                appIcon.setBounds(0, 0, size, size);
                dockButton.setCompoundDrawables(null, appIcon, null, null);
                dockButton.setCompoundDrawablePadding((int)(3 * getResources().getDisplayMetrics().density));
                dockButton.setGravity(android.view.Gravity.CENTER);
                dockButton.setTextSize(11);
                dockButton.setAllCaps(false);
                dockButton.setSingleLine(true);
                dockButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        } catch (Exception ignored) {
            if (button instanceof android.widget.Button) {
                android.widget.Button dockButton = (android.widget.Button) button;
                dockButton.setText("Pilih App");
                dockButton.setCompoundDrawables(null, null, null, null);
            }
        }
        button.setOnClickListener(v -> launchAppByPackage(pkg));
        button.setOnLongClickListener(v -> {
            int slot = 0;
            for (int i = 0; i < dockIds.length; i++) if (dockIds[i] == id) { slot = i; break; }
            chooseDockApp(slot);
            return true;
        });
    }

    private void chooseDockSlot() {
        new AlertDialog.Builder(this).setTitle("Pilih slot dock")
            .setItems(dockLabels, (dialog, slot) -> chooseDockApp(slot)).show();
    }

    private void chooseDockApp(int slot) {
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.Map<String, String> choices = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(getPackageName())) {
                    choices.put(ri.loadLabel(pm).toString() + " (" + ri.activityInfo.packageName + ")",
                        ri.activityInfo.packageName);
                }
            }
        }
        String[] names = choices.keySet().toArray(new String[0]);
        if (names.length == 0) {
            Toast.makeText(this, "Tiada aplikasi ditemui", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Pilih aplikasi")
            .setItems(names, (dialog, selected) -> {
                String pkg = choices.get(names[selected]);
                getSharedPreferences("dock", MODE_PRIVATE).edit()
                    .putString("slot_" + dockIds[slot], pkg).apply();
                bindApp(dockIds[slot], pkg);
            }).show();
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

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        refreshPrayers();
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
    public void showNightstandScreensaver() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(4, 9, 17));
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER);
        android.widget.TextClock clock = new android.widget.TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("hh:mm");
        clock.setTextSize(96);
        clock.setTextColor(android.graphics.Color.WHITE);
        clock.setGravity(android.view.Gravity.CENTER);
        android.widget.TextClock date = new android.widget.TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM yyyy");
        date.setFormat12Hour("EEEE, d MMMM yyyy");
        date.setTextSize(24);
        date.setTextColor(android.graphics.Color.LTGRAY);
        date.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView hint = new android.widget.TextView(this);
        hint.setText("Sentuh skrin untuk kembali");
        hint.setTextColor(android.graphics.Color.GRAY);
        hint.setTextSize(14);
        hint.setGravity(android.view.Gravity.CENTER);
        content.addView(clock);
        content.addView(date);
        content.addView(hint);
        root.addView(content, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
    public void showRestartDialog() {}
    public void showTurnOffDialog() {}
    public String getAppVersionName() { return "2.0-MEKXZ"; }
    public void showAutoUpdateDialog() {}
    public void setTestWeatherOverride(String cond) { this.testWeatherOverride = cond; }
    public void refreshLocationDependentData() {}
    public void updateSettingsBadge() {}
}
