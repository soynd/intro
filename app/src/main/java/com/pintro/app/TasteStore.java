package com.pintro.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.Locale;

public final class TasteStore {

    private static final String KEY = "recent";
    private static final int MAX = 30;

    private final SharedPreferences prefs;

    public TasteStore(Context context) {
        prefs = context.getSharedPreferences("taste", Context.MODE_PRIVATE);
    }

    public void record(String text) {
        String key = normalize(text);
        if (key.isEmpty()) {
            return;
        }

        JSONArray recent = read();
        JSONArray next = new JSONArray();
        next.put(key);
        for (int i = 0; i < recent.length() && next.length() < MAX; i++) {
            String value = recent.optString(i, "");
            if (!value.isEmpty()) {
                next.put(value);
            }
        }
        prefs.edit().putString(KEY, next.toString()).apply();
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }

    public String best(String fallback) {        JSONArray recent = read();
        String best = fallback;
        int bestCount = 0;
        for (int i = 0; i < recent.length(); i++) {
            String key = recent.optString(i, "");
            if (key.isEmpty()) {
                continue;
            }
            int count = 0;
            for (int j = 0; j < recent.length(); j++) {
                if (key.equals(recent.optString(j, ""))) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = key;
            }
        }
        return best;
    }

    private JSONArray read() {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
