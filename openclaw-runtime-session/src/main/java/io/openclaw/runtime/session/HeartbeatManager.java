package io.openclaw.runtime.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 心跳管理器，管理活跃会话的周期性心跳调度。 */
public class HeartbeatManager {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);

    private final ConcurrentHashMap<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public HeartbeatManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 为指定会话启动周期性心跳。
     *
     * @param sessionId 会话标识符
     * @param interval 心跳执行间隔
     * @param heartbeatAction 每次心跳时要执行的操作
     */
    public void start(String sessionId, Duration interval, Runnable heartbeatAction) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        heartbeatAction.run();
                    } catch (Exception e) {
                        log.error("Heartbeat failed for session: {}", sessionId, e);
                    }
                },
                0,
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        heartbeats.put(sessionId, future);
        log.info("Heartbeat started for session: {} with interval: {}", sessionId, interval);
    }

    /**
     * 停止指定会话的心跳。
     *
     * @param sessionId 会话标识符
     */
    public void stop(String sessionId) {
        ScheduledFuture<?> future = heartbeats.remove(sessionId);
        if (future != null) {
            future.cancel(false);
            log.info("Heartbeat stopped for session: {}", sessionId);
        }
    }

    /**
     * 检查指定会话的心跳是否正在运行。
     *
     * @param sessionId 会话标识符
     * @return 心跳活跃时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isRunning(String sessionId) {
        ScheduledFuture<?> future = heartbeats.get(sessionId);
        return future != null && !future.isCancelled();
    }

    /**
     * 关闭心跳调度器及所有活跃的心跳任务。
     */
    public void shutdown() {
        log.info("Shutting down heartbeat manager");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
