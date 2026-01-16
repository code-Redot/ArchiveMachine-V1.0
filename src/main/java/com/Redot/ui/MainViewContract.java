/*
 * Purpose: Contract between MainPresenter and MainView (MVP).
 * Exposes user inputs, UI outputs, and action callbacks without leaking UI widgets.
 */
package com.Redot.ui;

import com.Redot.core.destination.DestinationMode;
import com.Redot.core.settings.CoreSettings;

public interface MainViewContract {

    // --- event registration ---
    void onStart(Runnable r);
    void onPause(Runnable r);
    void onResume(Runnable r);
    void onCancel(Runnable r);
    void onReset(Runnable r);

    // --- inputs ---
    String getSourcePath();
    String getDestinationPath();
    DestinationMode getDestinationMode();
    boolean isDecompressionEnabled();
    boolean isSkipShortcutsEnabled();
    int getPartitionItemLimit();
    String getSevenZipPath();

    // --- settings IO (UI only reflects; persistence is core) ---
    void applySettings(CoreSettings settings);

    // --- outputs ---
    void setStatusText(String text);
    void setActiveTaskText(String text);
    void setProgress(double progress0to1);
    void showError(String message);
}
