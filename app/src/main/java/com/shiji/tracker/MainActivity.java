package com.shiji.tracker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shiji.tracker.data.BackupManager;
import com.shiji.tracker.data.UsageRepository;
import com.shiji.tracker.model.UsageModel.Snapshot;
import com.shiji.tracker.ui.BottomNav;
import com.shiji.tracker.ui.HourlyScreen;
import com.shiji.tracker.ui.RankingScreen;
import com.shiji.tracker.ui.ScreenHost;
import com.shiji.tracker.ui.SettingsScreen;
import com.shiji.tracker.ui.StateViews;
import com.shiji.tracker.ui.Theme;
import com.shiji.tracker.ui.TodayScreen;
import com.shiji.tracker.ui.TrendScreen;
import com.shiji.tracker.ui.Ui;
import com.shiji.tracker.ui.AuroraBackgroundView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-activity shell. Screens are programmatic so there are no resource
 * layout dependencies, and all data access runs on one background executor.
 */
public final class MainActivity extends Activity implements ScreenHost {

    private static final int REQUEST_EXPORT = 4101;
    private static final int REQUEST_IMPORT = 4102;
    private static final long AUTO_SYNC_INTERVAL_MS = 60_000L;

    private Theme theme;
    private UsageRepository repository;
    private ExecutorService worker;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout shell;
    private FrameLayout topBar;
    private FrameLayout content;
    private BottomNav bottomNav;

    private Snapshot snapshot;
    private boolean hasPermission;
    private boolean loading;
    private boolean syncRunning;
    private boolean initialLoadDone;
    private boolean firstResume = true;
    private boolean destroyed;
    private long lastSyncAttempt;
    private long snackToken;

    private int currentTab;
    private int previousTab;
    private boolean settingsOpen;
    private int rankingRange = 1;
    private int hoursRange = 0;
    private int trendRange = 0;
    /** 0 = automatic (current/latest year), -1 = multi-year, >0 = a concrete year. */
    private int selectedYear;

