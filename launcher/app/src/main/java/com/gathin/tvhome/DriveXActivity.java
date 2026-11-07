package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native 1024x600 reference cockpit; no WebView or simulated vehicle telemetry. */
public final class DriveXActivity extends Activity implements LocationListener, NowPlayingManager.Listener {
    private static final int PHOTO_PICK = 91, LYRIC_PICK = 92, GPS_REQUEST = 93, NOTIFY_REQUEST = 94;
    private static final String PREFS = "mekxz_drive_x";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Dashboard dashboard;
    private LocationManager gps;
    private double speedKmh;
    private boolean hasGps, editMode, showLyrics, screenDimmed, screensaver;
    private long speedAt, lastTouch, lastWeather;
    private String status = "GPS: mencari isyarat", weather = "Cuaca: memuat data", greetingStatus = "";
    private Bitmap carBitmap;
    private ArrayList<String> lyrics = new ArrayList<>();
    private ArrayList<Integer> lyricMillis = new ArrayList<>();
    private TextToSpeech tts;
    private DriveXRemoteServer remote;
    private final String[] ids = {"speed","car","prayer","media","weather"};
    private final RectF[] boxes = {
        new RectF(132, 132, 407, 452),
        new RectF(416, 121, 750, 352),
        new RectF(760, 86, 1012, 435),
        new RectF(420, 362, 748, 491),
        new RectF(420, 69, 746, 120)
    };
    private final int WHITE = 0xfff5f5f7, DIM = 0xffadaeb4, SILVER = 0xffa6aab1, RED = 0xffed313f;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i=0;i<boxes.length;i++) {
            RectF r=boxes[i]; float w=r.width(),h=r.height();
            float x=prefs.getFloat(ids[i]+"_x",r.left), y=prefs.getFloat(ids[i]+"_y",r.top);
            r.set(x,y,x+prefs.getFloat(ids[i]+"_w",w),y+prefs.getFloat(ids[i]+"_h",h));
        }
        immersive();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SoundEffects.init(this);
        NowPlayingManager.init(this);
        NowPlayingManager.registerListener(this);
        dashboard = new Dashboard();
        setContentView(dashboard);
        lastTouch=SystemClock.elapsedRealtime();
        // A safe fallback until permission is granted and a valid GPS fix exists.
        if (!prefs.getBoolean("location_initialized",false)) {
            AppLocationManager.setCustomLocation(this, "KULIM", "Kulim", "Kulim, Kedah", 5.364, 100.561);
            prefs.edit().putBoolean("location_initialized",true).apply();
        }
        initCarImage();
        refreshPrayerWeather();
        try { Azan.schedule(this, PrayerTimes.getActiveZone(this)); } catch(Exception ignored) {}
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_REQUEST);
        startGps();
        maybeGreetOnDeviceBoot();
        handler.postDelayed(ticker, 1000);
    }
    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if (dashboard==null) return;
            if (SystemClock.elapsedRealtime()-speedAt>6000) {hasGps=false;speedKmh=0;}
            if (SystemClock.elapsedRealtime()-lastTouch>prefs.getInt("screensaver_after",300)*1000L) screensaver=true;
            if (SystemClock.elapsedRealtime()-lastWeather>15*60_000L) refreshPrayerWeather();
            dashboard.invalidate();handler.postDelayed(this,1000);
        }
    };
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public void onResume() {super.onResume();startGps();editMode=prefs.getBoolean("edit_layout",false);if(dashboard!=null)dashboard.invalidate();}
    @Override public void onPause() {super.onPause();stopGps();}
    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);stopGps();NowPlayingManager.unregisterListener(this);
        if(remote!=null)remote.close();if(tts!=null)tts.shutdown();super.onDestroy();
    }
    private void startGps() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            status="GPS memerlukan kebenaran lokasi";return;
        }
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        try {
            if(gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0f,this);
                status="GPS: menunggu bacaan";
            } else status="GPS belum diaktifkan";
        }catch(Exception e){status="GPS tidak tersedia";}
    }
    private void stopGps(){if(gps!=null){try{gps.removeUpdates(this);}catch(Exception ignored){}gps=null;}}
    @Override public void onLocationChanged(Location l) {
        speedAt=SystemClock.elapsedRealtime();
        hasGps=l.hasSpeed() && l.getAccuracy()<100f;
        speedKmh=hasGps?Math.max(0,l.getSpeed()*3.6):0;
        status=hasGps?"GPS • ±"+Math.round(l.getAccuracy())+" m":"GPS • isyarat diterima";
        double lat=l.getLatitude(),lon=l.getLongitude();
        double lastLat=prefs.getFloat("gps_lat",0),lastLon=prefs.getFloat("gps_lon",0);
        if(Math.abs(lat-lastLat)+Math.abs(lon-lastLon)>0.10) {
            prefs.edit().putFloat("gps_lat",(float)lat).putFloat("gps_lon",(float)lon).apply();
            // Zone selection is an approximation from built-in presets. User can override in Settings.
            PrayerTimes.PrayerZone nearest=null;double best=Double.MAX_VALUE;
            for(PrayerTimes.PrayerZone z:PrayerTimes.getPresetZones()) {
                if(z.code.endsWith("_PJ") || z.code.startsWith("ID_"))continue;
                double a=(z.lat-lat)*111, b=(z.lon-lon)*111*Math.cos(Math.toRadians(lat));
                double d=a*a+b*b;if(d<best){best=d;nearest=z;}
            }
            if(nearest!=null && best<300*300 && prefs.getBoolean("auto_zone",true)) {
                final PrayerTimes.PrayerZone chosen=nearest;
                AppLocationManager.setCustomLocation(this,chosen.code,chosen.name,"GPS auto",lat,lon);
                PrayerTimes.setZone(this,chosen.code);
                try {Azan.schedule(this,chosen.code);}catch(Exception ignored){}
                status+=" • "+chosen.code;
            }
            refreshPrayerWeather();
        }
        dashboard.invalidate();
    }
    @Override public void onMediaChanged(NowPlayingManager.MediaInfo info) {runOnUiThread(()->{if(dashboard!=null)dashboard.invalidate();});}
    private void refreshPrayerWeather() {
        lastWeather=SystemClock.elapsedRealtime();
        PrayerTimes.syncIfDue(this,()->handler.post(()->{try {Azan.schedule(this,PrayerTimes.getActiveZone(this));}catch(Exception ignored){}if(dashboard!=null)dashboard.invalidate();}));
        if(!HomeWeather.due(this)) {
            HomeWeather.Line w=HomeWeather.last(this);if(w!=null)weather=w.weather;
            return;
        }
        new Thread(()->{
            HomeWeather.Line line=HomeWeather.refresh(this,AppLocationManager.getLatitude(this),AppLocationManager.getLongitude(this));
            handler.post(()->{weather=line==null?"Cuaca tidak tersedia":line.weather;if(dashboard!=null)dashboard.invalidate();});
        },"drivex-weather").start();
    }
    private void maybeGreetOnDeviceBoot(){
        long bootEpoch=System.currentTimeMillis()-SystemClock.elapsedRealtime();
        long last=prefs.getLong("boot_epoch",0);
        if(Math.abs(bootEpoch-last)<60000 || !prefs.getBoolean("greeting",true))return;
        prefs.edit().putLong("boot_epoch",bootEpoch).apply();
        // Android TTS quality depends on installed voice; not represented as a guaranteed natural recording.
        tts=new TextToSpeech(this,ok->{
            if(ok==TextToSpeech.SUCCESS && tts!=null){
                tts.setLanguage(new Locale("ms","MY"));
                Calendar c=Calendar.getInstance();
                int h=c.get(Calendar.HOUR_OF_DAY);
                String p=h<12?"Selamat pagi":h<18?"Selamat petang":"Selamat malam";
                tts.speak(p+". Selamat datang ke MEKXZ Drive X. Semoga perjalanan anda selamat.",
                        TextToSpeech.QUEUE_FLUSH,null,"drivex-greeting");
            }
        });
    }
    private void initCarImage(){
        String uri=prefs.getString("car_uri","");
        if(uri.isEmpty())return;
        try(InputStream in=getContentResolver().openInputStream(Uri.parse(uri))){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;
            carBitmap=BitmapFactory.decodeStream(in,null,o);
        }catch(Exception e){carBitmap=null;}
    }
    private void pickPhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PHOTO_PICK);}
    private void pickLyrics(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,LYRIC_PICK);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PHOTO_PICK){
            try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            prefs.edit().putString("car_uri",data.getData().toString()).apply();initCarImage();dashboard.invalidate();
        }else if(request==LYRIC_PICK){
            lyrics.clear();lyricMillis.clear();
            try(BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(data.getData())))){
                String line;
                while((line=br.readLine())!=null){
                    if(!line.startsWith("[")||line.length()<9)continue;
                    int end=line.indexOf(']');if(end<6)continue;
                    String stamp=line.substring(1,end),body=line.substring(end+1).trim();
                    String[] t=stamp.split(":");if(t.length!=2||body.isEmpty())continue;
                    int ms=(int)((Integer.parseInt(t[0])*60+Double.parseDouble(t[1]))*1000);
                    lyrics.add(body);lyricMillis.add(ms);
                }
                showLyrics=true;Toast.makeText(this,"Lirik LRC dimuatkan",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Fail .lrc tidak sah",Toast.LENGTH_SHORT).show();}
        }
    }
    private void openSettings(){
        String[] actions={"Theme Studio","Resize widget","Ubah gambar kereta","Gaya meter GPS","Edit susunan widget","Warna accent","Bunyi navigasi PS5","Azan & notification","Zon solat / lokasi","Aktifkan GPS","Web remote iPhone","Import lirik .LRC","Lyrics mode","Screensaver","Off-screen (dim)","Voice greeting","Semua apps","Launcher TV asal"};
        new AlertDialog.Builder(this).setTitle("MEKXZ DRIVE X • Settings").setItems(actions,(d,which)->{
            SoundEffects.playSelect();
            if(which==0){startActivity(new Intent(this,DriveXThemeStudioActivity.class));return;}
            if(which==1){widgetSizePicker();return;}
            which-=2;
            switch(which){
                case 0:pickPhoto();break;
                case 1:options("Gaya speedometer",new String[]{"RS Analog","Digital Minimal"}, "gauge",new String[]{"rs","digital"});break;
                case 2: editMode=!editMode;Toast.makeText(this,editMode?"Tekan lama dan seret widget":"Susunan dikunci",Toast.LENGTH_LONG).show();dashboard.invalidate();break;
                case 3: options("Warna accent",new String[]{"Merah RS","Putih/perak","Hijau"}, "accent",new String[]{"red","silver","green"});break;
                case 4: options("Sound effect",new String[]{"PS5-style","Nintendo-style","Xbox-style","Mute"}, "sound",new String[]{"ps5","switch","xbox","off"});break;
                case 5:Azan.setMasterEnabled(this,!Azan.isMasterEnabled(this));Azan.schedule(this,PrayerTimes.getActiveZone(this));Toast.makeText(this,"Azan: "+(Azan.isMasterEnabled(this)?"ON":"OFF"),Toast.LENGTH_SHORT).show();break;
                case 6:showZonePicker();break;
                case 7: if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},GPS_REQUEST);else startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));break;
                case 8:startRemote();break;
                case 9:pickLyrics();break;
                case 10:showLyrics=!showLyrics;dashboard.invalidate();break;
                case 11:options("Screensaver",new String[]{"Jam digital • 5 min","Jam analog • 5 min","Selepas 10 min","Off"}, "screensaver",new String[]{"digital","analog","10","off"});break;
                case 12:dimScreen();break;
                case 13:prefs.edit().putBoolean("greeting",!prefs.getBoolean("greeting",true)).apply();break;
                case 14:showAllApps();break;
                case 15:startActivity(new Intent(this,MainActivity.class));break;
            }
        }).show();
    }
    private void options(String title,String[] choices,String key,String[] values){
        new AlertDialog.Builder(this).setTitle(title).setItems(choices,(d,i)->{
            prefs.edit().putString(key,values[i]).apply();
            if("sound".equals(key))SoundEffects.setTheme(this,values[i]);
            if("screensaver".equals(key))prefs.edit().putInt("screensaver_after","off".equals(values[i])?Integer.MAX_VALUE:("10".equals(values[i])?600:300)).apply();
            dashboard.invalidate();
        }).show();
    }
    private void widgetSizePicker(){
        String[] labels={"GPS speedometer","Kereta","Waktu solat","Media player","Cuaca"};
        new AlertDialog.Builder(this).setTitle("Resize widget").setItems(labels,(d,i)->{
            new AlertDialog.Builder(this).setTitle(labels[i]).setItems(new String[]{"Kecil (80%)","Normal (100%)","Besar (120%)","Lebar (140%)"},(d2,n)->{
                float[] baseW={275,334,252,328,326},baseH={320,231,349,129,51};
                float wx=n==0?.8f:n==3?1.4f:n==2?1.2f:1f;
                float hy=n==0?.8f:n==2?1.2f:1f;
                RectF r=boxes[i];r.right=Math.min(1018,r.left+baseW[i]*wx);r.bottom=Math.min(510,r.top+baseH[i]*hy);
                prefs.edit().putFloat(ids[i]+"_w",r.width()).putFloat(ids[i]+"_h",r.height()).apply();dashboard.invalidate();
            }).show();
        }).show();
    }
    private void showZonePicker(){
        List<PrayerTimes.PrayerZone> zones=PrayerTimes.getPresetZones();String[] names=new String[zones.size()+1];
        names[0]="GPS automatik (fallback Kulim)";
        for(int i=0;i<zones.size();i++)names[i+1]=zones.get(i).getDisplayName();
        new AlertDialog.Builder(this).setTitle("Pilih zon JAKIM").setItems(names,(d,idx)->{
            if(idx==0){prefs.edit().putBoolean("auto_zone",true).apply();PrayerTimes.clearManualZone(this);}
            else{PrayerTimes.PrayerZone z=zones.get(idx-1);prefs.edit().putBoolean("auto_zone",false).apply();PrayerTimes.setZone(this,z.code);}
            PrayerTimes.sync(this,()->handler.post(()->dashboard.invalidate()));
            Azan.schedule(this,PrayerTimes.getActiveZone(this));dashboard.invalidate();
        }).show();
    }
    private void dimScreen(){
        screenDimmed=true;WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=0.01f;getWindow().setAttributes(a);dashboard.invalidate();
    }
    private void wakeScreen(){
        screenDimmed=false;screensaver=false;WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=-1f;getWindow().setAttributes(a);lastTouch=SystemClock.elapsedRealtime();dashboard.invalidate();
    }
    private void startRemote(){
        if(remote==null){remote=new DriveXRemoteServer(this,prefs,()->runOnUiThread(()->dashboard.invalidate()));remote.start();}
        new AlertDialog.Builder(this).setTitle("iPhone Safari • Remote")
            .setMessage(remote.getInstructions()).setPositiveButton("OK",null).show();
    }
    void applyRemoteChange(String key,String value){
        if("gauge".equals(key)||"accent".equals(key)||"screensaver".equals(key)){
            prefs.edit().putString(key,value).apply();dashboard.invalidate();
        }else if("move".equals(key)){
            String[] s=value.split(",");
            try{int i=Integer.parseInt(s[0]);float x=Float.parseFloat(s[1]),y=Float.parseFloat(s[2]);
                if(i>=0&&i<boxes.length){RectF r=boxes[i];r.offsetTo(Math.max(110,Math.min(1010-r.width(),x)),Math.max(55,Math.min(505-r.height(),y)));
                prefs.edit().putFloat(ids[i]+"_x",r.left).putFloat(ids[i]+"_y",r.top).apply();dashboard.invalidate();}}
            catch(Exception ignored){}
        }
    }
    private void launch(String pkg){
        try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)throw new Exception();startActivity(i);}
        catch(Exception e){Toast.makeText(this,"Aplikasi belum dipasang / tidak boleh dibuka",Toast.LENGTH_SHORT).show();}
    }
    private ArrayList<ResolveInfo> discoverApps(){
        PackageManager pm=getPackageManager();Map<String,ResolveInfo> all=new LinkedHashMap<>();
        for(String cat:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(cat);
            for(ResolveInfo a:pm.queryIntentActivities(query,0))if(a.activityInfo!=null)all.put(a.activityInfo.packageName,a);
        }
        ArrayList<ResolveInfo> out=new ArrayList<>(all.values());
        out.sort(Comparator.comparing(a->a.loadLabel(pm).toString().toLowerCase(Locale.ROOT)));
        return out;
    }
    private void showAllApps(){
        ArrayList<ResolveInfo> apps=discoverApps();PackageManager pm=getPackageManager();
        GridView grid=new GridView(this);grid.setNumColumns(5);grid.setVerticalSpacing(12);grid.setHorizontalSpacing(12);grid.setPadding(12,16,12,16);
        grid.setBackgroundColor(0xff101011);
        grid.setAdapter(new BaseAdapter() {
            public int getCount(){return apps.size();}
            public Object getItem(int p){return apps.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int p,View reuse,android.view.ViewGroup parent){
                LinearLayout cell=new LinearLayout(DriveXActivity.this);cell.setOrientation(1);cell.setGravity(17);cell.setPadding(4,8,4,8);
                ImageView icon=new ImageView(DriveXActivity.this);try{icon.setImageDrawable(apps.get(p).loadIcon(pm));}catch(Exception ignored){}
                cell.addView(icon,new LinearLayout.LayoutParams(50,50));
                TextView label=new TextView(DriveXActivity.this);label.setText(apps.get(p).loadLabel(pm));label.setTextSize(12);label.setMaxLines(1);label.setTextColor(-1);label.setGravity(17);
                cell.addView(label,new LinearLayout.LayoutParams(-1,32));return cell;
            }
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Semua Aplikasi • "+apps.size()).setView(grid).setNegativeButton("Tutup",null).create();
        grid.setOnItemClickListener((p,v,pos,id)->{ResolveInfo a=apps.get(pos);dialog.dismiss();launch(a.activityInfo.packageName);});
        grid.setOnItemLongClickListener((p,v,pos,id)->{ResolveInfo a=apps.get(pos);dockAppChoice(a.activityInfo.packageName);return true;});
        dialog.show();
    }
    private void dockAppChoice(String pkg){
        String[] labels={"Dock 1","Dock 2","Dock 3","Dock 4","Dock 5","Dock 6"};
        new AlertDialog.Builder(this).setTitle("Tambah ke dock").setItems(labels,(d,i)->{prefs.edit().putString("dock_"+i,pkg).apply();dashboard.invalidate();}).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] results){
        super.onRequestPermissionsResult(request,perms,results);
        if(request==GPS_REQUEST&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startGps();
    }
    @Override public void onBackPressed(){if(screensaver||screenDimmed)wakeScreen();else openSettings();}

    private final class Dashboard extends View {
        private final Paint p=new Paint(3);private float scaleX,scaleY;
        private long downAt;private float downX,downY,grabX,grabY;private int hit=-1;private boolean dragging;
        Dashboard(){super(DriveXActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        private void fill(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);p.setShader(null);}
        private void text(Canvas c,String s,float x,float y,float sz,int color,boolean bold){
            fill(color);p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",1):android.graphics.Typeface.create("sans-serif",0));p.setTextSize(sz);c.drawText(s,x,y,p);
        }
        private void panel(Canvas c,RectF r,float radius){
            int glass=prefs.getInt("glass",65),dark=prefs.getInt("darkness",95);
            int top=Color.argb(Math.min(245,115+glass),42,43,47);
            int bottom=Color.argb(Math.min(240,95+glass),12,12,15);
            fill(top);p.setShader(new LinearGradient(r.left,r.top,r.left,r.bottom,top,bottom,Shader.TileMode.CLAMP));
            float rad=prefs.getInt("radius",18);c.drawRoundRect(r,rad,rad,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f+prefs.getInt("glow",65)/100f);p.setColor(0xff6b6d72);c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);
            fill(0x30ffffff);c.drawRoundRect(new RectF(r.left+11,r.top+2,r.right-11,r.top+3.5f),2,2,p);
        }
        private void circle(Canvas c,float x,float y,float rad,int color){fill(color);c.drawCircle(x,y,rad,p);}
        private int themeAccent(){String a=prefs.getString("accent","red");switch(a){case "silver":return 0xffdddddd;case "amber":return 0xffefb24b;case "purple":return 0xffa98af0;case "green":return 0xff60dba3;default:return RED;}}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);scaleX=getWidth()/1024f;scaleY=getHeight()/600f;canvas.save();canvas.scale(scaleX,scaleY);
            canvas.drawColor(0xff030303);
            fill(0xff101012);p.setShader(new android.graphics.RadialGradient(570,260,460,new int[]{0xff202024,0xff09090b,0xff030303},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,1024,600,p);p.setShader(null);
            fill(0xff46464a);p.setStrokeWidth(1);canvas.drawLine(20,51,1004,51,p);
            text(canvas,"MEKXZ DRIVE",24,35,27,WHITE,true);text(canvas,"X",223,35,30,themeAccent(),true);
            text(canvas,"TOYOTA VIOS  •  SMART COCKPIT",350,31,12,0xff929298,false);
            text(canvas,new SimpleDateFormat("HH:mm",Locale.forLanguageTag("ms-MY")).format(new Date()),908,36,23,WHITE,true);
            RectF sidebar=new RectF(10,60,105,588);panel(canvas,sidebar,17);
            String[] nav={"⌂","➤","♫","▦","⚙"};String[] sub={"HOME","NAV","MEDIA","APPS","SET"};
            for(int i=0;i<5;i++){float y=114+i*92;
                if(i==0){RectF r=new RectF(20,y-30,95,y+40);panel(canvas,r,14);}
                text(canvas,nav[i],44,y+6,31,WHITE,true);text(canvas,sub[i],29,y+29,13,DIM,false);
            }
            if(prefs.getBoolean("show_speed",true))drawSpeed(canvas,boxes[0]);
            if(prefs.getBoolean("show_car",true))drawCar(canvas,boxes[1]);
            if(prefs.getBoolean("show_prayer",true))drawPrayer(canvas,boxes[2]);
            if(prefs.getBoolean("show_media",true))drawMedia(canvas,boxes[3]);
            if(prefs.getBoolean("show_weather",true))drawWeather(canvas,boxes[4]);drawDock(canvas);
            if(editMode){text(canvas,"EDIT LAYOUT • tekan lama, seret, lepaskan untuk simpan",140,510,15,0xffe5e5e5,true);}
            if(screenDimmed){fill(0xff000000);canvas.drawRect(0,0,1024,600,p);text(canvas,"Sentuh untuk hidupkan skrin",335,300,22,WHITE,true);}
            else if(screensaver){fill(0xff000000);canvas.drawRect(0,0,1024,600,p);
                text(canvas,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),340,318,98,WHITE,true);
                text(canvas,"MEKXZ DRIVE X • Sentuh untuk kembali",350,360,18,DIM,false);}
            canvas.restore();
        }
        private void drawSpeed(Canvas c,RectF r){
            panel(c,r,20);float cx=r.centerX(),cy=r.top+148,rad=Math.min(129,r.width()/2-13);
            fill(0xff080809);c.drawCircle(cx,cy,rad,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffd0d0d4);c.drawCircle(cx,cy,rad-3,p);p.setStyle(Paint.Style.FILL);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(9);p.setColor(0xff3d3d42);c.drawArc(cx-rad+10,cy-rad+10,cx+rad-10,cy+rad-10,135,270,false,p);
            p.setStrokeWidth(3);p.setColor(themeAccent());c.drawArc(cx-rad+10,cy-rad+10,cx+rad-10,cy+rad-10,290,48,false,p);
            for(int i=0;i<=26;i++){double a=Math.toRadians(135+i*270/26.0);
                float x1=cx+(rad-18)*(float)Math.cos(a),y1=cy+(rad-18)*(float)Math.sin(a),x2=cx+(rad-(i%2==0?35:27))*(float)Math.cos(a),y2=cy+(rad-(i%2==0?35:27))*(float)Math.sin(a);
                p.setColor(i>=22?themeAccent():WHITE);p.setStrokeWidth(i%2==0?2:1);c.drawLine(x1,y1,x2,y2,p);
            }
            p.setStyle(Paint.Style.FILL);
            if("digital".equals(prefs.getString("gauge","rs"))){
                text(c,"GPS DIGITAL",cx-68,cy-60,15,DIM,true);text(c,String.valueOf(hasGps?(int)Math.round(speedKmh):0),cx-55,cy+28,79,WHITE,true);
            }else{
                double a=Math.toRadians(135+Math.min(260,speedKmh)/260*270);
                p.setColor(themeAccent());p.setStrokeWidth(3.4f);c.drawLine(cx,cy,cx+(rad-48)*(float)Math.cos(a),cy+(rad-48)*(float)Math.sin(a),p);
                text(c,"GPS SPEED",cx-49,cy-50,14,DIM,false);text(c,String.valueOf(hasGps?(int)Math.round(speedKmh):0),cx-35,cy+28,66,WHITE,true);
            }
            text(c,"km/h",cx-26,cy+54,16,DIM,false);
            text(c,"0",cx-90,cy+87,13,DIM,false);text(c,"260",cx+59,cy+87,13,DIM,false);
            text(c,status.length()>32?status.substring(0,32):status,r.left+18,r.bottom-38,12,DIM,false);
            text(c,"TOYOTA • VIOS RS",r.left+18,r.bottom-14,16,0xffdddddf,true);
        }
        private void drawCar(Canvas c,RectF r){
            panel(c,r,20);
            if(carBitmap!=null){
                p.setFilterBitmap(true);fill(-1);
                Rect src=new Rect(0,0,carBitmap.getWidth(),carBitmap.getHeight());
                c.save();Path path=new Path();path.addRoundRect(r,20,20,Path.Direction.CW);c.clipPath(path);
                float zoom=Math.max(0.65f,Math.min(2.5f,prefs.getFloat("car_zoom",1f)));
                float aspect=(float)carBitmap.getWidth()/Math.max(1,carBitmap.getHeight());
                float targetW=r.width()*zoom,targetH=targetW/aspect;
                if(targetH<r.height()*zoom){targetH=r.height()*zoom;targetW=targetH*aspect;}
                float shiftX=prefs.getFloat("car_shift_x",0f),shiftY=prefs.getFloat("car_shift_y",0f);
                RectF dest=new RectF(r.centerX()-targetW/2+shiftX,r.centerY()-targetH/2+shiftY,r.centerX()+targetW/2+shiftX,r.centerY()+targetH/2+shiftY);
                c.drawBitmap(carBitmap,src,dest,p);c.restore();
            }else{
                drawCarPlaceholder(c,r);
            }
            text(c,"◉",r.right-33,r.bottom-17,23,themeAccent(),false);
        }
        private void drawCarPlaceholder(Canvas c,RectF r){
            fill(0xff101114);c.drawRoundRect(new RectF(r.left+8,r.top+8,r.right-8,r.bottom-8),17,17,p);
            float x=r.left+30,y=r.top+90,w=r.width()-60;
            Path silhouette=new Path();silhouette.moveTo(x,y+57);silhouette.lineTo(x+25,y+38);silhouette.lineTo(x+w*.27f,y+32);silhouette.lineTo(x+w*.40f,y+2);silhouette.lineTo(x+w*.73f,y+2);silhouette.lineTo(x+w*.86f,y+35);silhouette.lineTo(x+w-7,y+43);silhouette.lineTo(x+w,y+61);silhouette.close();
            fill(0xff3a3d44);c.drawPath(silhouette,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffb2b4ba);c.drawPath(silhouette,p);p.setStyle(Paint.Style.FILL);
            fill(0xff0d1015);c.drawRoundRect(new RectF(x+w*.43f,y+7,x+w*.70f,y+30),5,5,p);
            circle(c,x+w*.23f,y+60,20,0xff0b0b0d);circle(c,x+w*.78f,y+60,20,0xff0b0b0d);
            circle(c,x+w*.23f,y+60,11,0xffb9bac1);circle(c,x+w*.78f,y+60,11,0xffb9bac1);
            text(c,"TOYOTA VIOS RS",r.left+20,r.top+33,19,WHITE,true);
            text(c,"KBT 59",r.left+20,r.top+54,15,0xffc3c3c8,false);
            text(c,"Import gambar Vios dalam Settings",r.left+20,r.bottom-19,12,DIM,false);
        }
        private void drawPrayer(Canvas c,RectF r){
            panel(c,r,20);text(c,"WAKTU SOLAT",r.left+19,r.top+34,22,WHITE,true);
            text(c,"Zon: "+PrayerTimes.getActiveZone(DriveXActivity.this),r.left+19,r.top+57,14,DIM,false);
            PrayerTimes.PrayerItem[] times=PrayerTimes.getPrayerItems(DriveXActivity.this);
            float y=r.top+91;
            for(PrayerTimes.PrayerItem item:times){
                if("Syuruk".equalsIgnoreCase(item.name))continue;
                if(item.isNext){fill(0xff183b25);RectF hi=new RectF(r.left+10,y-20,r.right-10,y+12);c.drawRoundRect(hi,9,9,p);
                    p.setColor(0xff72e58c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawRoundRect(hi,9,9,p);p.setStyle(Paint.Style.FILL);}
                text(c,item.name,r.left+21,y,19,item.isNext?0xff88f0a2:WHITE,item.isNext);
                text(c,item.timeStr,r.right-78,y,18,item.isNext?0xff88f0a2:WHITE,item.isNext);
                y+=45;if(y>r.bottom-30)break;
            }
            text(c,Azan.isMasterEnabled(DriveXActivity.this)?"♪ Azan & notifikasi: ON":"♪ Azan: OFF",r.left+21,r.bottom-16,14,0xff83d592,false);
        }
        private void drawMedia(Canvas c,RectF r){
            panel(c,r,20);
            NowPlayingManager.MediaInfo m=NowPlayingManager.getCurrentInfo();
            RectF art=new RectF(r.left+12,r.top+13,r.left+110,r.top+111);
            if(m!=null&&m.albumArt!=null){
                c.save();Path clip=new Path();clip.addRoundRect(art,12,12,Path.Direction.CW);c.clipPath(clip);
                fill(-1);c.drawBitmap(m.albumArt,null,art,p);c.restore();
            }else{
                fill(0xff323237);p.setShader(new LinearGradient(art.left,art.top,art.right,art.bottom,0xff52525b,0xff17171a,Shader.TileMode.CLAMP));c.drawRoundRect(art,10,10,p);p.setShader(null);text(c,"♫",art.left+28,art.centerY()+13,42,WHITE,true);
            }
            String title=m!=null&&!m.title.isEmpty()?m.title:"READY TO PLAY";
            String artist=m!=null&&!m.artist.isEmpty()?m.artist:"Music • Radio • Local";
            if(showLyrics && !lyrics.isEmpty()){
                String line=lyrics.get(0);try{long pos=m!=null&&m.controller!=null&&m.controller.getPlaybackState()!=null?m.controller.getPlaybackState().getPosition():0;
                    for(int i=0;i<lyrics.size();i++)if(pos>=lyricMillis.get(i))line=lyrics.get(i);}catch(Exception ignored){}
                title=line;artist="LYRIC MODE • .LRC";
            }
            if(title.length()>28)title=title.substring(0,27)+"…";if(artist.length()>38)artist=artist.substring(0,37)+"…";
            text(c,title,r.left+122,r.top+36,19,WHITE,true);text(c,artist,r.left+122,r.top+59,13,DIM,false);
            fill(0xff54545a);c.drawRoundRect(new RectF(r.left+124,r.bottom-48,r.right-20,r.bottom-45),2,2,p);
            text(c,"◀◀",r.left+136,r.bottom-17,22,WHITE,true);
            text(c,m!=null&&m.isPlaying?"Ⅱ":"▶",r.left+207,r.bottom-17,26,WHITE,true);
            text(c,"▶▶",r.left+268,r.bottom-17,22,WHITE,true);
        }
        private void drawWeather(Canvas c,RectF r){
            panel(c,r,16);text(c,"☀",r.left+18,r.top+36,28,WHITE,true);
            String loc=AppLocationManager.getLocationName(DriveXActivity.this);
            text(c,weather.length()>25?weather.substring(0,25):weather,r.left+67,r.top+30,18,WHITE,true);
            text(c,loc,r.left+68,r.top+50,13,DIM,false);
        }
        private void drawDockSymbol(Canvas c,String name,float cx,float cy){
            p.setColor(0xfff4f4f6);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
            if("Maps".equals(name)||"Waze".equals(name)){
                c.drawCircle(cx,cy-5,8,p);c.drawLine(cx-5,cy+1,cx,cy+13,p);c.drawLine(cx+5,cy+1,cx,cy+13,p);
            }else if("YouTube".equals(name)){
                c.drawRoundRect(new RectF(cx-13,cy-9,cx+13,cy+9),6,6,p);Path triangle=new Path();triangle.moveTo(cx-3,cy-5);triangle.lineTo(cx+6,cy);triangle.lineTo(cx-3,cy+5);triangle.close();p.setStyle(Paint.Style.FILL);c.drawPath(triangle,p);
            }else if("Spotify".equals(name)||"Radio".equals(name)){
                c.drawArc(cx-12,cy-10,cx+12,cy+10,210,120,false,p);c.drawArc(cx-9,cy-5,cx+9,cy+8,210,120,false,p);
            }else if("Netflix".equals(name)){
                c.drawLine(cx-8,cy-12,cx-8,cy+12,p);c.drawLine(cx+8,cy-12,cx+8,cy+12,p);c.drawLine(cx-8,cy-12,cx+8,cy+12,p);
            }p.setStyle(Paint.Style.FILL);
        }
        private void drawDock(Canvas c){
            RectF r=new RectF(120,520,1016,594);panel(c,r,17);
            String[] pkgs={"com.google.android.apps.maps","com.waze","com.google.android.youtube","com.spotify.music","com.netflix.mediaclient","com.android.fmradio"};
            String[] names={"Maps","Waze","YouTube","Spotify","Netflix","Radio"};
            for(int i=0;i<7;i++){
                float x=145+i*125;
                if(i==6){text(c,"▦",x+30,559,32,WHITE,true);text(c,"All Apps",x+16,583,13,WHITE,false);continue;}
                String pkg=prefs.getString("dock_"+i,pkgs[i]);Drawable icon=null;
                try{icon=getPackageManager().getApplicationIcon(pkg);}catch(Exception ignored){}
                if(icon!=null){c.save();icon.setBounds((int)x+19,531,(int)x+58,569);icon.draw(c);c.restore();}
                else {fill(0xff34343a);c.drawRoundRect(new RectF(x+19,531,x+58,569),10,10,p);drawDockSymbol(c,names[i],x+38,550);}
                String label=names[i];try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
                if(label.length()>11)label=label.substring(0,10)+"…";text(c,label,x+13,585,12,WHITE,false);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent ev){
            float x=ev.getX()/(getWidth()/1024f),y=ev.getY()/(getHeight()/600f);
            if(ev.getAction()==MotionEvent.ACTION_DOWN){
                lastTouch=SystemClock.elapsedRealtime();
                if(screenDimmed||screensaver){wakeScreen();return true;}
                downAt=SystemClock.elapsedRealtime();downX=x;downY=y;hit=-1;dragging=false;
                for(int i=boxes.length-1;i>=0;i--)if(boxes[i].contains(x,y)){hit=i;grabX=x-boxes[i].left;grabY=y-boxes[i].top;break;}
                return true;
            }else if(ev.getAction()==MotionEvent.ACTION_MOVE){
                if(hit>=0 && editMode && Math.hypot(x-downX,y-downY)>8){
                    dragging=true;RectF b=boxes[hit];b.offsetTo(Math.max(108,Math.min(1018-b.width(),x-grabX)),Math.max(55,Math.min(511-b.height(),y-grabY)));invalidate();
                }
                return true;
            }else if(ev.getAction()==MotionEvent.ACTION_UP){
                lastTouch=SystemClock.elapsedRealtime();
                if(dragging&&hit>=0){RectF b=boxes[hit];prefs.edit().putFloat(ids[hit]+"_x",b.left).putFloat(ids[hit]+"_y",b.top).apply();SoundEffects.playSelect();return true;}
                if(x<110){SoundEffects.playSelect();if(y>440)openSettings();else if(y>335)showAllApps();else if(y>240)launch("com.spotify.music");else if(y>145)launch("com.google.android.apps.maps");return true;}
                if(y>510){
                    SoundEffects.playSelect();if(x>874)showAllApps();else{int i=Math.min(5,Math.max(0,(int)((x-120)/125)));String[] pkg={"com.google.android.apps.maps","com.waze","com.google.android.youtube","com.spotify.music","com.netflix.mediaclient","com.android.fmradio"};launch(prefs.getString("dock_"+i,pkg[i]));}
                }else if(hit==3){
                    SoundEffects.playSelect();
                    RectF b=boxes[3];float rx=x-b.left;
                    if(rx>235)NowPlayingManager.skipToNext();else if(rx>166)NowPlayingManager.togglePlayPause();else if(rx>105)NowPlayingManager.skipToPrevious();else NowPlayingManager.openMediaApp(DriveXActivity.this);
                }else if(hit==0){SoundEffects.playSelect();options("Gaya speedometer",new String[]{"RS Analog","Digital Minimal"},"gauge",new String[]{"rs","digital"});}
                else if(hit==1){SoundEffects.playSelect();pickPhoto();}
                else if(hit==2){SoundEffects.playSelect();showZonePicker();}
                else if(hit==4){SoundEffects.playSelect();startActivity(new Intent(DriveXActivity.this,WeatherActivity.class));}
                invalidate();return true;
            }
            return true;
        }
    }
}
