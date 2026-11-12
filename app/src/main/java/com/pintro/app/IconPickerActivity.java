package com.pintro.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IconPickerActivity extends AppCompatActivity {

    private static final String DEFAULT_QUERY = "app icon";

    private PinterestClient client;
    private IconAdapter adapter;
    private EditText search;
    private ProgressBar loading;
    private View empty;
    private TextView emptyText;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService applyIo = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String query = DEFAULT_QUERY;
    private String bookmark;
    private boolean loadingPage;
    private boolean end;
    private long generation;

    private androidx.appcompat.app.AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_picker);

        client = new PinterestClient(this);
        search = findViewById(R.id.icon_search);
        loading = findViewById(R.id.icon_loading);
        empty = findViewById(R.id.icon_empty);
        emptyText = findViewById(R.id.icon_empty_text);
        RecyclerView grid = findViewById(R.id.icon_grid);

        adapter = new IconAdapter(this::applyIcon);
        GridLayoutManager manager = new GridLayoutManager(this, 3);
        grid.setLayoutManager(manager);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setItemViewCacheSize(24);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        search.setText(DEFAULT_QUERY);
        search.setSelection(DEFAULT_QUERY.length());
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });

        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingPage || end) {
                    return;
                }
                int last = manager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 6) {
                    loadPage(false);
                }
            }
        });

        loadPage(true);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        applyIo.shutdownNow();
        dismissProgress();
        super.onDestroy();
    }

    private void submitSearch() {
        String value = search.getText().toString().trim();
        if (value.isEmpty()) {
            value = DEFAULT_QUERY;
        }
        hideKeyboard();
        if (value.equalsIgnoreCase(query) && !adapter.items().isEmpty()) {
            return;
        }
        query = value;
        bookmark = null;
        end = false;
        adapter.clear();
        loadPage(true);
    }

    private void loadPage(boolean first) {
        if (loadingPage || (!first && end)) {
            return;
        }
        loadingPage = true;
        if (first) {
            loading.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
        }

        long ticket = ++generation;
        String currentQuery = query;
        String currentBookmark = bookmark;

        io.execute(() -> {
            try {
                PinterestClient.Page page = client.search(currentQuery, currentBookmark);
                List<Pin> pins = new ArrayList<>();
                if (page != null) {
                    for (Pin pin : page.pins) {
                        if (pin != null) {
                            pins.add(pin);
                        }
                    }
                }
                String next = page == null ? null : page.bookmark;
                main.post(() -> {
                    if (ticket != generation) {
                        return;
                    }
                    loadingPage = false;
                    loading.setVisibility(View.GONE);
                    bookmark = next;
                    end = next == null;
                    if (first) {
                        adapter.setPins(pins);
                    } else {
                        adapter.append(pins);
                    }
                    if (adapter.getItemCount() == 0) {
                        emptyText.setText(R.string.icon_empty);
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        empty.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (ticket != generation) {
                        return;
                    }
                    loadingPage = false;
                    loading.setVisibility(View.GONE);
                    if (adapter.getItemCount() == 0) {
                        emptyText.setText(R.string.icon_error);
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, R.string.icon_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void applyIcon(Pin pin) {
        showProgress();
        applyIo.execute(() -> {
            Bitmap source = null;
            Bitmap icon = null;
            try {
                byte[] bytes;
                try {
                    bytes = client.download(pin.imageUrl);
                } catch (Exception e) {
                    bytes = client.download(pin.gridUrl);
                }

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw new IOException("Could not read image");
                }

                int sample = 1;
                while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1200) {
                    sample *= 2;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (source == null) {
                    throw new IOException("Could not decode image");
                }

                icon = square(source, 432);
                Bitmap finalIcon = icon;
                main.post(() -> {
                    dismissProgress();
                    pinIcon(finalIcon);
                });
            } catch (Exception e) {
                main.post(() -> {
                    dismissProgress();
                    Toast.makeText(this, R.string.icon_failed, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (source != null && source != icon) {
                    source.recycle();
                }
            }
        });
    }

    private Bitmap square(Bitmap source, int size) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(source, left, top, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
        if (cropped != scaled && cropped != source) {
            cropped.recycle();
        }
        return scaled;
    }

    private void pinIcon(Bitmap bitmap) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.icon_unsupported, Toast.LENGTH_LONG).show();
            return;
        }

        Intent launch = new Intent(this, MainActivity.class);
        launch.setAction(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);

        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(this,
                "pintro_icon_" + System.currentTimeMillis())
                .setShortLabel(getString(R.string.app_name))
                .setLongLabel(getString(R.string.app_name))
                .setIcon(IconCompat.createWithBitmap(bitmap))
                .setIntent(launch)
                .build();

        boolean requested = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
        Toast.makeText(this, requested ? R.string.icon_confirm : R.string.icon_unsupported,
                Toast.LENGTH_LONG).show();
    }

    private void showProgress() {
        dismissProgress();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView text = new TextView(this);
        text.setText(R.string.icon_applying);
        text.setTextSize(15);
        text.setTextColor(getColor(R.color.text));
        box.addView(text);

        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        box.addView(bar, params);

        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = null;
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
