/*
 * MVP contract between MainPresenter and MainView.
 * Adds: ignore-list IO, 7z install confirmation, and a richer progress channel.
 */
package com.archivemachine.mvp;

import com.archivemachine.core.destination.DestinationMode;
import com.archivemachine.core.settings.CoreSettings;

import java.util.List;
import java.util.function.Consumer;

public interface MainViewContract {

    // --- action callbacks (view -> presenter) ---
    void onStart(Runnable r);
    void onPause(Runnable r);
    void onResume(Runnable r);
    void onCancel(Runnable r);
    void onReset(Runnable r);

    /** Fired when the user asks the view to add a file/folder to the ignore list. */
    void onAddIgnoreFile(Runnable r);
    void onAddIgnoreFolder(Runnable r);

    /** Fired when the user removes the currently-selected ignore entry; presenter decides which. */
    void onRemoveIgnoreSelected(Consumer<String> selectionConsumer);

    // --- input getters ---
    String getSourcePath();
    String getDestinationPath();
    DestinationMode getDestinationMode();
    boolean isDecompressionEnabled();
    boolean isSkipShortcutsEnabled();
    int getPartitionItemLimit();
    String getSevenZipPath();
    List<String> getIgnoredPaths();
    String getSelectedIgnoredPath();

    // --- output setters ---
    void applySettings(CoreSettings settings);
    void setStatusText(String text);
    void setActiveTaskText(String text);
    void setProgress(double progress0to1);
    void setIgnoredPaths(List<String> paths);
    void setSevenZipPath(String path);
    void showError(String message);

    /** Returns true if the user confirms installation. */
    boolean confirmInstallSevenZip(String message);

    /** Modal multi-select picker. Returns absolute paths (empty list if user cancelled). */
    List<String> chooseFilesToIgnore();
    List<String> chooseFoldersToIgnore();
}
