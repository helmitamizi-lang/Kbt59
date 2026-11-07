package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Full Android launcher drawer: regular phone apps and Leanback TV apps. */
public class AppsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        PackageManager pm = getPackageManager();
        Map<String,ResolveInfo> found = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN);query.addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(getPackageName()))
                    found.put(ri.activityInfo.packageName, ri);
            }
        }
        ArrayList<ResolveInfo> items = new ArrayList<>(found.values());
        items.sort(Comparator.comparing(a -> a.loadLabel(pm).toString().toLowerCase(java.util.Locale.ROOT)));
        GridView grid = new GridView(this);
        grid.setNumColumns(6);grid.setPadding(16,20,16,20);
        grid.setHorizontalSpacing(10);grid.setVerticalSpacing(10);
        grid.setBackgroundColor(0xff050506);
        grid.setAdapter(new BaseAdapter(){
            public int getCount(){return items.size();}
            public Object getItem(int i){return items.get(i);}
            public long getItemId(int i){return i;}
            public View getView(int i, View recycled, ViewGroup parent){
                LinearLayout cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(android.view.Gravity.CENTER);
                cell.setPadding(3,10,3,8);
                ImageView iv=new ImageView(AppsActivity.this);iv.setImageDrawable(items.get(i).loadIcon(pm));
                cell.addView(iv,new LinearLayout.LayoutParams(54,54));
                TextView name=new TextView(AppsActivity.this);name.setText(items.get(i).loadLabel(pm));
                name.setTextColor(0xfff2f2f2);name.setGravity(android.view.Gravity.CENTER);
                name.setTextSize(12);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(name,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,30));
                return cell;
            }
        });
        grid.setOnItemClickListener((parent,view,position,id)->{
            String pkg=items.get(position).activityInfo.packageName;
            try{Intent launch=pm.getLaunchIntentForPackage(pkg);if(launch==null)throw new Exception();startActivity(launch);}
            catch(Exception e){Toast.makeText(this,"Tidak dapat buka aplikasi",Toast.LENGTH_SHORT).show();}
        });
        setContentView(grid);
    }
}