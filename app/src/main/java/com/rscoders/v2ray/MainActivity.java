package com.rscoders.v2ray;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.rscoders.v2ray.databinding.ActivityMainBinding;
import com.rscoders.v2ray.model.ProxyProfile;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;
    private boolean isConnected = false;
    private boolean isMeasuringPing = false;
    private ProfileAdapter profileAdapter;
    private ActivityResultLauncher<Intent> vpnPermLauncher;

    private void showToast(String msg, int duration) {
        Toast toast = Toast.makeText(this, msg, duration);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 200);
        toast.show();
    }

    private final BroadcastReceiver statsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(com.rscoders.v2ray.VpnService.EXTRA_STATE);
            if ("DISCONNECTED".equals(state)) {
                isConnected = false;
                runOnUiThread(() -> {
                    b.tvStatus.setText("Off");
                    b.tvStatus.setTextColor(getResources().getColor(R.color.status_off));
                    b.tvDownSpeed.setText("↓ 0 B/s");
                    b.tvUpSpeed.setText("↑ 0 B/s");
                    b.tvDuration.setText("00:00:00");
                    b.tvPing.setText("- ms");
                    b.speedGraph.setData(new long[0], new long[0]);
                    b.switchVpn.setOnCheckedChangeListener(null);
                    b.switchVpn.setChecked(false);
                    b.switchVpn.setEnabled(true);
                    b.switchVpn.setOnCheckedChangeListener((btn, checked) -> {
                        if (checked) requestConnect(); else disconnect();
                    });
                });
                return;
            }
            long upSpeed = intent.getLongExtra(com.rscoders.v2ray.VpnService.EXTRA_UP_SPEED, 0);
            long downSpeed = intent.getLongExtra(com.rscoders.v2ray.VpnService.EXTRA_DOWN_SPEED, 0);
            String duration = intent.getStringExtra(com.rscoders.v2ray.VpnService.EXTRA_DURATION);
            runOnUiThread(() -> {
                b.tvDownSpeed.setText("↓ " + formatSpeed(downSpeed));
                b.tvUpSpeed.setText("↑ " + formatSpeed(upSpeed));
                if (duration != null) b.tvDuration.setText(duration);
                b.speedGraph.pushData(downSpeed, upSpeed);
            });
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        vpnPermLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            r -> { if (r.getResultCode() == RESULT_OK) doConnect(); else resetToggle(); }
        );

        setupTabs();
        setupHome();
        showPage(0);

        IntentFilter filter = new IntentFilter(com.rscoders.v2ray.VpnService.BROADCAST_STATS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statsReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(statsReceiver, filter);
        }
    }

    private void setupTabs() {
        for (String t : new String[]{"Home", "V2ray", "About"})
            b.tabLayout.addTab(b.tabLayout.newTab().setText(t));
        b.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (pos == 1) {
                    startActivity(new Intent(MainActivity.this, EditorActivity.class));
                    b.tabLayout.selectTab(b.tabLayout.getTabAt(0));
                } else {
                    showPage(pos);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });
    }

    private void showPage(int pos) {
        b.pageHome.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
        b.pageAbout.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
    }

    private void setupHome() {
        profileAdapter = new ProfileAdapter(
            ConfigManager.loadProfiles(this),
            ConfigManager.getActiveId(this),
            new ProfileAdapter.Listener() {
                @Override
                public void onClick(ProxyProfile p, int pos) {
                    ConfigManager.setActiveProfile(MainActivity.this, p);
                    refreshProfiles();
                    showToast("Aktif: " + p.getDisplayName(), Toast.LENGTH_SHORT);
                }
                @Override
                public void onLongClick(ProxyProfile p, int pos) { showProfileMenu(p); }
            }
        );
        b.rvProfiles.setLayoutManager(new LinearLayoutManager(this));
        b.rvProfiles.setAdapter(profileAdapter);

        b.switchVpn.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) requestConnect(); else disconnect();
        });

        b.fabAdd.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        b.fabAdd.setOnLongClickListener(v -> { pasteFromClipboard(); return true; });
        b.tvPing.setOnClickListener(v -> measurePingManual());
    }

    private void requestConnect() {
        if (ConfigManager.loadProfiles(this).isEmpty()) {
            resetToggle();
            showToast("Tambahkan profil dulu!", Toast.LENGTH_SHORT);
            return;
        }
        Intent perm = VpnService.prepare(this);
        if (perm != null) vpnPermLauncher.launch(perm);
        else doConnect();
    }

    private void doConnect() {
        isConnected = true;
        b.tvStatus.setText("On");
        b.tvStatus.setTextColor(getResources().getColor(R.color.status_on));
        ProxyProfile active = ConfigManager.getActiveProfile(this);
        String remark = active != null ? active.getDisplayName() : "eV2ray";
        com.rscoders.v2ray.VpnService.start(this, remark);
        pingAfterConnect();
    }

    private void pingAfterConnect() {
        b.tvPing.setText("...");
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (Exception ignored) {}
            long delay = -1;
            try {
                delay = libv2ray.Libv2ray.measureOutboundDelay(ConfigManager.loadJson(this), "");
            } catch (Exception ignored) {}
            long finalDelay = delay;
            runOnUiThread(() ->
                b.tvPing.setText(finalDelay < 0 ? "- ms" : finalDelay + " ms")
            );
        }).start();
    }

    private void measurePingManual() {
        if (!isConnected || isMeasuringPing) return;
        isMeasuringPing = true;
        b.tvPing.setText("...");
        new Thread(() -> {
            long delay = -1;
            try {
                delay = libv2ray.Libv2ray.measureOutboundDelay(ConfigManager.loadJson(this), "");
            } catch (Exception ignored) {}
            long finalDelay = delay;
            runOnUiThread(() -> {
                b.tvPing.setText(finalDelay < 0 ? "- ms" : finalDelay + " ms");
                isMeasuringPing = false;
            });
        }).start();
    }

    private void resetToggle() {
        b.switchVpn.setOnCheckedChangeListener(null);
        b.switchVpn.setChecked(false);
        b.switchVpn.setEnabled(true);
        b.switchVpn.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) requestConnect(); else disconnect();
        });
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                showToast("Clipboard kosong!", Toast.LENGTH_SHORT);
                return;
            }
            String text = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
            ProxyProfile p = LinkParser.parse(text);
            if (p == null) {
                showToast("Format tidak dikenali!", Toast.LENGTH_SHORT);
                return;
            }
            ConfigManager.addProfile(this, p);
            refreshProfiles();
            showToast("Ditambahkan: " + p.getDisplayName(), Toast.LENGTH_SHORT);
        } catch (Exception e) {
            showToast("Gagal: " + e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void showProfileMenu(ProxyProfile p) {
        new AlertDialog.Builder(this)
            .setTitle(p.getDisplayName())
            .setItems(new String[]{"Edit", "Salin Link", "Hapus"}, (d, i) -> {
                if (i == 0) {
                    Intent intent = new Intent(this, ProfileActivity.class);
                    intent.putExtra("profile_id", p.id);
                    startActivity(intent);
                } else if (i == 1) {
                    String link = LinkExporter.export(p);
                    if (link == null) {
                        showToast("Gagal mengekspor link", Toast.LENGTH_SHORT);
                        return;
                    }
                    android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("link", link));
                    showToast("Link disalin!", Toast.LENGTH_SHORT);
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Hapus profil?")
                        .setPositiveButton("Hapus", (dd, w) -> {
                            ConfigManager.deleteProfile(this, p.id);
                            refreshProfiles();
                        })
                        .setNegativeButton("Batal", null).show();
                }
            }).show();
    }

    private void disconnect() {
        isConnected = false;
        b.tvStatus.setText("Off");
        b.tvStatus.setTextColor(getResources().getColor(R.color.status_off));
        b.tvPing.setText("- ms");
        com.rscoders.v2ray.VpnService.stop(this);
    }

    private String formatSpeed(long bps) {
        if (bps < 1024) return bps + " B/s";
        if (bps < 1024 * 1024) return String.format("%.1f KB/s", bps / 1024f);
        return String.format("%.2f MB/s", bps / (1024f * 1024));
    }

    private void refreshProfiles() {
        profileAdapter.update(ConfigManager.loadProfiles(this), ConfigManager.getActiveId(this));
    }

    @Override
    protected void onResume() { super.onResume(); refreshProfiles(); }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Tempel Link");
        menu.add(0, 2, 0, "Exit");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) { pasteFromClipboard(); return true; }
        if (item.getItemId() == 2) { disconnect(); finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(statsReceiver); } catch (Exception ignored) {}
    }
}
