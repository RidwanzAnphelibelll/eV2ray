package com.rscoders.v2ray;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rscoders.v2ray.model.ProxyProfile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private static final String PREFS = "v2ray_prefs";
    private static final String KEY_JSON = "config_json";
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_ACTIVE_ID = "active_profile_id";
    private static final Gson GSON = new Gson();

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveJson(Context ctx, String json) {
        prefs(ctx).edit().putString(KEY_JSON, json).apply();
    }

    public static String loadJson(Context ctx) {
        String s = prefs(ctx).getString(KEY_JSON, null);
        return s != null ? s : ConfigBuilder.buildEmpty();
    }

    public static List<ProxyProfile> loadProfiles(Context ctx) {
        String json = prefs(ctx).getString(KEY_PROFILES, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<ProxyProfile>>() {}.getType();
        List<ProxyProfile> list = GSON.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public static void saveProfiles(Context ctx, List<ProxyProfile> list) {
        prefs(ctx).edit().putString(KEY_PROFILES, GSON.toJson(list)).apply();
    }

    public static void addProfile(Context ctx, ProxyProfile p) {
        List<ProxyProfile> list = loadProfiles(ctx);
        list.add(p);
        saveProfiles(ctx, list);
        setActiveId(ctx, p.id);
        saveJson(ctx, ConfigBuilder.build(p));
    }

    public static void updateProfile(Context ctx, ProxyProfile p) {
        List<ProxyProfile> list = loadProfiles(ctx);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(p.id)) {
                list.set(i, p);
                break;
            }
        }
        saveProfiles(ctx, list);
        if (p.id.equals(getActiveId(ctx))) {
            saveJson(ctx, ConfigBuilder.build(p));
        }
    }

    public static void deleteProfile(Context ctx, String id) {
        List<ProxyProfile> list = loadProfiles(ctx);
        list.removeIf(p -> p.id.equals(id));
        saveProfiles(ctx, list);
        if (id.equals(getActiveId(ctx))) {
            if (!list.isEmpty()) {
                setActiveId(ctx, list.get(0).id);
                saveJson(ctx, ConfigBuilder.build(list.get(0)));
            } else {
                prefs(ctx).edit().remove(KEY_ACTIVE_ID).apply();
                saveJson(ctx, ConfigBuilder.buildEmpty());
            }
        }
    }

    public static void setActiveProfile(Context ctx, ProxyProfile p) {
        setActiveId(ctx, p.id);
        saveJson(ctx, ConfigBuilder.build(p));
    }

    public static ProxyProfile getActiveProfile(Context ctx) {
        String id = getActiveId(ctx);
        if (id == null) return null;
        for (ProxyProfile p : loadProfiles(ctx)) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public static void setActiveId(Context ctx, String id) {
        prefs(ctx).edit().putString(KEY_ACTIVE_ID, id).apply();
    }

    public static String getActiveId(Context ctx) {
        return prefs(ctx).getString(KEY_ACTIVE_ID, null);
    }
}
