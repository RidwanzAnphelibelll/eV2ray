package com.rscoders.v2ray;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import go.Seq;
import libv2ray.Libv2ray;
import libv2ray.V2RayPoint;
import libv2ray.V2RayVPNServiceSupportsSet;

public class VpnService extends android.net.VpnService {

    public static final String ACTION_START = "com.rscoders.v2ray.START";
    public static final String ACTION_STOP = "com.rscoders.v2ray.STOP";
    public static final String BROADCAST_STATS = "com.rscoders.v2ray.STATS";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_UP_SPEED = "up_speed";
    public static final String EXTRA_DOWN_SPEED = "down_speed";
    public static final String EXTRA_DURATION = "duration";

    private static final String CHANNEL_ID = "ev2ray_vpn";
    private static final int NOTIF_ID = 1001;
    private static final int SOCKS_PORT = 10808;

    private ParcelFileDescriptor tunInterface;
    private Process tun2SocksProcess;
    private V2RayPoint v2RayPoint;
    private boolean isRunning = false;
    private String currentRemark = "eV2ray";

    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;

    private int seconds, minutes, hours;
    private long totalUp, totalDown, upSpeed, downSpeed;
    private final Handler statsHandler = new Handler(Looper.getMainLooper());

    private final Runnable statsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            seconds++;
            if (seconds >= 60) { seconds = 0; minutes++; }
            if (minutes >= 60) { minutes = 0; hours++; }
            if (v2RayPoint != null && v2RayPoint.getIsRunning()) {
                downSpeed = v2RayPoint.queryStats("proxy", "downlink") + v2RayPoint.queryStats("block", "downlink");
                upSpeed = v2RayPoint.queryStats("proxy", "uplink") + v2RayPoint.queryStats("block", "uplink");
                totalDown += downSpeed;
                totalUp += upSpeed;
            }
            String duration = pad(hours) + ":" + pad(minutes) + ":" + pad(seconds);
            Intent stats = new Intent(BROADCAST_STATS);
            stats.setPackage(getPackageName());
            stats.putExtra(EXTRA_STATE, "CONNECTED");
            stats.putExtra(EXTRA_UP_SPEED, upSpeed);
            stats.putExtra(EXTRA_DOWN_SPEED, downSpeed);
            stats.putExtra(EXTRA_DURATION, duration);
            sendBroadcast(stats);
            updateNotification(currentRemark, duration);
            statsHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopVpn();
            stopSelf();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_START.equals(intent.getAction())) {
            currentRemark = intent.getStringExtra("remark") != null ? intent.getStringExtra("remark") : "eV2ray";
            createChannel();
            startForeground(NOTIF_ID, buildNotification("Menghubungkan...", ""));
            IntentFilter f = new IntentFilter(ACTION_STOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, f, RECEIVER_EXPORTED);
            } else {
                registerReceiver(stopReceiver, f);
            }
            new Thread(this::startVpn, "vpn_start").start();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startVpn() {
        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
            Seq.setContext(this);

            Libv2ray.initV2Env("", "");

            v2RayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
                @Override public long shutdown() { stopVpn(); stopSelf(); return 0; }
                @Override public long prepare() { return 0; }
                @Override public boolean protect(long fd) { return VpnService.this.protect((int) fd); }
                @Override public long onEmitStatus(long l, String s) { return 0; }
                @Override public long setup(String s) { setupTunnel(); return 0; }
            }, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

            String json = ConfigManager.loadJson(this);
            v2RayPoint.setConfigureFileContent(json);
            v2RayPoint.setDomainName(extractServerAddress(json));
            v2RayPoint.runLoop(false);

        } catch (Exception e) {
            Log.e("VpnService", "startVpn", e);
            broadcastDisconnected();
            stopSelf();
        }
    }

    private void setupTunnel() {
        try {
            if (tunInterface != null) tunInterface.close();

            Builder builder = new Builder();
            builder.setSession(currentRemark);
            builder.setMtu(1500);
            builder.addAddress("26.26.26.1", 30);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("8.8.8.8");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false);

            tunInterface = builder.establish();
            if (tunInterface == null) { stopVpn(); stopSelf(); return; }

            setupNetworkCallback();
            startTun2Socks();

            isRunning = true;
            seconds = 0; minutes = 0; hours = 0;
            totalUp = 0; totalDown = 0;
            startForeground(NOTIF_ID, buildNotification(currentRemark, "00:00:00"));

            Intent state = new Intent(BROADCAST_STATS);
            state.setPackage(getPackageName());
            state.putExtra(EXTRA_STATE, "CONNECTED");
            state.putExtra(EXTRA_UP_SPEED, 0L);
            state.putExtra(EXTRA_DOWN_SPEED, 0L);
            state.putExtra(EXTRA_DURATION, "00:00:00");
            sendBroadcast(state);

            statsHandler.postDelayed(statsRunnable, 1000);

        } catch (Exception e) {
            Log.e("VpnService", "setupTunnel", e);
            stopVpn();
            stopSelf();
        }
    }

    private void setupNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                setUnderlyingNetworks(new Network[]{network});
            }
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                setUnderlyingNetworks(new Network[]{network});
            }
            @Override
            public void onLost(Network network) {
                setUnderlyingNetworks(null);
            }
        };
        try {
            connectivity.requestNetwork(request, networkCallback);
        } catch (Exception ignored) {}
    }

    private void unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && networkCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
            networkCallback = null;
        }
    }

    private void startTun2Socks() {
        try {
            if (tun2SocksProcess != null) { tun2SocksProcess.destroy(); tun2SocksProcess = null; }

            ArrayList<String> cmds = new ArrayList<>(Arrays.asList(
                new File(getApplicationInfo().nativeLibraryDir, "libtun2socks.so").getAbsolutePath(),
                "--netif-ipaddr", "26.26.26.2",
                "--netif-netmask", "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:" + SOCKS_PORT,
                "--tunmtu", "1500",
                "--sock-path", "sock_path",
                "--enable-udprelay",
                "--loglevel", "notice"
            ));

            ProcessBuilder pb = new ProcessBuilder(cmds);
            pb.redirectErrorStream(true);
            tun2SocksProcess = pb.directory(getFilesDir()).start();

            new Thread(() -> {
                try {
                    Scanner sc = new Scanner(tun2SocksProcess.getInputStream());
                    while (sc.hasNextLine()) Log.d("tun2socks", sc.nextLine());
                } catch (Exception ignored) {}
            }, "t2s_log").start();

            new Thread(() -> {
                try {
                    tun2SocksProcess.waitFor();
                    Log.d("tun2socks", "exited");
                    if (isRunning && tunInterface != null) {
                        Log.d("tun2socks", "restart");
                        startTun2Socks();
                    }
                } catch (Exception ignored) {}
            }, "t2s_watch").start();

            sendFileDescriptor();

        } catch (Exception e) {
            Log.e("VpnService", "startTun2Socks", e);
        }
    }

    private void sendFileDescriptor() {
        if (tunInterface == null) return;
        FileDescriptor tunFd = tunInterface.getFileDescriptor();
        String sockPath = new File(getFilesDir(), "sock_path").getAbsolutePath();
        new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(50L * i);
                    LocalSocket socket = new LocalSocket();
                    socket.connect(new LocalSocketAddress(sockPath, LocalSocketAddress.Namespace.FILESYSTEM));
                    OutputStream out = socket.getOutputStream();
                    socket.setFileDescriptorsForSend(new FileDescriptor[]{tunFd});
                    out.write(42);
                    socket.shutdownOutput();
                    socket.close();
                    break;
                } catch (Exception ignored) {}
            }
        }, "sendFd").start();
    }

    private void stopVpn() {
        isRunning = false;
        statsHandler.removeCallbacks(statsRunnable);
        unregisterNetworkCallback();
        if (v2RayPoint != null) {
            try { if (v2RayPoint.getIsRunning()) v2RayPoint.stopLoop(); } catch (Exception ignored) {}
            v2RayPoint = null;
        }
        if (tun2SocksProcess != null) { tun2SocksProcess.destroy(); tun2SocksProcess = null; }
        if (tunInterface != null) {
            try { tunInterface.close(); } catch (Exception ignored) {}
            tunInterface = null;
        }
        broadcastDisconnected();
    }

    private void broadcastDisconnected() {
        Intent i = new Intent(BROADCAST_STATS);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_STATE, "DISCONNECTED");
        i.putExtra(EXTRA_UP_SPEED, 0L);
        i.putExtra(EXTRA_DOWN_SPEED, 0L);
        i.putExtra(EXTRA_DURATION, "00:00:00");
        sendBroadcast(i);
    }

    private String extractServerAddress(String json) {
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray outbounds = root.getJSONArray("outbounds");
            org.json.JSONObject settings = outbounds.getJSONObject(0).getJSONObject("settings");
            try {
                org.json.JSONObject s = settings.getJSONArray("vnext").getJSONObject(0);
                return s.getString("address") + ":" + s.getInt("port");
            } catch (Exception e) {
                org.json.JSONObject s = settings.getJSONArray("servers").getJSONObject(0);
                return s.getString("address") + ":" + s.getInt("port");
            }
        } catch (Exception e) {
            return "127.0.0.1:443";
        }
    }

    private String pad(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "eV2ray VPN", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String duration) {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;
        Intent stopIntent = new Intent(ACTION_STOP).setPackage(getPackageName());
        PendingIntent stopPi = PendingIntent.getBroadcast(this, 0, stopIntent, flags);
        Intent openIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent openPi = PendingIntent.getActivity(this, 1, openIntent, flags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(duration.isEmpty() ? "Menghubungkan..." : "Durasi: " + duration)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Putuskan", stopPi)
            .setOngoing(true)
            .setShowWhen(false)
            .build();
    }

    private void updateNotification(String title, String duration) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(title, duration));
    }

    @Override
    public void onRevoke() { stopVpn(); stopSelf(); }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(stopReceiver); } catch (Exception ignored) {}
        stopVpn();
        stopForeground(true);
        super.onDestroy();
    }

    public static void start(Context ctx, String remark) {
        Intent i = new Intent(ctx, VpnService.class);
        i.setAction(ACTION_START);
        i.putExtra("remark", remark);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, VpnService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }
}
