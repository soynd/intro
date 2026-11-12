package com.pintro.app;

import android.Manifest;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    private static final int STORAGE_REQUEST = 41;

    private Pin pin;
    private PinterestClient client;
    private SavedStore savedStore;
    private DownloadStore downloadStore;

    private ImageView photo;
    private ProgressBar photoLoading;
    private TextView title;
    private TextView description;
    private TextView author;
    private TextView domain;
    private View authorRow;
    private ShapeableImageView avatar;
    private ImageView saveIcon;
    private TextView saveLabel;
    private View headerView;
    private View similarSection;
    private RecyclerView detailList;
    private DetailFeedAdapter feedAdapter;

    private String similarQuery;
    private String similarBookmark;
    private boolean loadingSimilar;
    private boolean similarEnd;
    private int similarGeneration;
    private final Set<String> similarSeen = new HashSet<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService similarIo = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearProgressIndicator progressBar;
    private TextView progressText;
    private androidx.appcompat.app.AlertDialog progressDialog;

    private Runnable pendingPermissionAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Object extra = getIntent().getSerializableExtra("pin");
        if (!(extra instanceof Pin)) {
            finish();
            return;
        }
        pin = (Pin) extra;

        setContentView(R.layout.activity_detail);
        client = new PinterestClient(this);
        savedStore = new SavedStore(this);
        downloadStore = new DownloadStore(this);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        detailList = findViewById(R.id.detail_list);
        StaggeredGridLayoutManager detailLayout =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        detailLayout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        detailList.setLayoutManager(detailLayout);
        detailList.setHasFixedSize(true);
        detailList.setItemAnimator(null);

        headerView = getLayoutInflater().inflate(R.layout.item_detail_header, detailList, false);

        photo = headerView.findViewById(R.id.photo);
        photoLoading = headerView.findViewById(R.id.photo_loading);
        title = headerView.findViewById(R.id.title);
        description = headerView.findViewById(R.id.description);
        author = headerView.findViewById(R.id.author);
        domain = headerView.findViewById(R.id.domain);
        authorRow = headerView.findViewById(R.id.author_row);
        avatar = headerView.findViewById(R.id.avatar);
        saveIcon = headerView.findViewById(R.id.action_save_icon);
        saveLabel = headerView.findViewById(R.id.action_save_label);
        similarSection = headerView.findViewById(R.id.similar_section);

        feedAdapter = new DetailFeedAdapter(headerView, this::openSimilar, 2, screenWidth);
        detailList.setAdapter(feedAdapter);

        detailList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                StaggeredGridLayoutManager layout =
                        (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                if (layout == null) {
                    return;
                }
                int[] last = layout.findLastVisibleItemPositions(null);
                int lastVisible = 0;
                for (int position : last) {
                    lastVisible = Math.max(lastVisible, position);
                }
                if (lastVisible >= feedAdapter.getItemCount() - 6) {
                    loadSimilarPage();
                }
            }
        });

        findViewById(R.id.back).setOnClickListener(v -> finish());

        title.setText(pin.title);
        title.setVisibility(pin.title.isEmpty() ? View.GONE : View.VISIBLE);

        description.setText(pin.description);
        if (pin.description.isEmpty()) {
            description.setVisibility(View.GONE);
        } else {
            description.setOnClickListener(v -> {
                if (description.getMaxLines() == 3) {
                    description.setMaxLines(12);
                } else {
                    description.setMaxLines(3);
                }
            });
        }

        author.setText(pin.author);
        domain.setText(pin.domain);
        if (pin.author.isEmpty() && pin.domain.isEmpty()) {
            authorRow.setVisibility(View.GONE);
        }

        if (!pin.authorImage.isEmpty()) {
            avatar.setPadding(0, 0, 0, 0);
            Glide.with(this).load(pin.authorImage).circleCrop().into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_person);
            avatar.setImageTintList(ColorStateList.valueOf(getColor(R.color.text2)));
        }

        if (!pin.link.isEmpty()) {
            domain.setOnClickListener(v -> openLink());
            domain.setTextColor(accent());
        }

        headerView.findViewById(R.id.action_save).setOnClickListener(v -> toggleSave());
        headerView.findViewById(R.id.action_download).setOnClickListener(v -> downloadImage());
        headerView.findViewById(R.id.action_rcas).setOnClickListener(v -> startRcas());
        headerView.findViewById(R.id.action_share).setOnClickListener(v -> shareImage());

        updateSave();
        sizePhoto();
        loadPhoto();
        loadSimilar();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        similarIo.shutdownNow();
        dismissProgress();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Runnable action = pendingPermissionAction;
                pendingPermissionAction = null;
                if (action != null) {
                    action.run();
                }
            } else {
                Toast.makeText(this, "Storage permission is needed to save images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sizePhoto() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float ratio = pin.height / (float) Math.max(1, pin.width);
        ratio = Math.max(0.6f, Math.min(2.4f, ratio));
        int height = Math.max(dp(180), (int) (screenWidth * ratio));
        ViewGroup.LayoutParams params = photo.getLayoutParams();
        params.height = height;
        photo.setLayoutParams(params);
    }

    private void loadPhoto() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int height = photo.getLayoutParams().height;
        Glide.with(this)
                .load(pin.origUrl)
                .thumbnail(Glide.with(this).load(pin.gridUrl))
                .override(screenWidth, height)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        photoLoading.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        photoLoading.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(photo);
    }

    private void loadSimilar() {
        similarQuery = similarQuery();
        similarGeneration++;
        similarBookmark = null;
        loadingSimilar = false;
        similarEnd = false;
        similarSeen.clear();
        similarSeen.add(pin.id);
        feedAdapter.clear();
        if (similarQuery.isEmpty()) {
            similarEnd = true;
            return;
        }
        loadSimilarPage();
    }

    private void loadSimilarPage() {
        if (loadingSimilar || similarEnd || similarQuery == null || similarQuery.isEmpty()) {
            return;
        }
        loadingSimilar = true;
        feedAdapter.setLoading(true);

        final String bookmark = similarBookmark;
        final int generation = similarGeneration;

        similarIo.execute(() -> {
            List<Pin> fresh = new ArrayList<>();
            String next = null;
            try {
                PinterestClient.Page page = client.search(similarQuery, bookmark);
                if (page != null) {
                    next = page.bookmark;
                    for (Pin item : page.pins) {
                        if (item == null || item.id.isEmpty() || similarSeen.contains(item.id)) {
                            continue;
                        }
                        similarSeen.add(item.id);
                        fresh.add(item);
                    }
                }
            } catch (Exception ignored) {
            }

            final List<Pin> added = fresh;
            final String nextBookmark = next;
            main.post(() -> {
                if (generation != similarGeneration) {
                    return;
                }
                loadingSimilar = false;
                feedAdapter.setLoading(false);
                if (added.isEmpty()) {
                    similarEnd = true;
                    if (feedAdapter.pinCount() == 0) {
                        similarSection.setVisibility(View.GONE);
                    }
                    return;
                }
                feedAdapter.append(added);
                similarSection.setVisibility(View.VISIBLE);
                similarBookmark = nextBookmark;
                if (nextBookmark == null || nextBookmark.isEmpty()) {
                    similarEnd = true;
                }
            });
        });
    }

    private String similarQuery() {
        if (!pin.query.isEmpty()) {
            return pin.query;
        }
        if (!pin.title.isEmpty()) {
            return pin.title;
        }
        if (!pin.description.isEmpty()) {
            return pin.description;
        }
        return pin.domain;
    }

    private void openSimilar(Pin item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("pin", item);
        startActivity(intent);
    }

    private void updateSave() {
        boolean saved = savedStore.contains(pin.id);
        saveIcon.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        saveIcon.setImageTintList(ColorStateList.valueOf(saved ? accent() : getColor(R.color.text)));
        saveLabel.setText(saved ? "Saved" : "Save");
        saveLabel.setTextColor(saved ? accent() : getColor(R.color.text));
    }

    private void toggleSave() {
        boolean saved = savedStore.toggle(pin);
        updateSave();
        Toast.makeText(this, saved ? "Saved" : "Removed", Toast.LENGTH_SHORT).show();
    }

    private void downloadImage() {
        if (!ensureStoragePermission(this::downloadImage)) {
            return;
        }
        showProgress("Downloading original", true);
        io.execute(() -> {
            try {
                byte[] bytes = fetchBest();
                String mime = ImageSaver.mimeFromUrl(pin.origUrl);
                ImageSaver.Saved saved = ImageSaver.saveBytes(this, bytes, mime);
                downloadStore.put(pin.id, saved.reference());
                main.post(() -> {
                    dismissProgress();
                    Toast.makeText(this, "Saved to Pictures/intro", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    dismissProgress();
                    Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startRcas() {
        if (!ensureStoragePermission(this::startRcas)) {
            return;
        }
        showProgress("Fetching original", true);
        io.execute(() -> {
            Bitmap source = null;
            Bitmap output = null;
            try {
                byte[] bytes = fetchBest();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

                int sample = 1;
                while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) {
                    sample *= 2;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (source == null) {
                    throw new IOException("Could not decode image");
                }

                main.post(() -> updateProgress("Applying RCAS", 45));
                output = Rcas.apply(source);

                int width = output.getWidth();
                int height = output.getHeight();
                ImageSaver.Saved saved = ImageSaver.saveBitmap(this, output);
                boolean removed = deleteOriginal();
                Bitmap[] crops = comparisonCrops(source, output, 420);

                Bitmap beforeCrop = crops[0];
                Bitmap afterCrop = crops[1];
                boolean finalRemoved = removed;
                main.post(() -> {
                    dismissProgress();
                    showResult(beforeCrop, afterCrop, saved.uri, width, height, finalRemoved);
                });
            } catch (Throwable e) {
                main.post(() -> {
                    dismissProgress();
                    Toast.makeText(this, "RCAS failed", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (source != null && source != output) {
                    source.recycle();
                }
                if (output != null) {
                    output.recycle();
                }
            }
        });
    }

    private boolean deleteOriginal() {
        String reference = downloadStore.get(pin.id);
        if (reference == null) {
            return false;
        }
        ImageSaver.delete(this, reference);
        downloadStore.remove(pin.id);
        return true;
    }

    private void showResult(Bitmap before, Bitmap after, Uri uri, int width, int height, boolean removed) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int side = dp(128);
        addCrop(row, before, getString(R.string.before), side);
        addCrop(row, after, getString(R.string.after), side);
        box.addView(row);

        TextView text = new TextView(this);
        text.setText("Saved to Pictures/intro\n" + width + " x " + height
                + (removed ? "\nOriginal download removed" : ""));
        text.setTextSize(13);
        text.setTextColor(getColor(R.color.text2));
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(14);
        box.addView(text, textParams);

        new MaterialAlertDialogBuilder(this)
                .setTitle("RCAS applied")
                .setView(box)
                .setPositiveButton("Share", (dialog, which) -> shareUri(uri))
                .setNeutralButton("Open", (dialog, which) -> openUri(uri))
                .setNegativeButton("Done", null)
                .show();
    }

    private void addCrop(LinearLayout row, Bitmap bitmap, String label, int side) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);

        ShapeableImageView image = new ShapeableImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(bitmap);
        image.setShapeAppearanceModel(new ShapeAppearanceModel.Builder()
                .setAllCornerSizes(dp(14))
                .build());
        column.addView(image, new LinearLayout.LayoutParams(side, side));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(12);
        text.setTextColor(getColor(R.color.text2));
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(6);
        column.addView(text, textParams);

        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnParams.setMarginStart(dp(5));
        columnParams.setMarginEnd(dp(5));
        row.addView(column, columnParams);
    }

    private Bitmap[] comparisonCrops(Bitmap source, Bitmap output, int size) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int side = Math.max(1, Math.min(128, Math.min(sw, sh)));
        int stride = Math.max(16, side / 4);
        long best = -1;
        int bx = 0;
        int by = 0;
        for (int y0 = 0; y0 <= sh - side; y0 += stride) {
            for (int x0 = 0; x0 <= sw - side; x0 += stride) {
                long energy = 0;
                for (int y = y0 + 1; y < y0 + side - 1; y += 4) {
                    for (int x = x0 + 1; x < x0 + side - 1; x += 4) {
                        int left = (source.getPixel(x - 1, y) >> 8) & 0xFF;
                        int right = (source.getPixel(x + 1, y) >> 8) & 0xFF;
                        int up = (source.getPixel(x, y - 1) >> 8) & 0xFF;
                        int down = (source.getPixel(x, y + 1) >> 8) & 0xFF;
                        energy += Math.abs(right - left) + Math.abs(down - up);
                    }
                }
                if (energy > best) {
                    best = energy;
                    bx = x0;
                    by = y0;
                }
            }
        }

        bx = Math.max(0, Math.min(bx, sw - side));
        by = Math.max(0, Math.min(by, sh - side));

        int ow = output.getWidth();
        int oh = output.getHeight();
        int ox = Math.max(0, Math.min(ow - 1, Math.round(bx * (float) ow / sw)));
        int oy = Math.max(0, Math.min(oh - 1, Math.round(by * (float) oh / sh)));
        int os = Math.max(1, Math.round(side * (float) ow / sw));
        os = Math.min(os, Math.min(ow - ox, oh - oy));

        Bitmap before = Bitmap.createBitmap(source, bx, by, side, side);
        Bitmap after = Bitmap.createBitmap(output, ox, oy, os, os);
        Bitmap beforeScaled = Bitmap.createScaledBitmap(before, size, size, true);
        Bitmap afterScaled = Bitmap.createScaledBitmap(after, size, size, true);
        if (beforeScaled != before) {
            before.recycle();
        }
        if (afterScaled != after) {
            after.recycle();
        }
        return new Bitmap[]{beforeScaled, afterScaled};
    }

    private void shareImage() {
        showProgress("Preparing image", true);
        io.execute(() -> {
            try {
                byte[] bytes = fetchBest();
                String mime = ImageSaver.mimeFromUrl(pin.origUrl);
                File file = new File(getCacheDir(),
                        "share_" + System.currentTimeMillis() + ImageSaver.extension(mime));
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(bytes);
                }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
                main.post(() -> {
                    dismissProgress();
                    shareUri(uri);
                });
            } catch (Exception e) {
                main.post(() -> {
                    dismissProgress();
                    Toast.makeText(this, "Could not share image", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private byte[] fetchBest() throws IOException {
        try {
            return client.download(pin.origUrl);
        } catch (IOException e) {
            if (!pin.imageUrl.isEmpty() && !pin.imageUrl.equals(pin.origUrl)) {
                return client.download(pin.imageUrl);
            }
            throw e;
        }
    }

    private void shareUri(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mime)) {
            mime = "image/jpeg";
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("image", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "Share image"));
        } catch (Exception e) {
            Toast.makeText(this, "No app can share this image", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUri(Uri uri) {
        String mime = getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mime)) {
            mime = "image/jpeg";
        }
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this image", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLink() {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(pin.link));
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean ensureStoragePermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        pendingPermissionAction = action;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST);
        return false;
    }

    private void showProgress(String message, boolean indeterminate) {
        dismissProgress();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(20), dp(24), dp(8));

        progressText = new TextView(this);
        progressText.setText(message);
        progressText.setTextSize(15);
        progressText.setTextColor(getColor(R.color.text));
        box.addView(progressText);

        progressBar = new LinearProgressIndicator(this);
        progressBar.setIndeterminate(indeterminate);
        if (!indeterminate) {
            progressBar.setMax(100);
            progressBar.setProgress(0);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        box.addView(progressBar, params);

        progressDialog = new MaterialAlertDialogBuilder(this)
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void updateProgress(String message, int percent) {
        if (progressText != null) {
            progressText.setText(message + "  " + percent + "%");
        }
        if (progressBar != null) {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(percent);
        }
    }

    private void dismissProgress() {
        if (progressDialog != null) {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = null;
        }
        progressBar = null;
        progressText = null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int accent() {
        return com.google.android.material.color.MaterialColors.getColor(
                this, androidx.appcompat.R.attr.colorPrimary, getColor(R.color.accent));
    }
}
