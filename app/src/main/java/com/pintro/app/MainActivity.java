package com.pintro.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PinAdapter.Listener {

    private static final int MODE_HOME = 0;
    private static final int MODE_SEARCH = 1;
    private static final int MODE_SAVED = 2;

    private static final String[] DISCOVER = {
            "wallpaper mobile",
            "home decor",
            "outfit inspo",
            "digital art",
            "nature photography",
            "food photography",
            "travel",
            "anime aesthetic",
            "interior design",
            "flowers"
    };

    private static final String[][] CHIPS = {
            {"For you", ""},
            {"Wallpapers", "wallpaper mobile"},
            {"Home", "home decor"},
            {"Art", "digital art"},
            {"Fashion", "outfit inspo"},
            {"Nature", "nature photography"},
            {"Food", "food photography"},
            {"Travel", "travel"},
            {"Anime", "anime aesthetic"},
            {"Photography", "photography"}
    };

    private RecyclerView grid;
    private SwipeRefreshLayout refresh;
    private ProgressBar loading;
    private View empty;
    private TextView emptyTitle;
    private TextView emptyText;
    private MaterialButton emptyAction;
    private EditText search;
    private LinearLayout chipRow;
    private ImageButton navHome;
    private ImageButton navSaved;

    private StaggeredGridLayoutManager layoutManager;
    private PinAdapter adapter;
    private PinterestClient client;
    private SavedStore savedStore;
    private TasteStore tasteStore;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Pin> pins = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private final Map<String, String> bookmarks = new HashMap<>();
    private final Set<String> endedDiscover = new HashSet<>();
    private final List<String> discoverQueue = new ArrayList<>();

    private int spanCount = 2;
    private int mode = MODE_HOME;
    private String query = DISCOVER[0];
    private boolean discover = true;
    private int discoverIndex = 0;
    private String bookmark;
    private boolean loadingPage;
    private boolean end;
    private long generation;
    private boolean settingText;
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        client = new PinterestClient(this);
        savedStore = new SavedStore(this);
        tasteStore = new TasteStore(this);

        grid = findViewById(R.id.grid);
        refresh = findViewById(R.id.refresh);
        loading = findViewById(R.id.loading);
        empty = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.empty_title);
        emptyText = findViewById(R.id.empty_text);
        emptyAction = findViewById(R.id.empty_action);
        search = findViewById(R.id.search);
        chipRow = findViewById(R.id.chip_row);
        navHome = findViewById(R.id.nav_home);
        navSaved = findViewById(R.id.nav_saved);

        spanCount = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        adapter = new PinAdapter(this, spanCount, getResources().getDisplayMetrics().widthPixels);
        layoutManager = new StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        grid.setLayoutManager(layoutManager);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.setItemViewCacheSize(24);

        refresh.setColorSchemeColors(accent());
        refresh.setProgressBackgroundColorSchemeResource(R.color.surface);
        refresh.setOnRefreshListener(() -> {
            if (mode == MODE_SAVED) {
                showSaved();
                refresh.setRefreshing(false);
            } else {
                loadFirst(true);
            }
        });

        setupSearch();
        setupChips();
        setupNavigation();
        setupScroll();

        findViewById(R.id.settings).setOnClickListener(v -> showSettings());

        if (savedInstanceState != null) {
            mode = savedInstanceState.getInt("mode", MODE_HOME);
            query = savedInstanceState.getString("query", DISCOVER[0]);
            discover = savedInstanceState.getBoolean("discover", true);
            setSearchText(savedInstanceState.getString("text", ""));
            if (mode == MODE_SAVED) {
                showSaved();
            } else {
                updateChipSelection(discover ? 0 : -1);
                setNavChecked(R.id.nav_home);
                loadFirst(false);
            }
        } else {
            loadDiscover();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mode == MODE_SAVED) {
            showSaved();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mode", mode);
        outState.putString("query", query);
        outState.putBoolean("discover", discover);
        outState.putString("text", search.getText().toString());
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onPinClick(Pin pin) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("pin", pin);
        startActivity(intent);
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (settingText) {
                    return;
                }
                if (searchRunnable != null) {
                    main.removeCallbacks(searchRunnable);
                    searchRunnable = null;
                }
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    if (mode != MODE_SAVED) {
                        searchRunnable = MainActivity.this::loadDiscover;
                    }
                } else if (text.length() >= 2) {
                    searchRunnable = () -> performSearch(text);
                }
                if (searchRunnable != null) {
                    main.postDelayed(searchRunnable, 700);
                }
            }
        });

        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                String text = search.getText().toString().trim();
                if (!text.isEmpty()) {
                    performSearch(text);
                }
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void setupChips() {
        for (int i = 0; i < CHIPS.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(CHIPS[i][0]);
            chip.setTextSize(14);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setTextColor(getColorStateList(R.color.chip_text));
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            chip.setSelected(i == 0);
            chip.setClickable(true);
            chip.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            final int index = i;
            chip.setOnClickListener(v -> onChipClicked(index));
            chipRow.addView(chip);
        }
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> {
            if (mode == MODE_HOME) {
                grid.smoothScrollToPosition(0);
            } else {
                loadDiscover();
            }
        });
        navSaved.setOnClickListener(v -> showSaved());
    }

    private void setupScroll() {
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    hideKeyboard();
                }
                int[] positions = layoutManager.findLastVisibleItemPositions(null);
                int last = 0;
                for (int position : positions) {
                    last = Math.max(last, position);
                }
                if (last >= adapter.getItemCount() - 8) {
                    loadMore();
                }
            }
        });
    }

    private void onChipClicked(int index) {
        updateChipSelection(index);
        discover = index == 0;
        if (index == 0) {
            query = DISCOVER[0];
            setSearchText("");
            mode = MODE_HOME;
            setNavChecked(R.id.nav_home);
        } else {
            query = CHIPS[index][1];
            tasteStore.record(query);
            setSearchText(query);
            mode = MODE_SEARCH;
            setNavChecked(R.id.nav_home);
        }
        hideKeyboard();
        loadFirst(false);
    }

    private void loadDiscover() {
        mode = MODE_HOME;
        discover = true;
        query = DISCOVER[0];
        setSearchText("");
        updateChipSelection(0);
        setNavChecked(R.id.nav_home);
        loadFirst(false);
    }

    private void performSearch(String text) {
        String clean = text.trim();
        if (clean.isEmpty()) {
            loadDiscover();
            return;
        }
        mode = MODE_SEARCH;
        discover = false;
        query = clean;
        tasteStore.record(clean);
        setSearchText(clean);
        updateChipSelection(-1);
        setNavChecked(R.id.nav_home);
        loadFirst(false);
    }

    private void showSaved() {
        mode = MODE_SAVED;
        discover = false;
        generation++;
        loadingPage = false;
        end = true;
        setSearchText("");
        updateChipSelection(-1);
        setNavChecked(R.id.nav_saved);
        showLoading(false);
        refresh.setRefreshing(false);

        List<Pin> saved = savedStore.all();
        pins.clear();
        seen.clear();
        pins.addAll(saved);
        adapter.setPins(saved);

        if (saved.isEmpty()) {
            showEmpty("Nothing saved yet", "Tap Save on any pin to keep it here.", "Explore", this::loadDiscover);
        } else {
            hideEmpty();
        }
    }

    private void loadFirst(boolean fromRefresh) {
        generation++;
        loadingPage = false;
        end = false;
        bookmark = null;
        discoverIndex = 0;
        bookmarks.clear();
        endedDiscover.clear();
        discoverQueue.clear();
        if (mode == MODE_HOME && discover) {
            String taste = tasteStore.best("");
            if (!taste.isEmpty()) {
                discoverQueue.add(taste);
            }
            for (String item : DISCOVER) {
                if (!discoverQueue.contains(item)) {
                    discoverQueue.add(item);
                }
            }
        }
        pins.clear();
        seen.clear();
        adapter.clear();
        hideEmpty();
        if (!fromRefresh) {
            showLoading(true);
        }
        fetchPage(generation);
    }

    private void loadMore() {
        if (loadingPage || end || mode == MODE_SAVED || pins.isEmpty()) {
            return;
        }
        fetchPage(generation);
    }

    private void fetchPage(long requestGeneration) {
        if (loadingPage) {
            return;
        }

        final String pageQuery;
        final String pageBookmark;
        final boolean isDiscover = discover;

        if (isDiscover) {
            String[] next = nextDiscoverPage();
            if (next == null) {
                loadingPage = false;
                end = true;
                showLoading(false);
                refresh.setRefreshing(false);
                if (pins.isEmpty()) {
                    showEmpty("You're all caught up", "Pull to refresh for fresh ideas.", "Refresh", () -> loadFirst(false));
                } else {
                    Toast.makeText(this, "You're all caught up", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            pageQuery = next[0];
            pageBookmark = next[1];
        } else {
            pageQuery = query;
            pageBookmark = bookmark;
        }

        loadingPage = true;

        io.execute(() -> {
            try {
                PinterestClient.Page page = client.search(pageQuery, pageBookmark);

                main.post(() -> {
                    if (requestGeneration != generation) {
                        return;
                    }
                    loadingPage = false;
                    showLoading(false);
                    refresh.setRefreshing(false);

                    List<Pin> fresh = new ArrayList<>();
                    for (Pin pin : page.pins) {
                        if (seen.add(pin.id)) {
                            fresh.add(pin);
                        }
                    }

                    if (isDiscover) {
                        if (page.bookmark == null) {
                            endedDiscover.add(pageQuery);
                            bookmarks.remove(pageQuery);
                        } else {
                            bookmarks.put(pageQuery, page.bookmark);
                        }
                    } else {
                        bookmark = page.bookmark;
                        if (page.bookmark == null) {
                            end = true;
                        }
                    }

                    if (!fresh.isEmpty()) {
                        pins.addAll(fresh);
                        adapter.append(fresh);
                        hideEmpty();
                    }

                    if (pins.isEmpty()) {
                        showEmpty("Nothing here yet", "Try another search or pull to refresh.", "Retry", () -> loadFirst(false));
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestGeneration != generation) {
                        return;
                    }
                    loadingPage = false;
                    showLoading(false);
                    refresh.setRefreshing(false);
                    if (pins.isEmpty()) {
                        showEmpty("Couldn't reach Pinterest", friendly(e), "Retry", () -> loadFirst(false));
                    } else {
                        Toast.makeText(MainActivity.this, friendly(e), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private String[] nextDiscoverPage() {
        if (discoverQueue.isEmpty() || endedDiscover.size() >= discoverQueue.size()) {
            return null;
        }
        for (int i = 0; i < discoverQueue.size(); i++) {
            String candidate = discoverQueue.get(discoverIndex % discoverQueue.size());
            discoverIndex++;
            if (endedDiscover.contains(candidate)) {
                continue;
            }
            return new String[]{candidate, bookmarks.get(candidate)};
        }
        return null;
    }

    private void updateChipSelection(int selected) {
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            chipRow.getChildAt(i).setSelected(i == selected);
        }
    }

    private void setNavChecked(int id) {
        boolean home = id == R.id.nav_home;
        styleNav(navHome, home);
        styleNav(navSaved, !home);
    }

    private void styleNav(ImageButton button, boolean active) {
        int primary = MaterialColors.getColor(button, androidx.appcompat.R.attr.colorPrimary, getColor(R.color.accent));
        int onPrimary = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimary, getColor(R.color.white));
        int surface = MaterialColors.getColor(button, com.google.android.material.R.attr.colorSurface, getColor(R.color.surface));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(18));
        shape.setColor(active ? primary : surface);

        int rippleColor = active ? 0x33FFFFFF : getColor(R.color.ripple);
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null);
        button.setBackground(ripple);
        button.setImageTintList(ColorStateList.valueOf(active ? onPrimary : getColor(R.color.text)));
        button.setElevation(dp(active ? 10 : 6));
    }

    private void setSearchText(String text) {
        settingText = true;
        search.setText(text);
        if (!text.isEmpty()) {
            search.setSelection(text.length());
        }
        settingText = false;
        if (searchRunnable != null) {
            main.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    private void showLoading(boolean show) {
        loading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            empty.setVisibility(View.GONE);
        }
    }

    private void showEmpty(String title, String message, String action, Runnable runnable) {
        emptyTitle.setText(title);
        emptyText.setText(message);
        if (action == null) {
            emptyAction.setVisibility(View.GONE);
        } else {
            emptyAction.setVisibility(View.VISIBLE);
            emptyAction.setText(action);
            emptyAction.setOnClickListener(v -> runnable.run());
        }
        empty.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
    }

    private void hideEmpty() {
        empty.setVisibility(View.GONE);
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(4));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);

        MaterialButton cache = settingsButton("Clear cache & algorithm");
        box.addView(cache, rowParams);

        MaterialButton about = settingsButton("About app");
        box.addView(about, rowParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Settings")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();

        cache.setOnClickListener(v -> {
            dialog.dismiss();
            clearCache();
        });
        about.setOnClickListener(v -> {
            dialog.dismiss();
            openGitHub();
        });

        dialog.show();
    }

    private MaterialButton settingsButton(String text) {
        MaterialButton button = (MaterialButton) getLayoutInflater()
                .inflate(R.layout.item_settings_button, null, false);
        button.setText(text);
        return button;
    }

    private void openGitHub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/soynd/intro")));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the link", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCache() {
        tasteStore.clear();
        Toast.makeText(this, "Clearing cache", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            Glide.get(this).clearDiskCache();
            main.post(() -> {
                Glide.get(this).clearMemory();
                Toast.makeText(this, "Cache and algorithm cleared", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        search.postDelayed(() -> {
            search.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private String friendly(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return "Check your connection and try again.";
        }
        if (message.contains("limiting")) {
            return message;
        }
        return "Check your connection and try again.";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int accent() {
        return com.google.android.material.color.MaterialColors.getColor(
                this, androidx.appcompat.R.attr.colorPrimary, getColor(R.color.accent));
    }
}
