package com.rscoders.v2ray;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rscoders.v2ray.model.ProxyProfile;

public class ConfigBuilder {

    public static String build(ProxyProfile p) {
        JsonObject root = new JsonObject();

        JsonArray inbounds = new JsonArray();

        JsonObject socks = new JsonObject();
        socks.addProperty("tag", "socks");
        socks.addProperty("port", 10808);
        socks.addProperty("listen", "127.0.0.1");
        socks.addProperty("protocol", "socks");
        JsonObject socksSettings = new JsonObject();
        socksSettings.addProperty("auth", "noauth");
        socksSettings.addProperty("udp", true);
        socksSettings.addProperty("userLevel", 8);
        socks.add("settings", socksSettings);
        JsonObject socksSniff = new JsonObject();
        socksSniff.addProperty("enabled", false);
        socksSniff.add("destOverride", new JsonArray());
        socks.add("sniffing", socksSniff);
        inbounds.add(socks);

        JsonObject http = new JsonObject();
        http.addProperty("tag", "http");
        http.addProperty("port", 10809);
        http.addProperty("listen", "127.0.0.1");
        http.addProperty("protocol", "http");
        JsonObject httpSettings = new JsonObject();
        httpSettings.addProperty("userLevel", 8);
        http.add("settings", httpSettings);
        inbounds.add(http);

        root.add("inbounds", inbounds);

        JsonArray outbounds = new JsonArray();
        JsonObject outbound = new JsonObject();
        JsonObject mux = new JsonObject();
        mux.addProperty("enabled", p.mux);
        outbound.add("mux", mux);
        outbound.addProperty("protocol", p.protocol);

        JsonObject settings = new JsonObject();
        if ("vmess".equalsIgnoreCase(p.protocol) || "vless".equalsIgnoreCase(p.protocol)) {
            JsonArray vnext = new JsonArray();
            JsonObject server = new JsonObject();
            server.addProperty("address", p.address);
            server.addProperty("port", p.port);
            JsonArray users = new JsonArray();
            JsonObject user = new JsonObject();
            user.addProperty("id", p.uuid);
            if ("vmess".equalsIgnoreCase(p.protocol)) {
                user.addProperty("alterId", p.alterId);
                user.addProperty("level", 8);
                user.addProperty("security", p.security);
            } else {
                user.addProperty("encryption", "none");
                user.addProperty("level", 0);
                if (p.flow != null && !p.flow.isEmpty()) {
                    user.addProperty("flow", p.flow);
                }
            }
            users.add(user);
            server.add("users", users);
            vnext.add(server);
            settings.add("vnext", vnext);
        } else if ("trojan".equalsIgnoreCase(p.protocol)) {
            JsonArray servers = new JsonArray();
            JsonObject server = new JsonObject();
            server.addProperty("address", p.address);
            server.addProperty("port", p.port);
            server.addProperty("password", p.password);
            server.addProperty("level", 0);
            servers.add(server);
            settings.add("servers", servers);
        } else if ("shadowsocks".equalsIgnoreCase(p.protocol) || "ss".equalsIgnoreCase(p.protocol)) {
            JsonArray servers = new JsonArray();
            JsonObject server = new JsonObject();
            server.addProperty("address", p.address);
            server.addProperty("port", p.port);
            server.addProperty("method", p.ssMethod != null ? p.ssMethod : "aes-128-gcm");
            server.addProperty("password", p.ssPassword != null ? p.ssPassword : "");
            server.addProperty("level", 0);
            servers.add(server);
            settings.add("servers", servers);
            outbound.addProperty("protocol", "shadowsocks");
        }

        outbound.add("settings", settings);

        if (!"shadowsocks".equalsIgnoreCase(p.protocol) && !"ss".equalsIgnoreCase(p.protocol)) {
            outbound.add("streamSettings", buildStream(p));
        }

        outbound.addProperty("tag", "proxy");
        outbounds.add(outbound);

        JsonObject direct = new JsonObject();
        direct.addProperty("protocol", "freedom");
        direct.addProperty("tag", "direct");
        outbounds.add(direct);

        JsonObject block = new JsonObject();
        block.addProperty("protocol", "blackhole");
        JsonObject blockSettings = new JsonObject();
        JsonObject blockResponse = new JsonObject();
        blockResponse.addProperty("type", "http");
        blockSettings.add("response", blockResponse);
        block.add("settings", blockSettings);
        block.addProperty("tag", "block");
        outbounds.add(block);

        root.add("outbounds", outbounds);

        JsonObject log = new JsonObject();
        log.addProperty("loglevel", "error");
        root.add("log", log);

        root.add("policy", buildPolicy());
        root.add("stats", new JsonObject());
        root.add("dns", buildDns());
        root.add("routing", buildRouting());

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject buildStream(ProxyProfile p) {
        JsonObject stream = new JsonObject();
        String net = (p.network == null || p.network.isEmpty()) ? "tcp" : p.network.toLowerCase();
        stream.addProperty("network", net);

        if (p.tls) {
            stream.addProperty("security", "tls");
            JsonObject tls = new JsonObject();
            tls.addProperty("allowInsecure", p.insecure);
            if (p.sni != null && !p.sni.isEmpty()) {
                tls.addProperty("serverName", p.sni);
            }
            stream.add("tlsSettings", tls);
        } else {
            stream.addProperty("security", "none");
        }

        switch (net) {
            case "ws":
                JsonObject ws = new JsonObject();
                JsonObject wsHeaders = new JsonObject();
                if (p.host != null && !p.host.isEmpty()) wsHeaders.addProperty("Host", p.host);
                ws.add("headers", wsHeaders);
                ws.addProperty("path", (p.path != null && !p.path.isEmpty()) ? p.path : "/");
                stream.add("wsSettings", ws);
                break;
            case "grpc":
                JsonObject grpc = new JsonObject();
                String svcName = (p.grpcServiceName != null) ? p.grpcServiceName : "";
                grpc.addProperty("serviceName", svcName);
                grpc.addProperty("multiMode", false);
                stream.add("grpcSettings", grpc);
                break;
            case "tcp":
                JsonObject tcp = new JsonObject();
                JsonObject tcpHeader = new JsonObject();
                tcpHeader.addProperty("type", "none");
                tcp.add("header", tcpHeader);
                stream.add("tcpSettings", tcp);
                break;
            case "h2":
                JsonObject h2 = new JsonObject();
                JsonArray h2hosts = new JsonArray();
                if (p.host != null && !p.host.isEmpty()) h2hosts.add(p.host);
                h2.add("host", h2hosts);
                h2.addProperty("path", (p.path != null && !p.path.isEmpty()) ? p.path : "/");
                stream.add("httpSettings", h2);
                break;
            case "quic":
                JsonObject quic = new JsonObject();
                quic.addProperty("security", "none");
                quic.addProperty("key", "");
                JsonObject quicH = new JsonObject();
                quicH.addProperty("type", "none");
                quic.add("header", quicH);
                stream.add("quicSettings", quic);
                break;
        }
        return stream;
    }

    private static JsonObject buildPolicy() {
        JsonObject policy = new JsonObject();
        JsonObject levels = new JsonObject();
        JsonObject lv8 = new JsonObject();
        lv8.addProperty("connIdle", 300);
        lv8.addProperty("downlinkOnly", 1);
        lv8.addProperty("handshake", 4);
        lv8.addProperty("uplinkOnly", 1);
        levels.add("8", lv8);
        JsonObject lv0 = new JsonObject();
        lv0.addProperty("connIdle", 300);
        lv0.addProperty("downlinkOnly", 1);
        lv0.addProperty("handshake", 4);
        lv0.addProperty("uplinkOnly", 1);
        levels.add("0", lv0);
        policy.add("levels", levels);
        JsonObject system = new JsonObject();
        system.addProperty("statsOutboundUplink", true);
        system.addProperty("statsOutboundDownlink", true);
        policy.add("system", system);
        return policy;
    }

    private static JsonObject buildDns() {
        JsonObject dns = new JsonObject();
        JsonObject hosts = new JsonObject();
        hosts.addProperty("domain:googleapis.cn", "googleapis.com");
        dns.add("hosts", hosts);
        JsonArray servers = new JsonArray();
        servers.add("1.1.1.1");
        dns.add("servers", servers);
        return dns;
    }

    private static JsonObject buildRouting() {
        JsonObject routing = new JsonObject();
        routing.addProperty("domainStrategy", "IPIfNonMatch");
        JsonArray rules = new JsonArray();
        JsonObject dnsRule = new JsonObject();
        dnsRule.addProperty("type", "field");
        dnsRule.addProperty("outboundTag", "proxy");
        dnsRule.addProperty("port", "53");
        JsonArray dnsIp = new JsonArray();
        dnsIp.add("1.1.1.1");
        dnsRule.add("ip", dnsIp);
        rules.add(dnsRule);
        routing.add("rules", rules);
        return routing;
    }

    public static String buildEmpty() {
        JsonObject root = new JsonObject();
        root.add("inbounds", new JsonArray());
        root.add("outbounds", new JsonArray());
        root.add("policy", buildPolicy());
        root.add("stats", new JsonObject());
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
