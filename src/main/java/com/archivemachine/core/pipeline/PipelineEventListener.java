package com.archivemachine.core.pipeline;

public interface PipelineEventListener {
    void onPipelineStarted();
    void onPipelineFinishedSuccess();
    void onPipelineFinishedCancelled();
    void onPipelineFinishedFailed(Throwable error);
    void onTaskStarted(String description);
    void onProgress(int completed, int total);
}
