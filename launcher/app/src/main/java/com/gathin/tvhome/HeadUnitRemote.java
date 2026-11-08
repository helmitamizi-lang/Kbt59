package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/** Local-only, PIN-protected Safari remote. Requires same Wi-Fi network. */
final class HeadUnitRemote {
    private final MainActivity activity;
    private ServerSocket socket;
    private Thread worker;
    private volatile boolean running;
    private final String pin;

    HeadUnitRemote(MainActivity activity) {
        this.activity = activity;
        SharedPreferences prefs = activity.getSharedPreferences("remote", Context.MODE_PRIVATE);
        String saved = prefs.getString("pin", null);
        if (saved == null) {
            saved = String.format(Locale.US, "%06d", new SecureRandom().nextInt(1000000));
            prefs.edit().putString("pin", saved).apply();
        }
        pin = saved;
    }

    String getPin() { return pin; }
    int getPort() { return socket == null ? -1 : socket.getLocalPort(); }

    void start() {
        try {
            socket = new ServerSocket(8765);
            running = true;
            worker = new Thread(() -> {
                while (running) {
                    try { handle(socket.accept()); }
                    catch (IOException e) { if (!running) break; }
                }
            }, "mekxz-remote");
            worker.start();
        } catch (IOException ignored) { }
    }

    void stop() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.length() > 2048) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], target = parts[1];
            // Read remaining HTTP headers, without accepting request bodies.
            int count = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty() && count++ < 80) {}
            String path = target.split("\\?")[0];
            if ("GET".equals(method) && "/".equals(path)) {
                respond(s, 200, "text/html", page());
            } else if ("POST".equals(method) && path.startsWith("/action/")) {
                String query = target.contains("?") ? target.substring(target.indexOf('?') + 1) : "";
                String supplied = "";
                for (String pair : query.split("&")) {
                    if (pair.startsWith("pin=")) supplied = pair.substring(4);
                }
                if (!pin.equals(supplied)) { respond(s, 403, "text/plain", "Invalid PIN"); return; }
                String command = path.substring("/action/".length());
                if (!(command.equals("home") || command.equals("apps") || command.equals("settings") ||
                        command.equals("previous") || command.equals("play") || command.equals("next"))) {
                    respond(s, 404, "text/plain", "Unknown action"); return;
                }
                activity.remoteAction(command);
                respond(s, 200, "text/plain", "OK");
            } else respond(s, 404, "text/plain", "Not found");
        } catch (Exception ignored) {}
    }

    private void respond(Socket s, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = s.getOutputStream();
        String header = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Error") + "\r\n" +
                "Content-Type: " + type + "; charset=utf-8\r\n" +
                "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'\r\n" +
                "Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n" +
                "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String page() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<title>MEKXZ Remote</title><style>body{font-family:-apple-system,sans-serif;background:#0b1220;color:white;text-align:center;padding:24px}" +
                "button,input{font-size:20px;border-radius:14px;padding:16px;margin:6px;background:#27354b;color:white;border:1px solid #65758c}" +
                "button{min-width:115px}</style></head><body><h2>MEKXZ DRIVE X</h2><p>Safari Remote</p>" +
                "<input id='pin' type='password' inputmode='numeric' maxlength='6' placeholder='6-digit PIN'><div>" +
                "<button onclick=\"send('home')\">Home</button><button onclick=\"send('apps')\">All Apps</button>" +
                "<button onclick=\"send('settings')\">Settings</button></div><div>" +
                "<button onclick=\"send('previous')\">Previous</button><button onclick=\"send('play')\">Play/Pause</button>" +
                "<button onclick=\"send('next')\">Next</button></div><p id='status'></p>" +
                "<script>async function send(cmd){let pin=document.getElementById('pin').value;" +
                "if(!/^\\d{6}$/.test(pin)){document.getElementById('status').textContent='Enter 6-digit PIN';return;}" +
                "try{let r=await fetch('/action/'+cmd+'?pin='+encodeURIComponent(pin),{method:'POST'});" +
                "document.getElementById('status').textContent=r.ok?'Sent':'Invalid PIN / unavailable';}" +
                "catch(e){document.getElementById('status').textContent='Connection failed';}}</script></body></html>";
    }
}
