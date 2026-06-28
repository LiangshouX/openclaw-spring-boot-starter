package io.openclaw.runtime.client.interceptor;

import io.openclaw.runtime.client.dto.OpenClawRequest;

/** 请求拦截器接口，用于在发送前修改出站 HTTP 请求。 */
public interface RequestInterceptor {

    /**
     * 拦截并可选择性地修改出站请求。
     *
     * @param request 要拦截的出站请求
     * @return 可能被修改后的请求
     */
    OpenClawRequest intercept(OpenClawRequest request);
}
