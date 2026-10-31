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
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> availableActivities = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : availableActivities) {
            // Prevent launcher from showing itself in the drawer
            if (ri.activityInfo.packageName.equals(getPackageName())) {
                continue;
            }
            AppInfo app = new AppInfo(
                    ri.loadLabel(pm),
                    ri.activityInfo.packageName,
                    ri.activityInfo.loadIcon(pm)
            );
            appList.add(app);
        }

        // Sort alphabetically
        Collections.sort(appList, (a, b) -> a.name.toString().compareToIgnoreCase(b.name.toString()));

        adapter = new AppAdapter(this, appList);
        rvApps.setAdapter(adapter);
    }
}