    private boolean pickerOpened;
    private boolean skipResumeSync;
    private boolean fileBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = Theme.resolve(this);
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt("tab", 0);
            previousTab = savedInstanceState.getInt("previousTab", 0);
            settingsOpen = savedInstanceState.getBoolean("settingsOpen", false);
            rankingRange = savedInstanceState.getInt("rankingRange", 1);
            hoursRange = savedInstanceState.getInt("hoursRange", 0);
            trendRange = savedInstanceState.getInt("trendRange", 0);
            selectedYear = savedInstanceState.getInt("selectedYear", 0);
        }
        repository = new UsageRepository(getApplicationContext());
        worker = Executors.newSingleThreadExecutor();
        hasPermission = repository.hasUsageAccess();

        buildShell();
        applyWindowStyle();
        applyInsets();
        renderTopBar();
        renderContent(false);
        beginLoad(true);

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220L).start();
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(theme.background);
        root.addView(new AuroraBackgroundView(this, theme),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        topBar = new FrameLayout(this);
        shell.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52f)));

        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomNav = new BottomNav(this, theme, currentTab, this::selectTab);
        shell.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            shell.setPadding(left, top, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void applyWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(theme.dark ? 0 : mask, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!theme.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void renderTopBar() {
        topBar.removeAllViews();
        if (settingsOpen) {
            ImageView back = Ui.touchIcon(this, theme, R.drawable.ic_back, theme.secondary,
                    getString(R.string.cd_back), v -> closeSettings());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Ui.dp(this, 48f), Ui.dp(this, 48f));
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            params.leftMargin = Ui.dp(this, 4f);
            topBar.addView(back, params);
            return;
        }

        TextView date = Ui.text(this, Ui.dateLine(LocalDate.now()), 12f,
                theme.secondary, true);
        date.setLetterSpacing(0.03f);
        date.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
        dateParams.gravity = Gravity.START;
        dateParams.leftMargin = Ui.dp(this, 20f);
        topBar.addView(date, dateParams);

        ImageView settings = Ui.touchIcon(this, theme, R.drawable.ic_settings,
                theme.secondary, getString(R.string.cd_settings), v -> openSettings());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 48f), Ui.dp(this, 48f));
        settingsParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        settingsParams.rightMargin = Ui.dp(this, 4f);
        topBar.addView(settings, settingsParams);
    }

    private void renderContent(boolean animate) {
        bottomNav.setVisibility(settingsOpen ? View.GONE : View.VISIBLE);
        if (!settingsOpen) {
            bottomNav.setSelectedTab(currentTab);
        }
        content.removeAllViews();
        View screen;
        if (settingsOpen) {
            screen = new SettingsScreen(this, theme, Theme.readMode(this), hasPermission, this);
        } else if (snapshot == null) {
            if (loading && hasPermission) {
                screen = StateViews.loading(this, theme);
            } else if (!hasPermission) {
                screen = StateViews.permission(this, theme, this::openUsageAccess);
            } else {
                screen = StateViews.empty(this, theme, false, this::refresh,
                        this::openUsageAccess);
            }
        } else {
            switch (currentTab) {
                case 1:
                    screen = new RankingScreen(this, theme, snapshot, hasPermission, this,
                            rankingRange, selectedYear);
                    break;
                case 2:
                    screen = new HourlyScreen(this, theme, snapshot, hasPermission, this,
                            hoursRange, selectedYear);
                    break;
                case 3:
                    screen = new TrendScreen(this, theme, snapshot, hasPermission, this,
                            trendRange, selectedYear);
                    break;
                case 0:
                default:
                    screen = new TodayScreen(this, theme, snapshot, hasPermission, loading, this);
                    break;
            }
        }
        content.addView(screen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (animate) {
            screen.setAlpha(0f);
            screen.setTranslationY(Ui.dpf(this, 8f));
            screen.animate().alpha(1f).translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    private void beginLoad(boolean syncFromSystem) {
        if (syncRunning || destroyed) {
            return;
        }
        syncRunning = true;
        loading = true;
        lastSyncAttempt = SystemClock.elapsedRealtime();
        final boolean canSync = hasPermission && syncFromSystem;
        if (snapshot == null) {
            renderContent(false);
        }
        worker.execute(() -> {
            Snapshot loaded = null;
            Throwable failure = null;
            try {
                if (canSync) {
                    repository.syncHistory();
                }
                loaded = repository.loadSnapshot();
            } catch (Throwable error) {
                failure = error;
            }
            final Snapshot next = loaded;
            final Throwable error = failure;
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                syncRunning = false;
                loading = false;
                initialLoadDone = true;
                if (next != null) {
                    snapshot = next;
                    normalizeYearSelection();
                }
                hasPermission = repository.hasUsageAccess();
                renderTopBar();
                renderContent(false);
                if (error != null) {
                    showSnackbar(getString(R.string.msg_read_failed));
                }
            });
        });
    }

    private void selectTab(int index) {
        if (index < 0 || index > 3) {
            return;
        }
        boolean wasSettings = settingsOpen;
        if (wasSettings) {
            settingsOpen = false;
            bottomNav.setVisibility(View.VISIBLE);
        }
        if (index == currentTab && !wasSettings) {
            return;
        }
        currentTab = index;
        previousTab = index;
        renderTopBar();
        renderContent(true);
    }

    @Override
    public void openSettings() {
        if (settingsOpen) {
            return;
        }
        previousTab = currentTab;
        settingsOpen = true;
        renderTopBar();
        renderContent(true);
    }

    @Override
    public void closeSettings() {
        if (!settingsOpen) {
            return;
        }
        settingsOpen = false;
        currentTab = previousTab;
        renderTopBar();
        renderContent(true);
    }

    @Override
    public void openUsageAccess() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException second) {
                showSnackbar(getString(R.string.msg_read_failed));
            }
        }
    }

    @Override
    public void refresh() {
        beginLoad(hasPermission);
    }

    @Override
    public void setThemeMode(int mode) {
        if (Theme.readMode(this) == mode) {
            return;
        }
        Theme.writeMode(this, mode);
        recreate();
    }

    @Override
    public void exportData() {
        if (fileBusy) {
            return;
        }
        String stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "shiji-usage-" + stamp + ".json");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        launchPicker(intent, REQUEST_EXPORT);
    }

    @Override
    public void importData() {
        if (fileBusy) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/plain", "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        launchPicker(intent, REQUEST_IMPORT);
    }

    private void launchPicker(Intent intent, int requestCode) {
        try {
            pickerOpened = true;
            skipResumeSync = true;
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException error) {
            pickerOpened = false;
            skipResumeSync = false;
            showSnackbar(getString(R.string.msg_no_picker));
        }
    }

    @Override
    public void setRankingRange(int index) {
        rankingRange = Math.max(0, Math.min(3, index));
    }

    @Override
    public void setHoursRange(int index) {
        hoursRange = Math.max(0, Math.min(3, index));
    }

    @Override
    public void setTrendRange(int index) {
        trendRange = Math.max(0, Math.min(2, index));
    }

    @Override
    public void setSelectedYear(int year) {
        selectedYear = year >= -1 ? year : 0;
    }

    private void normalizeYearSelection() {
        if (snapshot == null) {
            return;
        }
        if (selectedYear == -1) {
            if (snapshot.availableYears.size() <= 1) {
                selectedYear = 0;
            }
            return;
        }
        if (selectedYear > 0 && !snapshot.hasYear(selectedYear)) {
            selectedYear = 0;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        pickerOpened = false;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            runExport(uri);
        } else if (requestCode == REQUEST_IMPORT) {
            runImport(uri);
        }
    }

    private void runExport(Uri uri) {
        if (fileBusy) {
            return;
        }
        fileBusy = true;
        final ContentResolver resolver = getContentResolver();
        worker.execute(() -> {
            Throwable error = null;
            try {
                BackupManager.exportJson(resolver, uri, repository.db(), versionName());
            } catch (Throwable failure) {
                error = failure;
            }
            final Throwable failure = error;
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                fileBusy = false;
                showSnackbar(getString(failure == null
                        ? R.string.msg_exported : R.string.msg_export_failed));
            });
        });
    }

    private void runImport(Uri uri) {
        if (fileBusy) {
            return;
        }
        fileBusy = true;
        final ContentResolver resolver = getContentResolver();
        worker.execute(() -> {
            BackupManager.ImportResult result = null;
            Snapshot loaded = null;
            Throwable error = null;
            try {
                result = BackupManager.importJson(resolver, uri, repository.db());
                loaded = repository.loadSnapshot();
            } catch (Throwable failure) {
                error = failure;
            }
            final BackupManager.ImportResult importResult = result;
            final Snapshot next = loaded;
            final Throwable failure = error;
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                fileBusy = false;
                if (next != null) {
                    snapshot = next;
                    normalizeYearSelection();
                }
                hasPermission = repository.hasUsageAccess();
                renderTopBar();
                renderContent(false);
                if (failure == null && importResult != null) {
                    showSnackbar(getString(R.string.msg_imported, importResult.days));
                } else {
                    showSnackbar(getString(R.string.msg_import_failed));
                }
            });
        });
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Throwable ignored) {
            return "1.0.0";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository == null) {
            return;
        }
        if (pickerOpened || skipResumeSync) {
            skipResumeSync = false;
            pickerOpened = false;
            hasPermission = repository.hasUsageAccess();
            return;
        }
        boolean wasPermission = hasPermission;
        boolean nowPermission = repository.hasUsageAccess();
        hasPermission = nowPermission;
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (settingsOpen) {
            // The settings screen shows the current permission state without a
            // disruptive rebuild while the user is interacting with it.
            renderTopBar();
        }
        long elapsed = SystemClock.elapsedRealtime() - lastSyncAttempt;
        if (nowPermission && (!wasPermission || elapsed > AUTO_SYNC_INTERVAL_MS)) {
            beginLoad(true);
        } else if (!nowPermission && wasPermission) {
            renderContent(false);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("tab", currentTab);
        outState.putInt("previousTab", previousTab);
        outState.putBoolean("settingsOpen", settingsOpen);
        outState.putInt("rankingRange", rankingRange);
        outState.putInt("hoursRange", hoursRange);
        outState.putInt("trendRange", trendRange);
        outState.putInt("selectedYear", selectedYear);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (settingsOpen) {
            closeSettings();
            return;
        }
        if (currentTab != 0) {
            selectTab(0);
            return;
        }
        super.onBackPressed();
    }

    private void showSnackbar(String message) {
        final long token = ++snackToken;
        TextView bar = Ui.text(this, message, 12f, theme.onAccent, true);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(Ui.dp(this, 18f), Ui.dp(this, 11f),
                Ui.dp(this, 18f), Ui.dp(this, 11f));
        bar.setBackground(Ui.fill(this, theme.accent, 20f));
        bar.setElevation(Ui.dpf(this, 6f));
        bar.setClickable(false);
        bar.setFocusable(false);
        bar.setAlpha(0f);
        bar.setTranslationY(Ui.dpf(this, 8f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = Ui.dp(this, 14f);
        content.addView(bar, params);
        bar.bringToFront();
        bar.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        mainHandler.postDelayed(() -> {
            if (token != snackToken) {
                return;
            }
            bar.animate().alpha(0f).translationY(Ui.dpf(this, 8f))
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (bar.getParent() == content) {
                            content.removeView(bar);
                        }
                    }).start();
        }, 2300L);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (worker != null) {
            worker.shutdownNow();
        }
        super.onDestroy();
    }
}
