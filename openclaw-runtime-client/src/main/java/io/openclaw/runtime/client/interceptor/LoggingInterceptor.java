package io.openclaw.runtime.client.interceptor;

import io.openclaw.runtime.client.dto.OpenClawRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 日志拦截器，以 DEBUG 级别记录出站 HTTP 请求详情。 */
public class LoggingInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public OpenClawRequest intercept(OpenClawRequest request) {
        log.debug("OpenClaw request: method={}, path={}", request.getMethod(), request.getPath());
        return request;
    }
}
