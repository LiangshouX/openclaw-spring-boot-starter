package io.openclaw.runtime.api.listener;

import io.openclaw.runtime.api.event.*;

/**
 * 用于接收 OpenClaw Runtime 生命周期事件的监听器接口。
 * 所有方法均提供默认空实现，允许选择性覆盖。
 */
public interface RuntimeListener {

    /** 运行时启动时调用。 */
    default void onRuntimeStarted(RuntimeStartedEvent event) {}

    /** 运行时停止时调用。 */
    default void onRuntimeStopped(RuntimeStoppedEvent event) {}

    /** 新会话创建时调用。 */
    default void onSessionCreated(SessionCreatedEvent event) {}

    /** 会话关闭时调用。 */
    default void onSessionClosed(SessionClosedEvent event) {}

    /** 任务开始执行时调用。 */
    default void onTaskStarted(TaskStartedEvent event) {}

    /** 任务成功完成时调用。 */
    default void onTaskFinished(TaskFinishedEvent event) {}

    /** 任务执行失败时调用。 */
    default void onTaskFailed(TaskFailedEvent event) {}

    /** 工具调用发起时调用。 */
    default void onToolCalling(ToolCallingEvent event) {}

    /** 工具调用完成时调用。 */
    default void onToolFinished(ToolFinishedEvent event) {}

    /** 新制品创建时调用。 */
    default void onArtifactCreated(ArtifactCreatedEvent event) {}

    /** 产生推理内容时调用。 */
    default void onReasoning(ReasoningEvent event) {}

    /** 流式响应的每个数据块到达时调用。 */
    default void onStreaming(StreamingEvent event) {}

    /** 发生错误时调用。 */
    default void onError(ErrorEvent event) {}
}
