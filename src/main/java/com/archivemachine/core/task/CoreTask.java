package com.archivemachine.core.task;

import com.archivemachine.core.task.runtime.TaskRuntime;

public interface CoreTask {
    String id();
    String description();
    TaskState state();
    void execute(TaskRuntime runtime) throws Exception;
    void onCancel();
}
