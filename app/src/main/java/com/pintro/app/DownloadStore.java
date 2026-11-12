package com.pintro.app;

import android.content.Context;
import android.content.SharedPreferences;

public class DownloadStore {

    private final SharedPreferences prefs;

    public DownloadStore(Context context) {
        prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE);
    }

    public void put(String pinId, String reference) {
        prefs.edit().putString(pinId, reference).apply();
    }

    public String get(String pinId) {
        return prefs.getString(pinId, null);
    }

    public void remove(String pinId) {
        prefs.edit().remove(pinId).apply();
    }
}
