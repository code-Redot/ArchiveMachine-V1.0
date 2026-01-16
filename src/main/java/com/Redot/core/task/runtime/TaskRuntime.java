package com.Redot.core.task.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class TaskRuntime {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();
    private volatile boolean paused = false;

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void requestCancel() {
        cancelled.set(true);
        resume();
    }

    public void pause() {
        pauseLock.lock();
        try {
            paused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            paused = false;
            unpaused.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    public void awaitIfPaused() throws InterruptedException {
        if (isCancelled()) return;
        pauseLock.lock();
        try {
            while (paused && !isCancelled()) {
                unpaused.await();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    public void throwIfCancelled() {
        if (isCancelled()) throw new TaskCancelledException();
    }

    public static final class TaskCancelledException extends RuntimeException {
        public TaskCancelledException() { super("Task cancelled"); }
    }
}
