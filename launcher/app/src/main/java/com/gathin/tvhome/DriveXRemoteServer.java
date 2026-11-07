package com.gathin.tvhome;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Opt-in LAN-only Safari settings page. Never forwarded to the internet. */
final class DriveXRemoteServer {
    private final DriveXActivity activity;
    private final SharedPreferences prefs;
    private final Runnable changed;
    private final String pin;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile ServerSocket server;
    private volatile String address="";
    DriveXRemoteServer(DriveXActivity host, SharedPreferences p, Runnable callback){
        activity=host;prefs=p;changed=callback;
        pin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
    }
    void start(){
        if(running)return;
        new Thread(()->{
            try {
                address=localIp();
                if(address.isEmpty())return;
                server=new ServerSocket(8765,16,InetAddress.getByName(address));
                running=true;
                while(running){
                    try(Socket socket=server.accept()){
                        socket.setSoTimeout(3000);
                        handle(socket);
                    }catch(Exception ignored){}
                }
            }catch(Exception e){running=false;}
        },"DriveX-local-remote").start();
    }
    void close(){
        running=false;try{if(server!=null)server.close();}catch(Exception ignored){}
    }
    String getInstructions(){
        return address.isEmpty()?
           "Pastikan iPhone dan Android player berada pada Wi-Fi sama. Buka semula halaman ini selepas Wi-Fi tersedia.":
           "Buka Safari: http://"+address+":8765\nPIN pasangan: "+pin+
           "\nSambungan tempatan sahaja. Jangan gunakan pada Wi-Fi awam.";
    }
    private static String localIp() throws Exception{
        Enumeration<NetworkInterface> ifaces=NetworkInterface.getNetworkInterfaces();
        String fallback="";
        while(ifaces.hasMoreElements()){
            NetworkInterface n=ifaces.nextElement();if(!n.isUp()||n.isLoopback())continue;
            Enumeration<InetAddress> a=n.getInetAddresses();
            while(a.hasMoreElements()){
                InetAddress ip=a.nextElement();
                if(ip instanceof Inet4Address && ip.isSiteLocalAddress()){
                    if(n.getName().startsWith("wlan")||n.getName().startsWith("ap"))return ip.getHostAddress();
                    if(fallback.isEmpty())fallback=ip.getHostAddress();
                }
            }
        }
        return fallback;
    }
    private void handle(Socket socket) throws Exception{
        BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
        String first=in.readLine();if(first==null)return;
        String[] req=first.split(" ");if(req.length<2)return;
        String method=req[0];int len=0;
        String line;
        while((line=in.readLine())!=null&&!line.isEmpty()){
            if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")){
                try{len=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());}catch(Exception ignored){}
            }
        }
        if(len<0||len>2048){respond(socket,413,"Request too large");return;}
        if("POST".equals(method) && "/change".equals(req[1])){
            char[] body=new char[len];int count=0;
            while(count<len){int n=in.read(body,count,len-count);if(n<0)break;count+=n;}
            Map<String,String> args=form(new String(body,0,count));
            if(!pin.equals(args.get("pin"))){respond(socket,403,"PIN salah");return;}
            String key=args.getOrDefault("key",""),value=args.getOrDefault("value","");
            if(!isAllowed(key,value)){respond(socket,400,"Tetapan tidak sah");return;}
            ui.post(()->{activity.applyRemoteChange(key,value);changed.run();});
            respond(socket,200,"Tetapan diterima. <a href='/'>Kembali</a>");
        }else if("GET".equals(method) && "/".equals(req[1])){
            respond(socket,200,page());
        }else respond(socket,404,"Not found");
    }
    private static boolean isAllowed(String key,String value){
        switch(key){
            case "gauge":return value.equals("rs")||value.equals("digital");
            case "accent":return value.equals("red")||value.equals("silver")||value.equals("green");
            case "screensaver":return value.equals("digital")||value.equals("analog")||value.equals("10")||value.equals("off");
            case "move":{
                String[] v=value.split(",");
                if(v.length!=3)return false;
                try{int i=Integer.parseInt(v[0]);float x=Float.parseFloat(v[1]),y=Float.parseFloat(v[2]);
                    return i>=0&&i<=4&&Float.isFinite(x)&&Float.isFinite(y)&&x>=0&&y>=0&&x<=1024&&y<=600;
                }catch(Exception e){return false;}
            }
        }
        return false;
    }
    private static Map<String,String> form(String body)throws Exception{
        Map<String,String> out=new HashMap<>();
        for(String kv:body.split("&")){
            String[] parts=kv.split("=",2);
            if(parts.length==2)out.put(URLDecoder.decode(parts[0],"UTF-8"),URLDecoder.decode(parts[1],"UTF-8"));
        }
        return out;
    }
    private static void respond(Socket s,int code,String html)throws Exception{
        String body="<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"+html;
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8));
        out.write("HTTP/1.1 "+code+" OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n");
        out.write(body);out.flush();
    }
    private static String choice(String pin,String label,String key,String[] names,String[] values){
        StringBuilder s=new StringBuilder("<section><h3>").append(label).append("</h3>");
        for(int i=0;i<names.length;i++)s.append("<form method='post' action='/change'><input type='hidden' name='pin' value='")
          .append(pin).append("'><input type='hidden' name='key' value='").append(key)
          .append("'><input type='hidden' name='value' value='").append(values[i])
          .append("'><button>").append(names[i]).append("</button></form>");
        return s.append("</section>").toString();
    }
    private String page(){
        StringBuilder s=new StringBuilder("<title>MEKXZ DRIVE X Remote</title><style>body{background:#050506;color:#f5f5f7;font:16px -apple-system,BlinkMacSystemFont,system-ui;margin:18px}h1{color:#fff}section{background:#18181a;border:1px solid #777;border-radius:18px;padding:15px;margin:14px 0}button{background:#28282b;border:1px solid #bbb;color:white;border-radius:12px;padding:13px;margin:5px;min-width:95px}input{background:#27272b;border:1px solid #888;border-radius:9px;color:white;padding:12px;width:80px}small{color:#aaa}</style>");
        s.append("<h1>MEKXZ DRIVE <span style='color:#ee3344'>X</span></h1><small>Control Center • Safari / local Wi-Fi • PIN diperlukan untuk setiap perubahan</small>");
        s.append(choice(pin,"Speedometer","gauge",new String[]{"RS Analog","Digital"},new String[]{"rs","digital"}));
        s.append(choice(pin,"Tema","accent",new String[]{"Merah","Perak","Hijau"},new String[]{"red","silver","green"}));
        s.append(choice(pin,"Screensaver","screensaver",new String[]{"Digital","Analog","10 min","Off"},new String[]{"digital","analog","10","off"}));
        s.append("<section><h3>Susun widget</h3><small>1 Meter · 2 Kereta · 3 Solat · 4 Media · 5 Cuaca</small>");
        for(int i=0;i<5;i++)s.append("<form method='post' action='/change' onsubmit=''>Widget "+(i+1)+
            " <input type='hidden' name='pin' value='"+pin+"'><input type='hidden' name='key' value='move'>"+
            "<input type='text' name='value' placeholder='index,x,y' value='"+i+","+prefs.getFloat(new String[]{"speed_x","car_x","prayer_x","media_x","weather_x"}[i],new float[]{132,416,760,420,420}[i])+","+prefs.getFloat(new String[]{"speed_y","car_y","prayer_y","media_y","weather_y"}[i],new float[]{132,121,86,362,69}[i])+"'><button>Simpan</button></form>");
        s.append("</section><small>Remote hanya hidup semasa aplikasi aktif. Jangan kongsi PIN.</small>");
        return s.toString();
    }
}
