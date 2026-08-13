package com.rscoders.v2ray;

import android.util.Base64;
import com.google.gson.JsonObject;
import com.rscoders.v2ray.model.ProxyProfile;
import java.net.URLEncoder;

public class LinkExporter {

    public static String export(ProxyProfile p) {
        if (p == null) return null;
        switch (p.protocol.toLowerCase()) {
            case "vmess": return exportVmess(p);
            case "vless": return exportVless(p);
            case "trojan": return exportTrojan(p);
            case "shadowsocks":
            case "ss": return exportShadowsocks(p);
            default: return null;
        }
    }

    private static String exportVmess(ProxyProfile p) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("v", "2");
            obj.addProperty("ps", p.name != null ? p.name : "");
            obj.addProperty("add", p.address);
            obj.addProperty("port", String.valueOf(p.port));
            obj.addProperty("id", p.uuid);
            obj.addProperty("aid", String.valueOf(p.alterId));
            obj.addProperty("scy", p.security != null && !p.security.isEmpty() ? p.security : "auto");
            obj.addProperty("net", p.network != null ? p.network : "ws");
            obj.addProperty("host", p.host != null ? p.host : "");
            obj.addProperty("path", p.path != null ? p.path : "/");
            obj.addProperty("tls", p.tls ? "tls" : "");
            obj.addProperty("sni", p.sni != null ? p.sni : "");
            obj.addProperty("type", "none");
            String json = new com.google.gson.Gson().toJson(obj);
            String b64 = Base64.encodeToString(json.getBytes("UTF-8"), Base64.NO_WRAP);
            return "vmess://" + b64;
        } catch (Exception e) {
            return null;
        }
    }

    private static String exportVless(ProxyProfile p) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("vless://");
            sb.append(p.uuid);
            sb.append("@");
            sb.append(p.address);
            sb.append(":");
            sb.append(p.port);
            sb.append("?");
            sb.append("type=").append(enc(p.network != null ? p.network : "tcp"));
            if (p.tls) sb.append("&security=tls");
            if (p.sni != null && !p.sni.isEmpty()) sb.append("&sni=").append(enc(p.sni));
            if (p.host != null && !p.host.isEmpty()) sb.append("&host=").append(enc(p.host));
            if (p.path != null && !p.path.isEmpty()) sb.append("&path=").append(enc(p.path));
            if (p.flow != null && !p.flow.isEmpty()) sb.append("&flow=").append(enc(p.flow));
            if (p.grpcServiceName != null && !p.grpcServiceName.isEmpty()) sb.append("&serviceName=").append(enc(p.grpcServiceName));
            sb.append("#").append(enc(p.name != null ? p.name : p.address));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String exportTrojan(ProxyProfile p) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("trojan://");
            sb.append(p.password);
            sb.append("@");
            sb.append(p.address);
            sb.append(":");
            sb.append(p.port);
            sb.append("?");
            sb.append("type=").append(enc(p.network != null ? p.network : "tcp"));
            if (p.sni != null && !p.sni.isEmpty()) sb.append("&sni=").append(enc(p.sni));
            if (p.host != null && !p.host.isEmpty()) sb.append("&host=").append(enc(p.host));
            if (p.path != null && !p.path.isEmpty()) sb.append("&path=").append(enc(p.path));
            if (!p.tls) sb.append("&security=none");
            if (p.grpcServiceName != null && !p.grpcServiceName.isEmpty()) sb.append("&serviceName=").append(enc(p.grpcServiceName));
            sb.append("#").append(enc(p.name != null ? p.name : p.address));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String exportShadowsocks(ProxyProfile p) {
        try {
            String method = p.ssMethod != null ? p.ssMethod : "aes-128-gcm";
            String pass = p.ssPassword != null ? p.ssPassword : "";
            String methodPass = Base64.encodeToString(
                (method + ":" + pass).getBytes("UTF-8"), Base64.NO_WRAP);
            String tag = (p.name != null && !p.name.isEmpty()) ? p.name : p.address;
            return "ss://" + methodPass + "@" + p.address + ":" + p.port + "#" + enc(tag);
        } catch (Exception e) {
            return null;
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }
}
