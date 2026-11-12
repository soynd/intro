package com.pintro.app;

import android.graphics.Bitmap;

public final class Rcas {

    private static final float PEAK = -0.125f;
    private static final float STRENGTH = 0.9f;

    private Rcas() {
    }

    public static Bitmap apply(Bitmap source) {
        return apply(source, STRENGTH);
    }

    public static Bitmap apply(Bitmap source, float strength) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 2 || height < 2) {
            return source;
        }

        int[] input = new int[width * height];
        int[] output = new int[width * height];
        source.getPixels(input, 0, width, 0, 0, width, height);

        float peak = PEAK * Math.max(0f, Math.min(1.5f, strength));

        for (int y = 0; y < height; y++) {
            int row = y * width;
            int up = (y > 0 ? y - 1 : 0) * width;
            int down = (y < height - 1 ? y + 1 : height - 1) * width;
            for (int x = 0; x < width; x++) {
                int left = x > 0 ? x - 1 : 0;
                int right = x < width - 1 ? x + 1 : width - 1;

                int center = input[row + x];
                int north = input[up + x];
                int west = input[row + left];
                int east = input[row + right];
                int south = input[down + x];

                int r = channel((center >> 16) & 0xFF, (north >> 16) & 0xFF,
                        (west >> 16) & 0xFF, (east >> 16) & 0xFF, (south >> 16) & 0xFF, peak);
                int g = channel((center >> 8) & 0xFF, (north >> 8) & 0xFF,
                        (west >> 8) & 0xFF, (east >> 8) & 0xFF, (south >> 8) & 0xFF, peak);
                int b = channel(center & 0xFF, north & 0xFF,
                        west & 0xFF, east & 0xFF, south & 0xFF, peak);

                output[row + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int channel(int center, int north, int west, int east, int south, float peak) {
        float low = Math.min(Math.min(north, south), Math.min(west, east));
        float high = Math.max(Math.max(north, south), Math.max(west, east));
        low = Math.min(low, center);
        high = Math.max(high, center);

        float inverse = high > 0.0001f ? 1f / high : 0f;
        float amplitude = Math.max(0f, Math.min(1f, low * inverse));
        amplitude = (float) Math.sqrt(amplitude);

        float weight = amplitude * peak;
        float scale = 1f / (1f + 4f * weight);
        float value = ((north + south + west + east) * weight + center) * scale;

        if (value < low) {
            value = low;
        }
        if (value > high) {
            value = high;
        }
        return Math.round(value);
    }
}
