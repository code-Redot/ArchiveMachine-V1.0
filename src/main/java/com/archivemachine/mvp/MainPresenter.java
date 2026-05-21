/*
 * Presenter (MVP) — translates view inputs into a PipelinePlanner.Input and submits
 * the resulting CoreTasks to the TaskManager.
 *
 * Adds (1.1):
 *  - 7-Zip locate-or-install on startup.
 *  - Ignore-list add/remove with persistence.
 *  - Case-insensitive source/dest containment check (Windows-safe).
 *  - Empty-pipeline finishes successfully instead of leaving the UI silent.
 *  - Pure pipeline math lives in core.pipeline.PipelinePlanner now.
 */
package com.archivemachine.mvp;

import com.archivemachine.core.destination.*;
import com.archivemachine.core.pipeline.PipelineEventListener;
import com.archivemachine.core.pipeline.PipelinePlanner;
import com.archivemachine.core.settings.CoreSettings;
import com.archivemachine.core.settings.SettingsStore;
import com.archivemachine.core.task.CoreTask;
import com.archivemachine.core.task.TaskManager;
import com.archivemachine.core.util.PathSafety;
import com.archivemachine.io.sevenzip.SevenZipInstaller;
import com.archivemachine.io.sevenzip.SevenZipLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MainPresenter implements PipelineEventListener {

    private final TaskManager taskManager;
    private final SettingsStore settingsStore;
    private final SevenZipLocator sevenZipLocator;
    private final SevenZipInstaller sevenZipInstaller;
    private final PipelinePlanner planner;

    private MainViewContract view;

    public MainPresenter(TaskManager taskManager,
                         SettingsStore settingsStore,
                         SevenZipLocator sevenZipLocator,
                         SevenZipInstaller sevenZipInstaller,
                         PipelinePlanner planner) {
        this.taskManager = taskManager;
        this.settingsStore = settingsStore;
        this.sevenZipLocator = sevenZipLocator;
        this.sevenZipInstaller = sevenZipInstaller;
        this.planner = planner;
        this.taskManager.setListener(this);
    }

    public void attachView(MainViewContract view) {
        this.view = view;
        view.onStart(this::startPipelineFromView);
        view.onPause(this::pause);
        view.onResume(this::resume);
        view.onCancel(this::cancel);
        view.onReset(this::reset);
        view.onAddIgnoreFile(this::addIgnoreFile);
        view.onAddIgnoreFolder(this::addIgnoreFolder);
        view.onRemoveIgnoreSelected(this::removeIgnoreSelected);
    }

    public void onStartup() {
        CoreSettings s = settingsStore.load();
        if (view != null) view.applySettings(s);
        ensureSevenZipAvailable(s);
    }

    private void ensureSevenZipAvailable(CoreSettings settings) {
        Optional<Path> found = sevenZipLocator.locate(settings.sevenZipPath());
        if (found.isPresent()) {
            if (view != null) view.setSevenZipPath(found.get().toString());
            return;
        }
        if (view == null) return;

        boolean canInstall = sevenZipInstaller.isBundleAvailable();
        String msg = canInstall
                ? "ArchiveMachine couldn't find 7-Zip on this system. Install it now?\n\n"
                        + "The bundled official installer will run silently and add 7-Zip to your system."
                : "ArchiveMachine couldn't find 7-Zip on this system and no installer is bundled with this build.\n\n"
                        + "You can install it manually from https://www.7-zip.org/download.html and then re-launch the app, "
                        + "or pick the 7z executable manually in the Pipeline tab.";

        if (!canInstall) {
            view.showError(msg);
            return;
        }
        if (!view.confirmInstallSevenZip(msg)) return;

        SevenZipInstaller.Result result = sevenZipInstaller.install();
        switch (result) {
            case INSTALLED -> {
                Optional<Path> afterInstall = sevenZipLocator.locate(null);
                if (afterInstall.isPresent()) {
                    view.setSevenZipPath(afterInstall.get().toString());
                    view.setStatusText("7-Zip installed");
                } else {
                    view.showError("7-Zip installer ran successfully but the executable could not be located. "
                            + "You may need to restart the app.");
                }
            }
            case NOT_BUNDLED -> view.showError("Installer resource missing.");
            case UNSUPPORTED_OS -> view.showError("Automatic 7-Zip install is only supported on Windows.");
            case FAILED -> view.showError("7-Zip installer failed. Please install 7-Zip manually.");
        }
    }

    // region Start

    private void startPipelineFromView() {
        if (view == null) return;

        if (taskManager.getState() == TaskManager.State.RESET_REQUIRED) {
            view.showError("Reset required before starting again.");
            return;
        }

        Path source = Path.of(view.getSourcePath() == null ? "" : view.getSourcePath().trim());
        Path destRoot = Path.of(view.getDestinationPath() == null ? "" : view.getDestinationPath().trim());

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
        if (PathSafety.isInsideOrEqual(source, destRoot)) {
            view.showError("Destination cannot be inside or equal to source.");
            return;
        }

        DestinationMode mode = view.getDestinationMode();
        BucketStrategy strategy = switch (mode) {
            case TRANSFER_DATE -> new TransferDateStrategy();
            case CREATION_DATE -> new CreationDateStrategy();
            case LAST_MODIFIED_DATE -> new LastModifiedDateStrategy();
            case FIRST_LETTER -> new FirstLetterStrategy();
        };

        boolean skipShortcuts = view.isSkipShortcutsEnabled();
        int partitionLimit = Math.max(0, view.getPartitionItemLimit());
        boolean decompress = view.isDecompressionEnabled();
        String sevenZipRaw = view.getSevenZipPath();
        Path sevenZipExe = (sevenZipRaw == null || sevenZipRaw.isBlank())
                ? Path.of("7z") : Path.of(sevenZipRaw.trim());
        List<String> ignored = view.getIgnoredPaths();

        // persist
        CoreSettings settings = new CoreSettings(
                source.toString(),
                destRoot.toString(),
                decompress,
                skipShortcuts,
                partitionLimit,
                mode,
                sevenZipExe.toString(),
                ignored
        );
        settingsStore.save(settings);

        try {
            PipelinePlanner.Input planInput = new PipelinePlanner.Input(
                    source, destRoot, strategy,
                    skipShortcuts, partitionLimit,
                    decompress, sevenZipExe, ignored
            );
            List<CoreTask> tasks = planner.plan(planInput);
            if (tasks.isEmpty()) {
                view.setStatusText("Nothing to transfer (source empty or all items ignored)");
                view.setProgress(0.0);
                return;
            }
            for (CoreTask t : tasks) taskManager.submit(t);
        } catch (Exception ex) {
            view.showError("Failed to start pipeline: " + ex.getMessage());
        }
    }

    // region Ignore list ops

    private void addIgnoreFile() {
        if (view == null) return;
        String p = view.chooseFileToIgnore();
        if (p == null || p.isBlank()) return;
        appendIgnoredAndPersist(p);
    }

    private void addIgnoreFolder() {
        if (view == null) return;
        String p = view.chooseFolderToIgnore();
        if (p == null || p.isBlank()) return;
        appendIgnoredAndPersist(p);
    }

    private void removeIgnoreSelected(String selected) {
        if (view == null) return;
        if (selected == null || selected.isBlank()) return;
        List<String> next = new ArrayList<>(view.getIgnoredPaths());
        next.remove(selected);
        view.setIgnoredPaths(next);
        persistIgnored(next);
    }

    private void appendIgnoredAndPersist(String path) {
        List<String> next = new ArrayList<>(view.getIgnoredPaths());
        if (!next.contains(path)) next.add(path);
        view.setIgnoredPaths(next);
        persistIgnored(next);
    }

    private void persistIgnored(List<String> ignored) {
        CoreSettings current = settingsStore.load();
        settingsStore.save(new CoreSettings(
                current.lastSourceDirectory(),
                current.lastDestinationDirectory(),
                current.decompressionEnabled(),
                current.skipShortcutsEnabled(),
                current.partitionItemLimit(),
                current.destinationMode(),
                current.sevenZipPath(),
                ignored
        ));
    }

    // region Control

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
    }

    private void reset() {
        taskManager.reset();
        if (view != null) {
            view.setStatusText("Ready");
            view.setProgress(0.0);
            view.setActiveTaskText("");
        }
    }

    // region PipelineEventListener
    @Override
    public void onPipelineStarted() {
        if (view != null) {
            view.setStatusText("Running");
            view.setProgress(0.0);
        }
    }

    @Override
    public void onPipelineFinishedSuccess() {
        if (view != null) {
            view.setStatusText("Done");
            view.setProgress(1.0);
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

    @Override
    public void onTaskStarted(String description) {
        if (view != null && description != null) view.setActiveTaskText(description);
    }

    @Override
    public void onProgress(int completed, int total) {
        if (view != null && total > 0) view.setProgress((double) completed / (double) total);
    }
}
