package com.shiji.tracker.ui;

/** Narrow contract between content screens and the activity shell. */
public interface ScreenHost {
    void openSettings();

    void closeSettings();

    void openUsageAccess();

    void refresh();

    void setThemeMode(int mode);

    void exportData();

    void importData();

    void setRankingRange(int index);

    void setHoursRange(int index);

    void setTrendRange(int index);

    void setSelectedYear(int year);
}
