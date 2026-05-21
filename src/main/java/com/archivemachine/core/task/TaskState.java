package com.archivemachine.core.task;

public enum TaskState {
    QUEUED,
    RUNNING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED
}
