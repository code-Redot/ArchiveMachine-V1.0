/*
 * Purpose: Transfers a level-0 item (file or directory) into its resolved destination.
 * Applies skip-shortcuts policy and reports cancellation/pause via TaskRuntime.
 */
package com.Redot.core.tasks;

import com.Redot.core.task.CoreTask;
import com.Redot.core.task.TaskState;
import com.Redot.core.task.runtime.TaskRuntime;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.UUID;

public final class TransferMoveTask implements CoreTask {

    // region Transfer rules (skip shortcuts, follow links policy)
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

    // region File vs directory move logic
    @Override
    public void execute(TaskRuntime runtime) throws Exception {
        state = TaskState.RUNNING;
        runtime.throwIfCancelled();

        if (skipShortcuts && shouldSkipLevel0(sourceItem)) {
            state = TaskState.COMPLETED;
            return;
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

        // When skipping shortcuts, do not follow links to avoid traversing junctions/reparse points.
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

    // region Error handling + overwrite rules
    private static void moveFile(TaskRuntime runtime, Path src, Path dst) throws Exception {
        runtime.awaitIfPaused();
        runtime.throwIfCancelled();
        Files.createDirectories(dst.getParent());
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
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

    // region Progress reporting
    // Progress is reported at task granularity; TaskManager advances between queued tasks.
    @Override
    public void onCancel() {
        state = TaskState.CANCELLED;
    }

    private static boolean shouldSkipLevel0(Path p) {
        try {
            if (Files.isSymbolicLink(p)) return true;
        } catch (Exception ignored) { }
        return isShortcutName(p);
    }

    private static boolean shouldSkipFile(Path file, BasicFileAttributes attrs) {
        if (attrs != null && attrs.isSymbolicLink()) return true;
        try {
            if (Files.isSymbolicLink(file)) return true;
        } catch (Exception ignored) { }
        return isShortcutName(file);
    }

    private static boolean isLinkDirectory(Path dir, BasicFileAttributes attrs) {
        if (attrs != null && attrs.isSymbolicLink()) return true;
        try {
            return Files.isSymbolicLink(dir);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isShortcutName(Path p) {
        if (p == null || p.getFileName() == null) return false;
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".lnk") || name.endsWith(".url");
    }
}
