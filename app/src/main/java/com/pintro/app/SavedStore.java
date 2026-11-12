package com.pintro.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SavedStore {

    private final SharedPreferences prefs;

    public SavedStore(Context context) {
        prefs = context.getSharedPreferences("saved_pins", Context.MODE_PRIVATE);
    }

    public synchronized List<Pin> all() {
        List<Pin> pins = new ArrayList<>();
        String raw = prefs.getString("pins", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                Pin pin = Pin.fromStored(array.optJSONObject(i));
                if (pin != null) {
                    pins.add(pin);
                }
            }
        } catch (Exception ignored) {
        }
        return pins;
    }

    public synchronized boolean contains(String id) {
        return indexOf(id) >= 0;
    }

    public synchronized boolean toggle(Pin pin) {
        List<Pin> pins = all();
        int index = -1;
        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).id.equals(pin.id)) {
                index = i;
                break;
            }
        }

        boolean saved;
        if (index >= 0) {
            pins.remove(index);
            saved = false;
        } else {
            pins.add(0, pin);
            saved = true;
        }

        JSONArray array = new JSONArray();
        for (Pin item : pins) {
            array.put(item.toJson());
        }
        prefs.edit().putString("pins", array.toString()).apply();
        return saved;
    }

    public synchronized void remove(String id) {
        List<Pin> pins = all();
        JSONArray array = new JSONArray();
        for (Pin pin : pins) {
            if (!pin.id.equals(id)) {
                array.put(pin.toJson());
            }
        }
        prefs.edit().putString("pins", array.toString()).apply();
    }

    private int indexOf(String id) {
        String raw = prefs.getString("pins", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null && object.optString("id").equals(id)) {
                    return i;
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
