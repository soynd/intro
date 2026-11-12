package com.pintro.app;

import android.app.Application;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.google.android.material.color.DynamicColors;

public class PintroApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        DynamicColors.applyToActivitiesIfAvailable(this);

        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(this).build();
        GlideBuilder builder = new GlideBuilder()
                .setMemoryCache(new LruResourceCache(calculator.getMemoryCacheSize()))
                .setBitmapPool(new LruBitmapPool(calculator.getBitmapPoolSize()))
                .setDiskCache(new InternalCacheDiskCacheFactory(this, "glide", 320L * 1024L * 1024L))
                .setLogLevel(Log.ERROR);
        Glide.init(this, builder);
    }
}
