/*
 * Purpose: Single-threaded pipeline runner for CoreTask instances.
 * Manages a small state machine (run/pause/cancel/reset) and emits PipelineEventListener callbacks.
 */
package com.Redot.core.task;

import com.Redot.core.task.events.PipelineEventListener;
import com.Redot.core.task.runtime.TaskRuntime;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;

public final class TaskManager {

    // region State machine (IDLE/RUNNING/PAUSED/CANCELLED/RESET_REQUIRED)
    public enum State { IDLE, RUNNING, PAUSED, RESET_REQUIRED }

    // region Task queue + transitions
    private final Queue<CoreTask> queue = new ArrayDeque<>();

    private ExecutorService executor;
    private TaskRuntime runtime;

    private volatile PipelineEventListener listener;
    private volatile boolean cancelling;
    private volatile boolean finishedEmitted;

    private volatile State state = State.IDLE;

    private volatile CoreTask activeTask;
    private volatile Future<?> activeFuture;

    // region Public API (start/pause/resume/cancel/reset)
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
        if (state == State.IDLE) {
            startPipeline();
        }
    }

    private void startPipeline() {
        cancelling = false;
        finishedEmitted = false;
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
        activeFuture = executor.submit(() -> {
            try {
                next.execute(runtime);
                synchronized (TaskManager.this) { scheduleNext(); }
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
        // no need to reschedule; active task will continue, and scheduling continues after completion
    }

    public synchronized void cancel() {
        if (state == State.IDLE) {
            // nothing running; still require reset so UI behavior is deterministic
            cancelling = true;
            finishCancelled();
            return;
        }

        cancelling = true;

        // stop scheduling immediately
        queue.clear();

        // tell runtime to cancel (unblocks pause waits)
        runtime.requestCancel();

        // kill external process etc
        CoreTask t = activeTask;
        if (t != null) {
            try { t.onCancel(); } catch (Throwable ignored) {}
        }

        // interrupt running task
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
        state = State.IDLE;
        freshExecutor();
    }

    // Success returns to IDLE to support immediate re-run without forcing a user-visible reset.
    private void finishSuccess() {
        if (finishedEmitted) return;
        finishedEmitted = true;
        // Success should auto-reset back to an idle state so the user can run again immediately.
        state = State.IDLE;
        activeTask = null;
        activeFuture = null;
        if (listener != null) listener.onPipelineFinishedSuccess();
    }

    // Cancel requires reset to clear partial state and keep subsequent runs deterministic.
    private void finishCancelled() {
        if (finishedEmitted) return;
        finishedEmitted = true;
        state = State.RESET_REQUIRED;
        if (listener != null) listener.onPipelineFinishedCancelled();
    }

    // Failures require reset so the UI can explicitly acknowledge and clear the error state.
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
