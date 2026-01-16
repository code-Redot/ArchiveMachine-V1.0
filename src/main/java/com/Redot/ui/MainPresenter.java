/*
 * Purpose: Presenter for the main screen (MVP).
 * Orchestrates the pipeline by translating UI inputs into CoreTasks submitted to TaskManager.
 */
package com.Redot.ui;

import com.Redot.core.destination.*;
import com.Redot.core.settings.CoreSettings;
import com.Redot.core.settings.SettingsStore;
import com.Redot.core.task.TaskManager;
import com.Redot.core.task.events.PipelineEventListener;
import com.Redot.core.tasks.Decompress7ZipTask;
import com.Redot.core.tasks.TransferMoveTask;
import com.Redot.core.util.ArchiveType;
import com.Redot.core.util.Level0Enumerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainPresenter implements PipelineEventListener {

    private final TaskManager taskManager;
    private final SettingsStore settingsStore;

    private MainViewContract view;

    public MainPresenter(TaskManager taskManager, SettingsStore settingsStore) {
        this.taskManager = taskManager;
        this.settingsStore = settingsStore;
        this.taskManager.setListener(this);
    }

    public void attachView(MainViewContract view) {
        this.view = view;

        // View -> Presenter wiring
        view.onStart(this::startPipelineFromView);
        view.onPause(this::pause);
        view.onResume(this::resume);
        view.onCancel(this::cancel);
        view.onReset(this::reset);
    }

    public void onStartup() {
        CoreSettings s = settingsStore.load();
        if (view != null) view.applySettings(s);
    }


    // region Pipeline orchestration
    private void startPipelineFromView() {
        if (view == null) return;

        if (taskManager.getState() == TaskManager.State.RESET_REQUIRED) {
            view.showError("Reset required before starting again.");
            return;
        }

        Path source = Path.of(view.getSourcePath() == null ? "" : view.getSourcePath().trim());
        Path destRoot = Path.of(view.getDestinationPath() == null ? "" : view.getDestinationPath().trim());

        // validation only (allowed in presenter)
        if (source.toString().isBlank() || !Files.isDirectory(source)) {
            view.showError("Invalid source directory.");
            return;
        }
        if (destRoot.toString().isBlank() || !Files.exists(destRoot)) {
            view.showError("Invalid destination directory.");
            return;
        }
        if (!Files.isDirectory(destRoot)) {
            view.showError("Destination must be a directory.");
            return;
        }
        if (destRoot.normalize().startsWith(source.normalize())) {
            view.showError("Destination cannot be inside source.");
            return;
        }

        DestinationMode mode = view.getDestinationMode();
        DestinationStrategy strat = switch (mode) {
            case TRANSFER_DATE -> new TransferDateStrategy();
            case CREATION_DATE -> new CreationDateStrategy();
            case LAST_MODIFIED_DATE -> new LastModifiedDateStrategy();
        };
        final boolean skipShortcuts = view.isSkipShortcutsEnabled();
        final int partitionLimit = Math.max(0, view.getPartitionItemLimit());

        // persist settings
        String sevenZip = view.getSevenZipPath();
        CoreSettings settings = new CoreSettings(
                source.toString(),
                destRoot.toString(),
                view.isDecompressionEnabled(),
                skipShortcuts,
                partitionLimit,
                mode,
                sevenZip == null ? "" : sevenZip.trim()
        );
        settingsStore.save(settings);

        try {

            // region Partition/date folder resolution rules
            // Use the real destination root for date bucketing; partitions (P1/P2/...) are applied
            // *inside* the date folder: {Destination}\{MMM yyyy}\P#\{item}
            DestinationResolver resolver = new DestinationResolver(destRoot, strat);

            // Per-date partition state. On re-runs, we start at (maxExistingPartition + 1) per date bucket.
            Map<Path, Integer> currentPartitionIndexByDateDir = new HashMap<>();
            Map<Path, Integer> currentPartitionCountByDateDir = new HashMap<>();
            Pattern partitionPattern = Pattern.compile("^P(\\d+)$");

            // region Task scheduling (transfer + optional decompression)
            for (Path item : Level0Enumerator.listLevel0Items(source)) {
                String partitionName = null;
                if (partitionLimit > 0) {
                    Path dateDir = resolver.resolveDateDirectory(item);

                    Integer partitionIndex = currentPartitionIndexByDateDir.get(dateDir);
                    Integer partitionCount = currentPartitionCountByDateDir.get(dateDir);

                    if (partitionIndex == null) {
                        partitionIndex = findStartingPartitionIndex(dateDir, partitionPattern);
                        partitionCount = 0;
                    }

                    if (partitionCount != null && partitionCount == partitionLimit) {
                        partitionIndex = partitionIndex + 1;
                        partitionCount = 0;
                    }

                    partitionName = "P" + partitionIndex;
                    currentPartitionIndexByDateDir.put(dateDir, partitionIndex);
                    currentPartitionCountByDateDir.put(dateDir, (partitionCount == null ? 0 : partitionCount) + 1);
                }

                Path finalDest = resolver.resolveFinalDestinationForItem(item, partitionName);

                // one task per level-0 item
                taskManager.submit(new TransferMoveTask(item, finalDest, skipShortcuts));

                // optional post-copy decompress task for archive files only
                if (view.isDecompressionEnabled()
                        && Files.isRegularFile(item)
                        && ArchiveType.isSupportedArchiveName(item.getFileName().toString())) {
                    Path sevenZipExe = (sevenZip == null || sevenZip.isBlank()) ? Path.of("7z") : Path.of(sevenZip.trim());
                    taskManager.submit(new Decompress7ZipTask(finalDest, sevenZipExe));
                }

            }
        } catch (Exception ex) {
            view.showError("Failed to start pipeline: " + ex.getMessage());
        }
    }

    // Computes the starting P# for a date bucket by scanning existing partitions and starting at (max + 1).
    private static int findStartingPartitionIndex(Path dateDir, Pattern partitionPattern) throws IOException {
        if (dateDir == null) return 1;
        if (!Files.isDirectory(dateDir)) return 1;

        int maxP = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dateDir)) {
            for (Path p : ds) {
                if (!Files.isDirectory(p)) continue;
                String name = p.getFileName() == null ? "" : p.getFileName().toString();
                Matcher m = partitionPattern.matcher(name);
                if (!m.matches()) continue;
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > maxP) maxP = n;
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return Math.max(1, maxP + 1);
    }

    // region UI state updates (enable/disable buttons, labels, progress)
    private void pause() {
        taskManager.pause();
        if (view != null) view.setStatusText("Paused");
    }

    private void resume() {
        taskManager.resume();
        if (view != null) view.setStatusText("Running");
    }

    private void cancel() {
        taskManager.cancel();
        // status will be finalized by pipeline callback
    }

    private void reset() {
        taskManager.reset();
        if (view != null) {
            view.setStatusText("Ready");
            view.setProgress(0.0);
            view.setActiveTaskText("");
        }
    }

    // region PipelineEventListener callbacks
    @Override
    public void onPipelineStarted() {
        if (view != null) {
            view.setStatusText("Running");
            view.setProgress(0.0);
        }
    }

    @Override
    public void onPipelineFinishedSuccess() {
        // Auto-reset after success so user can run again immediately.
        if (view != null) {
            view.setStatusText("Ready");
            view.setProgress(0.0);
            view.setActiveTaskText("");
        }
    }

    @Override
    public void onPipelineFinishedCancelled() {
        if (view != null) {
            view.setStatusText("Cancelled (reset required)");
            view.setProgress(0.0);
        }
    }

    @Override
    public void onPipelineFinishedFailed(Throwable error) {
        if (view != null) {
            view.setStatusText("Failed (reset required)");
            view.showError(error == null ? "Unknown error" : String.valueOf(error.getMessage()));
        }
    }
}
