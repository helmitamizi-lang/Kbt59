package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Native live-preview appearance editor. Settings are shared with DriveXActivity. */
public final class DriveXThemeStudioActivity extends Activity {
    private SharedPreferences prefs;
    private Preview preview;
    private LinearLayout controls;
    private final int[] accents={0xffed313f,0xffeeeeee,0xffefb24b,0xffa98af0,0xff60dba3};
    private final String[] labels={"RS Red","Silver","Amber","Purple","Green"};
    private final String[] codes={"red","silver","amber","purple","green"};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("mekxz_drive_x",MODE_PRIVATE);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(0xff050505);
        preview=new Preview();root.addView(preview,new LinearLayout.LayoutParams(0,-1,1.5f));
        ScrollView scroll=new ScrollView(this);controls=new LinearLayout(this);controls.setOrientation(1);controls.setPadding(18,12,18,24);
        scroll.addView(controls);root.addView(scroll,new LinearLayout.LayoutParams(0,-1,1f));setContentView(root);
        heading("MEKXZ • THEME STUDIO");subtitle("Live preview • perubahan terus disimpan");
        heading("Warna accent");
        for(int i=0;i<codes.length;i++){final String code=codes[i];button(labels[i],()->set("accent",code));}
        heading("Panel appearance");
        seek("Glass opacity", "glass",20,95,65);
        seek("Corner radius", "radius",4,32,18);
        seek("Border glow", "glow",0,100,65);
        seek("Background darkness", "darkness",65,100,95);
        heading("Speedometer skin");
        for(String[] a:new String[][]{{"AMG RS Analog","rs"},{"AMG Digital","digital"},{"Minimal","minimal"},{"Classic","classic"},{"Performance","performance"}})button(a[0],()->set("gauge",a[1]));
        heading("Homescreen");
        button("Buka widget editor",()->{prefs.edit().putBoolean("edit_layout",true).apply();finish();});
        button("Restore Factory Theme",()->new AlertDialog.Builder(this).setTitle("Restore Factory Theme?")
            .setMessage("Warna, panel dan gaya meter akan kembali ke asal. Gambar kereta dan susunan widget tidak dipadam.")
            .setNegativeButton("Batal",null).setPositiveButton("Restore",(d,w)->{
                SharedPreferences.Editor e=prefs.edit();
                for(String k:new String[]{"accent","glass","radius","glow","darkness","gauge"})e.remove(k);
                e.apply();preview.invalidate();
            }).show());
        button("Selesai",this::finish);
    }
    private void heading(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(20);t.setTextColor(-1);t.setTypeface(null,1);t.setPadding(0,16,0,7);controls.addView(t);}
    private void subtitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(12);t.setTextColor(0xffb4b4b4);controls.addView(t);}
    private void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(-1);b.setAllCaps(false);b.setOnClickListener(v->r.run());controls.addView(b,new LinearLayout.LayoutParams(-1,48));}
    private void seek(String name,String key,int min,int max,int def){
        TextView t=new TextView(this);t.setText(name+" • "+prefs.getInt(key,def));t.setTextColor(-1);t.setTextSize(14);controls.addView(t);
        SeekBar b=new SeekBar(this);b.setMax(max-min);b.setProgress(prefs.getInt(key,def)-min);
        b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean from){int v=value+min;t.setText(name+" • "+v);if(from){prefs.edit().putInt(key,v).apply();preview.invalidate();}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });controls.addView(b);
    }
    private void set(String k,String v){prefs.edit().putString(k,v).apply();preview.invalidate();}
    private final class Preview extends View{
        private final Paint p=new Paint(3);
        Preview(){super(DriveXThemeStudioActivity.this);}
        private void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);}
        private void label(Canvas c,String s,float x,float y,int size,int col){color(col);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create("sans-serif",1));c.drawText(s,x,y,p);}
        @Override protected void onDraw(Canvas canvas){
            canvas.save();canvas.scale(getWidth()/560f,getHeight()/360f);
            int dark=prefs.getInt("darkness",95);
            int bg=Color.rgb((100-dark)/2,(100-dark)/2,(100-dark)/2);
            canvas.drawColor(bg);
            String accent=prefs.getString("accent","red");
            int a=accents[0];for(int i=0;i<codes.length;i++)if(codes[i].equals(accent))a=accents[i];
            label(canvas,"MEKXZ DRIVE",18,32,22,-1);label(canvas,"X",197,32,23,a);
            int radius=prefs.getInt("radius",18);
            int alpha=(int)(prefs.getInt("glass",65)*2.55);
            RectF[] cards={new RectF(15,56,210,285),new RectF(218,56,398,185),new RectF(406,56,547,285),new RectF(218,193,398,285)};
            for(RectF r:cards){
                color(Color.argb(alpha,33,33,36));canvas.drawRoundRect(r,radius,radius,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(a);
                canvas.drawRoundRect(r,radius,radius,p);p.setStyle(Paint.Style.FILL);
            }
            label(canvas,"GPS SPEED",43,90,14,0xffc8c8c8);
            String gauge=prefs.getString("gauge","rs");
            color(a);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);
            canvas.drawArc(45,110,185,250,140,265,false,p);p.setStyle(Paint.Style.FILL);
            label(canvas,"82",81,194,51,-1);label(canvas,"km/h",91,222,13,0xffbbbbbb);
            label(canvas,gauge.toUpperCase(Locale.ROOT),67,266,12,a);
            label(canvas,"TOYOTA VIOS",236,93,18,-1);label(canvas,"KBT 59",236,122,15,a);
            label(canvas,"CAR VISUAL",236,163,12,0xff999999);
            label(canvas,"WAKTU SOLAT",417,88,15,-1);
            label(canvas,"Subuh  05:52",418,127,12,0xffdedede);
            label(canvas,"Zohor  13:08",418,157,12,0xffdedede);
            label(canvas,"Asar   16:25",418,187,12,0xffdedede);
            label(canvas,"Maghrib 19:00",414,218,12,a);
            label(canvas,"Isyak  20:12",418,248,12,0xffdedede);
            label(canvas,"NOW PLAYING",231,222,15,-1);label(canvas,"♫  Media Player",231,255,14,0xffdddddd);
            RectF dock=new RectF(15,300,547,350);color(0xff242426);canvas.drawRoundRect(dock,14,14,p);
            label(canvas,"MAPS       MUSIC       RADIO       APPS       SETTINGS",37,332,15,-1);
            canvas.restore();
        }
    }
}
