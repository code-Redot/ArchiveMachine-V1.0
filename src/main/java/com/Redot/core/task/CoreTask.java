package com.Redot.core.task;

import com.Redot.core.task.runtime.TaskRuntime;

public interface CoreTask {
    String id();
    String description();
    TaskState state();
    void execute(TaskRuntime runtime) throws Exception;
    void onCancel();
}
