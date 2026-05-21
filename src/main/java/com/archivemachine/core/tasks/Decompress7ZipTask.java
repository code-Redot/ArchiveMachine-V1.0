/*
 * Post-transfer decompression using an external 7-Zip executable.
 * Extracts the archive into a sibling folder ("extract to <name>") and moves the
 * original archive into that folder on success.
 *
 * Audit fixes vs 1.0:
 *  - If a file with the archive's name already exists inside the extract dir
 *    (archive contained a file named like itself), the moved archive is suffixed
 *    instead of overwriting the extracted content.
 */
package com.archivemachine.core.tasks;

import com.archivemachine.core.task.CoreTask;
import com.archivemachine.core.task.TaskState;
import com.archivemachine.core.task.runtime.TaskRuntime;
import com.archivemachine.core.util.ArchiveType;

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

        // Move archive into extracted folder. If it would clobber an extracted file
        // of the same name, suffix the archive copy.
        Path targetArchive = extractDir.resolve(archiveName);
        if (Files.exists(targetArchive)) {
            targetArchive = uniquifyByCounter(targetArchive);
        }
        Files.move(archiveFile, targetArchive);
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

    private static Path uniquifyByCounter(Path desired) {
        String name = desired.getFileName().toString();
        Path dir = desired.getParent();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext  = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            Path candidate = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        return dir.resolve(base + " (collision)" + ext);
    }
}
