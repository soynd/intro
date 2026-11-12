package com.pintro.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class ImageSaver {

    public static final class Saved {
        public final Uri uri;
        public final File file;

        Saved(Uri uri, File file) {
            this.uri = uri;
            this.file = file;
        }

        public String reference() {
            return file != null ? file.getAbsolutePath() : uri.toString();
        }
    }

    private ImageSaver() {
    }

    public static String mimeFromUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public static String extension(String mime) {
        if ("image/png".equals(mime)) {
            return ".png";
        }
        if ("image/webp".equals(mime)) {
            return ".webp";
        }
        return ".jpg";
    }

    public static Saved saveBytes(Context context, byte[] bytes, String mime) throws IOException {
        String name = "intro_" + System.currentTimeMillis() + extension(mime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/intro");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Could not create image");
            }

            try (OutputStream output = resolver.openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Could not open image");
                }
                output.write(bytes);
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return new Saved(uri, null);
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "intro");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create folder");
        }
        File file = new File(dir, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{mime}, null);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
        return new Saved(uri, file);
    }

    public static Saved saveBitmap(Context context, Bitmap bitmap) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, buffer);
        return saveBytes(context, buffer.toByteArray(), "image/jpeg");
    }

    public static void delete(Context context, String reference) {
        if (reference == null || reference.isEmpty()) {
            return;
        }
        try {
            if (reference.startsWith("content://")) {
                context.getContentResolver().delete(Uri.parse(reference), null, null);
            } else {
                File file = new File(reference);
                if (file.exists()) {
                    file.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
