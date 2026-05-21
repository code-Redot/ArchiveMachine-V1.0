/*
 * Transfers a level-0 item (file or directory) into its resolved destination.
 * Honors skip-shortcuts (.lnk/.url + symlinks/junctions) and the runtime pause/cancel contract.
 *
 * Audit fixes vs 1.0:
 *  - Refuses to overwrite an existing destination (was silent REPLACE_EXISTING on cross-volume).
 *  - System-folder items are filtered earlier (in the enumerator), not here.
 */
package com.archivemachine.core.tasks;

import com.archivemachine.core.task.CoreTask;
import com.archivemachine.core.task.TaskState;
import com.archivemachine.core.task.runtime.TaskRuntime;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.UUID;

public final class TransferMoveTask implements CoreTask {

    private final String id = UUID.randomUUID().toString();
    private final Path sourceItem;
    private final Path finalDestination;
    private final boolean skipShortcuts;

    private volatile TaskState state = TaskState.QUEUED;

    public TransferMoveTask(Path sourceItem, Path finalDestination, boolean skipShortcuts) {
        this.sourceItem = sourceItem;
        this.finalDestination = finalDestination;
        this.skipShortcuts = skipShortcuts;
    }

    @Override public String id() { return id; }
    @Override public String description() { return "Transfer " + sourceItem.getFileName(); }
    @Override public TaskState state() { return state; }

    @Override
    public void execute(TaskRuntime runtime) throws Exception {
        state = TaskState.RUNNING;
        runtime.throwIfCancelled();

        if (skipShortcuts && shouldSkipLevel0(sourceItem)) {
            state = TaskState.COMPLETED;
            return;
        }

        if (Files.exists(finalDestination, LinkOption.NOFOLLOW_LINKS)) {
            state = TaskState.FAILED;
            throw new FileAlreadyExistsException("Destination already exists: " + finalDestination);
        }

        Files.createDirectories(finalDestination.getParent());

        if (Files.isDirectory(sourceItem)) {
            moveDirectory(runtime);
        } else {
            moveFile(runtime, sourceItem, finalDestination);
        }

        state = TaskState.COMPLETED;
    }

    private void moveDirectory(TaskRuntime runtime) throws Exception {
        Files.createDirectories(finalDestination);

        EnumSet<FileVisitOption> opts = skipShortcuts
                ? EnumSet.noneOf(FileVisitOption.class)
                : EnumSet.of(FileVisitOption.FOLLOW_LINKS);

        Files.walkFileTree(sourceItem, opts, Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        try {
                            runtime.awaitIfPaused();
                            runtime.throwIfCancelled();

                            if (skipShortcuts && isLinkDirectory(dir, attrs)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            Path rel = sourceItem.relativize(dir);
                            Files.createDirectories(finalDestination.resolve(rel));
                            return FileVisitResult.CONTINUE;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted", e);
                        } catch (TaskRuntime.TaskCancelledException e) {
                            throw new IOException("Cancelled", e);
                        }
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            runtime.awaitIfPaused();
                            runtime.throwIfCancelled();

                            if (skipShortcuts && shouldSkipFile(file, attrs)) {
                                return FileVisitResult.CONTINUE;
                            }

                            Path rel = sourceItem.relativize(file);
                            Path dst = finalDestination.resolve(rel);
                            moveFile(runtime, file, dst);
                            return FileVisitResult.CONTINUE;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted", e);
                        } catch (TaskRuntime.TaskCancelledException e) {
                            throw new IOException("Cancelled", e);
                        } catch (Exception e) {
                            if (e instanceof IOException io) throw io;
                            throw new IOException(e);
                        }
                    }
                });

        deleteTree(sourceItem);
    }

    // Same-volume: atomic rename. Cross-volume: copy-then-delete via Files.move without REPLACE_EXISTING.
    private static void moveFile(TaskRuntime runtime, Path src, Path dst) throws Exception {
        runtime.awaitIfPaused();
        runtime.throwIfCancelled();
        Files.createDirectories(dst.getParent());
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dst);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void onCancel() {
        state = TaskState.CANCELLED;
    }

    private static boolean shouldSkipLevel0(Path p) {
        try { if (Files.isSymbolicLink(p)) return true; } catch (Exception ignored) {}
        return isShortcutName(p);
    }

    private static boolean shouldSkipFile(Path file, BasicFileAttributes attrs) {
        if (attrs != null && attrs.isSymbolicLink()) return true;
        try { if (Files.isSymbolicLink(file)) return true; } catch (Exception ignored) {}
        return isShortcutName(file);
    }

    private static boolean isLinkDirectory(Path dir, BasicFileAttributes attrs) {
        if (attrs != null && attrs.isSymbolicLink()) return true;
        try { return Files.isSymbolicLink(dir); } catch (Exception ignored) { return false; }
    }

    private static boolean isShortcutName(Path p) {
        if (p == null || p.getFileName() == null) return false;
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".lnk") || name.endsWith(".url");
    }
}
