package com.gathin.tvhome;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ImageView btnAllApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Automotive immersive full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        setupDock();
    }

    private void setupDock() {
        btnAllApps = findViewById(R.id.btnAllApps);
        btnAllApps.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppsActivity.class);
            startActivity(intent);
        });

        // Set up custom dock slots (mock mappings, users will configure via SharedPreferences)
        findViewById(R.id.dockApp1).setOnClickListener(v -> launchAppByPackage("com.google.android.apps.maps"));
        findViewById(R.id.dockApp2).setOnClickListener(v -> launchAppByPackage("com.spotify.music"));
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
        // Do nothing. The Home button/Back button shouldn't exit the launcher.
    }
}
