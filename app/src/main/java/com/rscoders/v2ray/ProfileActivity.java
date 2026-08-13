package com.rscoders.v2ray;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.rscoders.v2ray.databinding.ActivityProfileBinding;
import com.rscoders.v2ray.model.ProxyProfile;

import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding b;
    private ProxyProfile editingProfile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupProtocolSpinner();
        setupNetworkSpinner();

        String profileId = getIntent().getStringExtra("profile_id");
        if (profileId != null) {
            List<ProxyProfile> list = ConfigManager.loadProfiles(this);
            for (ProxyProfile p : list) {
                if (p.id.equals(profileId)) {
                    editingProfile = p;
                    break;
                }
            }
            if (editingProfile != null) populateForm(editingProfile);
        }

        b.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void setupProtocolSpinner() {
        String[] protocols = {"vmess", "vless", "trojan", "shadowsocks"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, protocols);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerProtocol.setAdapter(adapter);
        b.spinnerProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateVisibility(protocols[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupNetworkSpinner() {
        String[] networks = {"ws", "grpc", "tcp", "h2", "quic"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, networks);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.spinnerNetwork.setAdapter(adapter);
        b.spinnerNetwork.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateNetworkFields(networks[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateVisibility(String proto) {
        boolean isTrojan = "trojan".equalsIgnoreCase(proto);
        boolean isVless = "vless".equalsIgnoreCase(proto);
        boolean isSS = "shadowsocks".equalsIgnoreCase(proto);

        b.layoutUuid.setVisibility((isTrojan || isSS) ? View.GONE : View.VISIBLE);
        b.layoutPassword.setVisibility(isTrojan ? View.VISIBLE : View.GONE);
        b.layoutAlterId.setVisibility("vmess".equalsIgnoreCase(proto) ? View.VISIBLE : View.GONE);
        b.layoutFlow.setVisibility(isVless ? View.VISIBLE : View.GONE);
        b.layoutSecurity.setVisibility("vmess".equalsIgnoreCase(proto) ? View.VISIBLE : View.GONE);
        b.layoutSsMethod.setVisibility(isSS ? View.VISIBLE : View.GONE);
        b.layoutSsPassword.setVisibility(isSS ? View.VISIBLE : View.GONE);
        b.layoutNetwork.setVisibility(isSS ? View.GONE : View.VISIBLE);
        b.layoutTls.setVisibility(isSS ? View.GONE : View.VISIBLE);
        b.layoutHost.setVisibility(isSS ? View.GONE : b.layoutHost.getVisibility());
        b.layoutPath.setVisibility(isSS ? View.GONE : b.layoutPath.getVisibility());
        b.layoutGrpcService.setVisibility(isSS ? View.GONE : b.layoutGrpcService.getVisibility());
        b.layoutSni.setVisibility(isSS ? View.GONE : View.VISIBLE);
        b.layoutMux.setVisibility(isSS ? View.GONE : View.VISIBLE);
    }

    private void updateNetworkFields(String net) {
        boolean ws = "ws".equalsIgnoreCase(net);
        boolean grpc = "grpc".equalsIgnoreCase(net);
        boolean h2 = "h2".equalsIgnoreCase(net);
        b.layoutHost.setVisibility((ws || h2) ? View.VISIBLE : View.GONE);
        b.layoutPath.setVisibility((ws || h2) ? View.VISIBLE : View.GONE);
        b.layoutGrpcService.setVisibility(grpc ? View.VISIBLE : View.GONE);
    }

    private void populateForm(ProxyProfile p) {
        String[] protos = {"vmess", "vless", "trojan", "shadowsocks"};
        for (int i = 0; i < protos.length; i++) {
            if (protos[i].equalsIgnoreCase(p.protocol)) {
                b.spinnerProtocol.setSelection(i);
                break;
            }
        }
        b.etName.setText(p.name);
        b.etAddress.setText(p.address);
        b.etPort.setText(String.valueOf(p.port));
        b.etUuid.setText(p.uuid);
        b.etPassword.setText(p.password);
        b.etAlterId.setText(String.valueOf(p.alterId));
        b.switchMux.setChecked(p.mux);
        b.switchTls.setChecked(p.tls);
        b.switchInsecure.setChecked(p.insecure);
        b.etHost.setText(p.host);
        b.etPath.setText(p.path);
        b.etSni.setText(p.sni);
        b.etFlow.setText(p.flow);
        b.etSecurity.setText(p.security);
        b.etGrpcService.setText(p.grpcServiceName);
        b.etSsMethod.setText(p.ssMethod);
        b.etSsPassword.setText(p.ssPassword);

        String[] nets = {"ws", "grpc", "tcp", "h2", "quic"};
        for (int i = 0; i < nets.length; i++) {
            if (nets[i].equalsIgnoreCase(p.network)) {
                b.spinnerNetwork.setSelection(i);
                break;
            }
        }
    }

    private void saveProfile() {
        String address = b.etAddress.getText().toString().trim();
        String portStr = b.etPort.getText().toString().trim();

        if (address.isEmpty()) {
            Toast.makeText(this, "Alamat server wajib diisi!", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Port tidak valid!", Toast.LENGTH_SHORT).show();
            return;
        }

        ProxyProfile p = editingProfile != null ? editingProfile : new ProxyProfile();
        p.protocol = b.spinnerProtocol.getSelectedItem().toString();
        p.name = b.etName.getText().toString().trim();
        p.address = address;
        p.port = port;
        p.uuid = b.etUuid.getText().toString().trim();
        p.password = b.etPassword.getText().toString().trim();
        p.network = b.spinnerNetwork.getSelectedItem().toString();
        p.mux = b.switchMux.isChecked();
        p.tls = b.switchTls.isChecked();
        p.insecure = b.switchInsecure.isChecked();
        p.host = b.etHost.getText().toString().trim();
        p.path = b.etPath.getText().toString().trim();
        p.sni = b.etSni.getText().toString().trim();
        p.flow = b.etFlow.getText().toString().trim();
        p.grpcServiceName = b.etGrpcService.getText().toString().trim();
        p.ssMethod = b.etSsMethod.getText().toString().trim();
        p.ssPassword = b.etSsPassword.getText().toString().trim();
        try {
            p.alterId = Integer.parseInt(b.etAlterId.getText().toString().trim());
        } catch (NumberFormatException e) { p.alterId = 0; }
        p.security = b.etSecurity.getText().toString().trim();
        if (p.security.isEmpty()) p.security = "auto";

        if (editingProfile != null) {
            ConfigManager.updateProfile(this, p);
            Toast.makeText(this, "Profil diperbarui!", Toast.LENGTH_SHORT).show();
        } else {
            ConfigManager.addProfile(this, p);
            Toast.makeText(this, "Profil ditambahkan!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
