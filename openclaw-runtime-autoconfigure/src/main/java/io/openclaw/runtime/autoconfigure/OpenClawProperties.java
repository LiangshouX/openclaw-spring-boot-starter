package io.openclaw.runtime.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenClaw Runtime SDK 配置属性。
 */
@Data
@ConfigurationProperties(prefix = "openclaw")
public class OpenClawProperties {

    /**
     * OpenClaw Gateway 端点 URL。
     */
    private String endpoint;

    /**
     * OpenClaw API 认证令牌。
     */
    private String token;

    /**
     * 默认工作空间 ID。
     */
    private String workspace;

    /**
     * 回调配置。
     */
    private CallbackProperties callback = new CallbackProperties();

    /**
     * 请求超时配置。
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * 重试配置。
     */
    private RetryProperties retry = new RetryProperties();

    /**
     * 心跳配置。
     */
    private HeartbeatProperties heartbeat = new HeartbeatProperties();

    /**
     * 流式传输配置。
     */
    private StreamProperties stream = new StreamProperties();

    /**
     * 是否在启动时自动注册技能。
     */
    private boolean autoRegisterSkill = true;

    /**
     * 链路追踪配置。
     */
    private TraceProperties trace = new TraceProperties();

    /**
     * 是否记录 HTTP 请求日志。
     */
    private boolean logRequest = false;

    /**
     * 是否记录 HTTP 响应日志。
     */
    private boolean logResponse = false;

    @Data
    public static class CallbackProperties {
        /**
         * 回调端点路径。
         */
        private String path = "/openclaw/callback";

        /**
         * 是否启用回调端点。
         */
        private boolean enabled = true;
    }

    @Data
    public static class RetryProperties {
        /**
         * 最大重试次数。
         */
        private int maxAttempts = 3;

        /**
         * 重试间隔时间。
         */
        private Duration backoff = Duration.ofSeconds(1);
    }

    @Data
    public static class HeartbeatProperties {
        /**
         * 是否启用心跳。
         */
        private boolean enabled = true;

        /**
         * 心跳间隔。
         */
        private Duration interval = Duration.ofSeconds(30);
    }

    @Data
    public static class StreamProperties {
        /**
         * 是否启用流式传输。
         */
        private boolean enabled = true;

        /**
         * 流式连接重连延迟。
         */
        private Duration reconnectDelay = Duration.ofSeconds(5);
    }

    @Data
    public static class TraceProperties {
        /**
         * 是否启用分布式链路追踪。
         */
        private boolean enabled = false;

        /**
         * 追踪数据导出器类型。
         */
        private String exporter = "otel";
    }
}
