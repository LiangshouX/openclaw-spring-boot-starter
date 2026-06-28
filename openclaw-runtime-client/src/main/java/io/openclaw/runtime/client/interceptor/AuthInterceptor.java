package io.openclaw.runtime.client.interceptor;

import io.openclaw.runtime.client.dto.OpenClawRequest;

import java.util.HashMap;
import java.util.Map;

/** 认证拦截器，为出站请求添加 Bearer 令牌认证。 */
public class AuthInterceptor implements RequestInterceptor {

    private final String token;

    public AuthInterceptor(String token) {
        this.token = token;
    }

    @Override
    public OpenClawRequest intercept(OpenClawRequest request) {
        Map<String, String> headers = request.getHeaders() != null
                ? new HashMap<>(request.getHeaders())
                : new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        return OpenClawRequest.builder()
                .requestId(request.getRequestId())
                .method(request.getMethod())
                .path(request.getPath())
                .headers(headers)
                .body(request.getBody())
                .timestamp(request.getTimestamp())
                .build();
    }
}
