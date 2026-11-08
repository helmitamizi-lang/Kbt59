package com.gathin.tvhome;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class AppsActivity extends AppCompatActivity {

    private RecyclerView rvApps;
    private AppAdapter adapter;
    private List<AppInfo> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_apps);

        rvApps = findViewById(R.id.rvApps);
        
        // 1024x600 layout fits roughly 6 columns nicely
        rvApps.setLayoutManager(new GridLayoutManager(this, 6)); 
        
        loadApps();
    }

    private void loadApps() {
        appList = new ArrayList<>();
        PackageManager pm = getPackageManager();
        // Android head units can host both regular and Leanback-only apps.
        // Merge both launcher categories while keeping one entry per package.
        Map<String, AppInfo> uniqueApps = new LinkedHashMap<>();
        collectApps(pm, Intent.CATEGORY_LAUNCHER, uniqueApps);
        collectApps(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, uniqueApps);
        appList.addAll(uniqueApps.values());

        // Sort alphabetically
        Collections.sort(appList, (a, b) -> a.name.toString().compareToIgnoreCase(b.name.toString()));

        adapter = new AppAdapter(this, appList);
        rvApps.setAdapter(adapter);
    }
    private void collectApps(PackageManager pm, String category, Map<String, AppInfo> apps) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
            apps.put(pkg, new AppInfo(ri.loadLabel(pm), pkg, ri.loadIcon(pm)));
        }
    }
}
