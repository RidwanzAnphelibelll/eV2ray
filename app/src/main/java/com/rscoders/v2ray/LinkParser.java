package com.rscoders.v2ray;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rscoders.v2ray.model.ProxyProfile;

import java.net.URI;
import java.net.URLDecoder;

public class LinkParser {

    public static ProxyProfile parse(String link) {
        if (link == null) return null;
        link = link.trim();
        if (link.startsWith("vmess://")) return parseVmess(link);
        if (link.startsWith("vless://")) return parseVless(link);
        if (link.startsWith("trojan://")) return parseTrojan(link);
        if (link.startsWith("ss://")) return parseShadowsocks(link);
        return null;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static ProxyProfile parseVmess(String link) {
        try {
            String b64 = link.substring(8);
            String json = new String(Base64.decode(b64, Base64.DEFAULT));
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            ProxyProfile p = new ProxyProfile();
            p.protocol = "vmess";
            p.name = obj.has("ps") ? obj.get("ps").getAsString() : "";
            p.address = obj.has("add") ? obj.get("add").getAsString() : "";
            p.port = obj.has("port") ? parseInt(obj.get("port").getAsString(), 443) : 443;
            p.uuid = obj.has("id") ? obj.get("id").getAsString() : "";
            p.alterId = obj.has("aid") ? parseInt(obj.get("aid").getAsString(), 0) : 0;
            p.security = obj.has("scy") ? obj.get("scy").getAsString() : "auto";
            if (p.security == null || p.security.isEmpty()) p.security = "auto";
            p.network = obj.has("net") ? obj.get("net").getAsString() : "ws";
            if (p.network == null || p.network.isEmpty()) p.network = "ws";
            p.host = obj.has("host") ? obj.get("host").getAsString() : "";
            p.path = obj.has("path") ? obj.get("path").getAsString() : "/";
            String tls = obj.has("tls") ? obj.get("tls").getAsString() : "";
            p.tls = "tls".equals(tls);
            p.sni = obj.has("sni") ? obj.get("sni").getAsString() : "";
            if ("grpc".equalsIgnoreCase(p.network)) {
                p.grpcServiceName = (p.path != null && !p.path.isEmpty() && !p.path.equals("/")) ? p.path : "";
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static ProxyProfile parseVless(String link) {
        try {
            URI uri = new URI(link);
            ProxyProfile p = new ProxyProfile();
            p.protocol = "vless";
            p.uuid = uri.getUserInfo();
            p.address = uri.getHost();
            p.port = uri.getPort() > 0 ? uri.getPort() : 443;
            p.name = uri.getFragment() != null ? URLDecoder.decode(uri.getFragment(), "UTF-8") : p.address;
            p.tls = false;
            String query = uri.getQuery() != null ? uri.getQuery() : "";
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length < 2) continue;
                String k = kv[0];
                String v = URLDecoder.decode(kv[1], "UTF-8");
                switch (k) {
                    case "type": p.network = v; break;
                    case "security": p.tls = "tls".equals(v) || "reality".equals(v); break;
                    case "sni": p.sni = v; break;
                    case "host": p.host = v; break;
                    case "path": p.path = v; break;
                    case "flow": p.flow = v; break;
                    case "serviceName": p.grpcServiceName = v; break;
                }
            }
            if (p.network == null || p.network.isEmpty()) p.network = "tcp";
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static ProxyProfile parseTrojan(String link) {
        try {
            URI uri = new URI(link);
            ProxyProfile p = new ProxyProfile();
            p.protocol = "trojan";
            p.password = uri.getUserInfo();
            p.address = uri.getHost();
            p.port = uri.getPort() > 0 ? uri.getPort() : 443;
            p.name = uri.getFragment() != null ? URLDecoder.decode(uri.getFragment(), "UTF-8") : p.address;
            p.tls = true;
            p.network = "tcp";
            String query = uri.getQuery() != null ? uri.getQuery() : "";
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length < 2) continue;
                String k = kv[0];
                String v = URLDecoder.decode(kv[1], "UTF-8");
                switch (k) {
                    case "sni": p.sni = v; break;
                    case "type": p.network = v; break;
                    case "host": p.host = v; break;
                    case "path": p.path = v; break;
                    case "security": p.tls = !"none".equals(v); break;
                    case "serviceName": p.grpcServiceName = v; break;
                }
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static ProxyProfile parseShadowsocks(String link) {
        try {
            String raw = link.substring(5);
            String fragment = "";
            if (raw.contains("#")) {
                String[] parts = raw.split("#", 2);
                raw = parts[0];
                fragment = URLDecoder.decode(parts[1], "UTF-8");
            }
            String decoded;
            try {
                decoded = new String(Base64.decode(raw, Base64.DEFAULT));
            } catch (Exception e) {
                if (raw.contains("@")) {
                    decoded = new String(Base64.decode(raw.split("@")[0], Base64.DEFAULT))
                        + "@" + raw.split("@", 2)[1];
                } else {
                    return null;
                }
            }
            ProxyProfile p = new ProxyProfile();
            p.protocol = "shadowsocks";
            p.name = fragment;
            if (decoded.contains("@")) {
                String[] atSplit = decoded.split("@", 2);
                String methodPass = atSplit[0];
                String hostPort = atSplit[1];
                if (methodPass.contains(":")) {
                    p.ssMethod = methodPass.split(":", 2)[0];
                    p.ssPassword = methodPass.split(":", 2)[1];
                }
                if (hostPort.contains(":")) {
                    p.address = hostPort.split(":")[0];
                    p.port = parseInt(hostPort.split(":")[1], 8388);
                }
            }
            if (p.name == null || p.name.isEmpty()) p.name = p.address;
            return p;
        } catch (Exception e) {
            return null;
        }
    }
}
