package io.openclaw.runtime.autoconfigure;

import io.openclaw.runtime.event.EventPublisher;
import io.openclaw.runtime.event.callback.CallbackDispatcher;
import io.openclaw.runtime.event.callback.CallbackPayloadParser;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * OpenClaw 回调处理自动配置类。
 * 当 {@code openclaw.callback.enabled} 为 true（默认值）时激活。
 */
@AutoConfiguration(after = OpenClawAutoConfiguration.class)
@ConditionalOnProperty(prefix = "openclaw.callback", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenClawCallbackAutoConfiguration {

    /** 创建 {@link CallbackPayloadParser} Bean，用于将原始回调 JSON 解析为事件。 */
    @Bean
    @ConditionalOnMissingBean
    public CallbackPayloadParser callbackPayloadParser() {
        return new CallbackPayloadParser();
    }

    /** 创建 {@link CallbackDispatcher} Bean，用于将解析后的回调路由到事件发布器。 */
    @Bean
    @ConditionalOnMissingBean
    public CallbackDispatcher callbackDispatcher(EventPublisher eventPublisher,
                                                  CallbackPayloadParser callbackPayloadParser) {
        return new CallbackDispatcher(eventPublisher, callbackPayloadParser);
    }

    /** 创建 {@link OpenClawCallbackController} Bean，用于接收来自 OpenClaw 的 HTTP 回调。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenClawCallbackController openClawCallbackController(CallbackDispatcher callbackDispatcher,
                                                                  OpenClawProperties properties) {
        return new OpenClawCallbackController(callbackDispatcher, properties);
    }
}
