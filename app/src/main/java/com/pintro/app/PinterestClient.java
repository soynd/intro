package com.pintro.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PinterestClient {

    public static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final Pattern APP_VERSION = Pattern.compile("\"appVersion\":\"([^\"]+)\"");

    private final OkHttpClient http;
    private final CookieManager cookies;
    private final Object warmLock = new Object();

    private volatile boolean warmed;
    private volatile String appVersion = "7eb5076";
    private volatile String csrf = "";

    public PinterestClient(Context context) {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        http = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookies))
                .cache(new Cache(new File(context.getCacheDir(), "pinterest-http"), 32L * 1024L * 1024L))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public Page search(String query, String bookmark) throws IOException {
        warm();
        Page page = request(query, bookmark);
        if (page == null) {
            resetSession();
            warm();
            page = request(query, bookmark);
        }
        if (page == null) {
            throw new IOException("Pinterest refused the request");
        }
        return page;
    }

    public byte[] download(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Referer", "https://www.pinterest.com/")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Image request failed with " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty image response");
            }
            return body.bytes();
        }
    }

    private Page request(String query, String bookmark) throws IOException {
        JSONObject options = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            options.put("query", query);
            options.put("scope", "pins");
            options.put("page_size", 30);
            options.put("gated", false);
            if (bookmark != null && !bookmark.isEmpty()) {
                options.put("bookmarks", new JSONArray().put(bookmark));
            }
            data.put("options", options);
            data.put("context", new JSONObject());
        } catch (Exception e) {
            throw new IOException("Could not build request", e);
        }

        HttpUrl url = HttpUrl.get("https://www.pinterest.com/resource/BaseSearchResource/get/")
                .newBuilder()
                .addQueryParameter("source_url", "/search/pins/?q=" + query)
                .addQueryParameter("data", data.toString())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*, q=0.01")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.pinterest.com/search/pins/?q=" + query)
                .header("Origin", "https://www.pinterest.com")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-Pinterest-AppState", "active")
                .header("X-Pinterest-PWS-Handler", "www/search/pins.js")
                .header("X-App-Version", appVersion)
                .header("X-CSRFToken", csrf)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = http.newCall(request).execute()) {
            int code = response.code();
            if (code == 403) {
                return null;
            }
            if (code == 429) {
                throw new IOException("Pinterest is limiting requests. Try again in a minute.");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Pinterest returned " + code);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response");
            }
            return parse(body.string(), query);
        }
    }

    private Page parse(String body, String query) throws IOException {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject resource = root.optJSONObject("resource_response");
            if (resource == null) {
                throw new IOException("Unexpected response");
            }
            JSONObject data = resource.optJSONObject("data");
            JSONArray results = data == null ? null : data.optJSONArray("results");
            if (results == null) {
                return new Page(Collections.emptyList(), null);
            }

            List<Pin> pins = new ArrayList<>(results.length());
            for (int i = 0; i < results.length(); i++) {
                Pin pin = Pin.fromJson(results.optJSONObject(i), query);
                if (pin != null) {
                    pins.add(pin);
                }
            }

            String bookmark = resource.optString("bookmark", "");
            if (bookmark.isEmpty() || bookmark.equals("-end-")) {
                bookmark = null;
            }
            return new Page(pins, bookmark);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not read Pinterest response", e);
        }
    }

    private void warm() throws IOException {
        synchronized (warmLock) {
            if (warmed) {
                return;
            }
            Request request = new Request.Builder()
                    .url("https://www.pinterest.com/")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (body != null) {
                    Matcher matcher = APP_VERSION.matcher(body.string());
                    if (matcher.find()) {
                        appVersion = matcher.group(1);
                    }
                }
            }
            csrf = findCookie("csrftoken");
            warmed = true;
        }
    }

    private void resetSession() {
        synchronized (warmLock) {
            cookies.getCookieStore().removeAll();
            warmed = false;
            csrf = "";
        }
    }

    private String findCookie(String name) {
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return "";
    }

    public static class Page {
        public final List<Pin> pins;
        public final String bookmark;

        public Page(List<Pin> pins, String bookmark) {
            this.pins = pins;
            this.bookmark = bookmark;
        }
    }
}
