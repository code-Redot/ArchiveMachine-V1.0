/*
 * Single-threaded pipeline runner for CoreTask instances.
 * Owns the run/pause/cancel/reset state machine and emits PipelineEventListener callbacks.
 * Adds per-task progress reporting (completed/total) and active-task description signaling.
 */
package com.archivemachine.core.task;

import com.archivemachine.core.pipeline.PipelineEventListener;
import com.archivemachine.core.task.runtime.TaskRuntime;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;

public final class TaskManager {

    public enum State { IDLE, RUNNING, PAUSED, RESET_REQUIRED }

    private final Queue<CoreTask> queue = new ArrayDeque<>();

    private ExecutorService executor;
    private TaskRuntime runtime;

    private volatile PipelineEventListener listener;
    private volatile boolean cancelling;
    private volatile boolean finishedEmitted;

    private volatile State state = State.IDLE;

    private volatile CoreTask activeTask;
    private volatile Future<?> activeFuture;

    private int totalSubmitted;
    private int completed;

    public TaskManager() { freshExecutor(); }

    public synchronized void setListener(PipelineEventListener listener) {
        this.listener = listener;
    }

    public synchronized State getState() { return state; }

    public synchronized void submit(CoreTask task) {
        Objects.requireNonNull(task);
        if (state == State.RESET_REQUIRED) {
            throw new IllegalStateException("reset() required before submitting new tasks");
        }
        queue.add(task);
        totalSubmitted++;
        if (state == State.IDLE) {
            startPipeline();
        }
    }

    // Lets callers (presenter) signal "no items to schedule" so the UI doesn't sit silent.
    public synchronized void noWorkSubmitted() {
        if (state == State.IDLE && totalSubmitted == 0) {
            finishedEmitted = true;
            if (listener != null) listener.onPipelineFinishedSuccess();
        }
    }

    private void startPipeline() {
        cancelling = false;
        finishedEmitted = false;
        completed = 0;
        state = State.RUNNING;
        if (listener != null) listener.onPipelineStarted();
        scheduleNext();
    }

    private synchronized void scheduleNext() {
        if (cancelling || runtime.isCancelled()) {
            finishCancelled();
            return;
        }

        CoreTask next = queue.poll();
        if (next == null) {
            finishSuccess();
            return;
        }

        activeTask = next;
        if (listener != null) listener.onTaskStarted(next.description());
        activeFuture = executor.submit(() -> {
            try {
                next.execute(runtime);
                synchronized (TaskManager.this) {
                    completed++;
                    if (listener != null) listener.onProgress(completed, totalSubmitted);
                    scheduleNext();
                }
            } catch (Throwable t) {
                synchronized (TaskManager.this) {
                    if (isCancellationTriggered(t)) {
                        finishCancelled();
                    } else {
                        finishFailed(t);
                    }
                }
            }
        });
    }

    public synchronized void pause() {
        if (state != State.RUNNING) return;
        state = State.PAUSED;
        runtime.pause();
    }

    public synchronized void resume() {
        if (state != State.PAUSED) return;
        state = State.RUNNING;
        runtime.resume();
    }

    public synchronized void cancel() {
        if (state == State.IDLE) {
            cancelling = true;
            finishCancelled();
            return;
        }

        cancelling = true;
        queue.clear();
        runtime.requestCancel();

        CoreTask t = activeTask;
        if (t != null) {
            try { t.onCancel(); } catch (Throwable ignored) {}
        }

        Future<?> f = activeFuture;
        if (f != null) f.cancel(true);

        finishCancelled();
    }

    public synchronized void reset() {
        shutdownExecutor();
        queue.clear();
        activeTask = null;
        activeFuture = null;
        cancelling = false;
        finishedEmitted = false;
        totalSubmitted = 0;
        completed = 0;
        state = State.IDLE;
        freshExecutor();
    }

    private void finishSuccess() {
        if (finishedEmitted) return;
        finishedEmitted = true;
        state = State.IDLE;
        activeTask = null;
        activeFuture = null;
        totalSubmitted = 0;
        completed = 0;
        if (listener != null) listener.onPipelineFinishedSuccess();
    }

    private void finishCancelled() {
        if (finishedEmitted) return;
        finishedEmitted = true;
        state = State.RESET_REQUIRED;
        if (listener != null) listener.onPipelineFinishedCancelled();
    }

    private void finishFailed(Throwable error) {
        if (finishedEmitted) return;
        finishedEmitted = true;
        state = State.RESET_REQUIRED;
        if (listener != null) listener.onPipelineFinishedFailed(error);
    }

    private boolean isCancellationTriggered(Throwable t) {
        if (cancelling || runtime.isCancelled()) return true;
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof TaskRuntime.TaskCancelledException) return true;
            if (cur instanceof CancellationException) return true;
            if (cur instanceof InterruptedException) return true;
            cur = cur.getCause();
        }
        return false;
    }

    private void freshExecutor() {
        runtime = new TaskRuntime();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TaskManager-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    private void shutdownExecutor() {
        if (executor == null) return;
        executor.shutdownNow();
        try { executor.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
