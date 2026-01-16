package com.Redot.core.task.events;

public interface PipelineEventListener {

    void onPipelineStarted();

    void onPipelineFinishedSuccess();

    void onPipelineFinishedCancelled();

    void onPipelineFinishedFailed(Throwable error);
}
