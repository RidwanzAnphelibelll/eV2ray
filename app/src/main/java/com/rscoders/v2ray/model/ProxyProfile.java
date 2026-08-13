package com.rscoders.v2ray.model;

import java.util.UUID;

public class ProxyProfile {
    public String id = UUID.randomUUID().toString();
    public String protocol = "vmess";
    public String address = "";
    public int port = 443;
    public String uuid = "";
    public String password = "";
    public int alterId = 0;
    public String security = "auto";
    public boolean mux = false;
    public String network = "ws";
    public boolean tls = true;
    public boolean insecure = false;
    public String host = "";
    public String path = "/";
    public String sni = "";
    public String flow = "";
    public String name = "";
    public String grpcServiceName = "";
    public String ssMethod = "aes-128-gcm";
    public String ssPassword = "";

    public String getDisplayName() {
        if (name != null && !name.isEmpty()) return name;
        if (host != null && !host.isEmpty()) return host;
        return address + ":" + port;
    }

    public String getProtocolUpper() {
        if (protocol == null) return "VMESS";
        return protocol.toUpperCase();
    }
}
