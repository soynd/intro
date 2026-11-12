package com.pintro.app;

import android.graphics.Color;

import org.json.JSONObject;

import java.io.Serializable;

public class Pin implements Serializable {

    private static final long serialVersionUID = 1L;

    public String id = "";
    public String title = "";
    public String description = "";
    public String gridUrl = "";
    public String imageUrl = "";
    public String origUrl = "";
    public String author = "";
    public String authorImage = "";
    public String domain = "";
    public String link = "";
    public String query = "";
    public String color = "";
    public int width = 4;
    public int height = 5;

    public static Pin fromJson(JSONObject o, String query) {
        if (o == null || o.optBoolean("is_promoted") || o.optBoolean("promoted_is_lead_ad")
                || o.optBoolean("is_downstream_promotion")) {
            return null;
        }

        JSONObject images = o.optJSONObject("images");
        if (images == null) {
            return null;
        }

        JSONObject grid = images.optJSONObject("474x");
        if (grid == null) {
            grid = images.optJSONObject("236x");
        }

        JSONObject big = images.optJSONObject("orig");
        if (big == null) {
            big = images.optJSONObject("736x");
        }
        if (big == null) {
            big = grid;
        }

        if (grid == null || big == null) {
            return null;
        }

        String gridUrl = grid.optString("url", "");
        String bigUrl = big.optString("url", "");
        if (gridUrl.isEmpty() || bigUrl.isEmpty()) {
            return null;
        }

        Pin p = new Pin();
        p.id = str(o, "id");
        if (p.id.isEmpty()) {
            p.id = str(o, "node_id");
        }

        p.title = clean(first(str(o, "title"), str(o, "grid_title"), str(o, "auto_alt_text"), str(o, "seo_alt_text")));
        p.description = clean(str(o, "description"));
        p.gridUrl = gridUrl;
        p.imageUrl = bigUrl;
        p.origUrl = bigUrl;
        p.query = query == null ? "" : query;
        p.color = str(o, "dominant_color");

        JSONObject pinner = o.optJSONObject("pinner");
        if (pinner != null) {
            p.author = clean(first(str(pinner, "full_name"), str(pinner, "username")));
            p.authorImage = str(pinner, "image_small_url");
        }

        p.domain = clean(str(o, "domain"));
        if (p.domain.isEmpty() || p.domain.equalsIgnoreCase("Uploaded by user")) {
            p.domain = p.author;
        }

        p.link = str(o, "link");
        if (!p.link.startsWith("http")) {
            p.link = "";
        }

        JSONObject ratio = images.optJSONObject("236x");
        if (ratio == null) {
            ratio = images.optJSONObject("474x");
        }
        if (ratio == null) {
            ratio = big;
        }

        p.width = ratio.optInt("width", 0);
        p.height = ratio.optInt("height", 0);
        if (p.width <= 0 || p.height <= 0) {
            p.width = 4;
            p.height = 5;
        }

        if (p.title.isEmpty()) {
            p.title = p.description;
        }

        return p;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("description", description);
            o.put("gridUrl", gridUrl);
            o.put("imageUrl", imageUrl);
            o.put("origUrl", origUrl);
            o.put("author", author);
            o.put("authorImage", authorImage);
            o.put("domain", domain);
            o.put("link", link);
            o.put("query", query);
            o.put("color", color);
            o.put("width", width);
            o.put("height", height);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Pin fromStored(JSONObject o) {
        if (o == null) {
            return null;
        }
        Pin p = new Pin();
        p.id = str(o, "id");
        p.title = str(o, "title");
        p.description = str(o, "description");
        p.gridUrl = str(o, "gridUrl");
        p.imageUrl = str(o, "imageUrl");
        p.origUrl = str(o, "origUrl");
        p.author = str(o, "author");
        p.authorImage = str(o, "authorImage");
        p.domain = str(o, "domain");
        p.link = str(o, "link");
        p.query = str(o, "query");
        p.color = str(o, "color");
        p.width = o.optInt("width", 4);
        p.height = o.optInt("height", 5);
        if (p.id.isEmpty() || p.gridUrl.isEmpty()) {
            return null;
        }
        return p;
    }

    public int colorInt() {
        try {
            return Color.parseColor(color);
        } catch (Exception e) {
            return 0xFFE8E6E1;
        }
    }

    private static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return "";
        }
        return o.optString(key, "").trim();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) {
                return value;
            }
        }
        return "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (text.length() > 180) {
            text = text.substring(0, 177) + "...";
        }
        return text;
    }
}
