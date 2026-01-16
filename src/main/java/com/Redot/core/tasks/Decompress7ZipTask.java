/*
 * Purpose: Post-transfer decompression using an external 7-Zip executable.
 * Extracts the archive into a sibling folder and moves the original archive into that folder on success.
 */
package com.Redot.core.tasks;

import com.Redot.core.task.CoreTask;
import com.Redot.core.task.TaskState;
import com.Redot.core.task.runtime.TaskRuntime;
import com.Redot.core.util.ArchiveType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Decompress7ZipTask implements CoreTask {

    private final String id = UUID.randomUUID().toString();
    private final Path archiveFile;
    private final Path sevenZipExe;

    private volatile TaskState state = TaskState.QUEUED;

    private final AtomicReference<Process> procRef = new AtomicReference<>();
    private final AtomicReference<InputStream> streamRef = new AtomicReference<>();

    public Decompress7ZipTask(Path archiveFile, Path sevenZipExe) {
        this.archiveFile = archiveFile;
        this.sevenZipExe = sevenZipExe;
    }

    @Override public String id() { return id; }

    @Override public String description() { return "Decompress " + archiveFile.getFileName(); }

    @Override public TaskState state() { return state; }

    // Runs 7-Zip as an external process and drains stdout to avoid blocking on full buffers.
    @Override
    public void execute(TaskRuntime runtime) throws Exception {
        state = TaskState.RUNNING;
        runtime.throwIfCancelled();

        if (!Files.isRegularFile(archiveFile)) {
            state = TaskState.FAILED;
            throw new IllegalArgumentException("Archive not found: " + archiveFile);
        }

        String nameLower = archiveFile.getFileName().toString().toLowerCase();
        if (!ArchiveType.isSupportedArchiveName(nameLower)) {
            state = TaskState.FAILED;
            throw new IllegalArgumentException("Unsupported archive type: " + archiveFile);
        }

        String archiveName = archiveFile.getFileName().toString();
        Path parent = archiveFile.getParent();
        Path extractDir = parent.resolve(stripExtension(archiveName));
        Files.createDirectories(extractDir);

        List<String> cmd = List.of(
                sevenZipExe.toString(),
                "x",
                archiveFile.toString(),
                "-o" + extractDir.toString(),
                "-y"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        procRef.set(p);

        InputStream in = p.getInputStream();
        streamRef.set(in);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                runtime.awaitIfPaused();
                if (runtime.isCancelled()) {
                    state = TaskState.CANCELLED;
                    return;
                }

                try {
                    line = br.readLine();
                } catch (Exception readEx) {
                    if (runtime.isCancelled()) {
                        state = TaskState.CANCELLED;
                        return;
                    }
                    throw readEx;
                }

                if (line == null) break;
            }
        } finally {
            streamRef.set(null);
        }

        if (runtime.isCancelled()) {
            state = TaskState.CANCELLED;
            return;
        }

        int exit;
        while (true) {
            try {
                exit = p.waitFor();
                break;
            } catch (InterruptedException ie) {
                if (runtime.isCancelled()) {
                    state = TaskState.CANCELLED;
                    return;
                }
                Thread.currentThread().interrupt();
                // retry; TaskManager will classify interrupt as cancel only when cancelling
            }
        }

        if (runtime.isCancelled()) {
            state = TaskState.CANCELLED;
            return;
        }

        if (exit != 0) {
            state = TaskState.FAILED;
            throw new IllegalStateException("7-Zip failed with exit code " + exit);
        }

        // move archive into extracted folder after success: example/example.rar
        Files.move(archiveFile, extractDir.resolve(archiveName), StandardCopyOption.REPLACE_EXISTING);
        state = TaskState.COMPLETED;
    }

    @Override
    public void onCancel() {
        state = TaskState.CANCELLED;

        InputStream in = streamRef.getAndSet(null);
        if (in != null) {
            try { in.close(); } catch (Exception ignored) {}
        }

        Process p = procRef.getAndSet(null);
        if (p != null) {
            try {
                p.destroy();
                // bounded wait only; never block indefinitely
                if (!p.waitFor(300, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            } catch (Exception ignored) {
                p.destroyForcibly();
            }
        }
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
