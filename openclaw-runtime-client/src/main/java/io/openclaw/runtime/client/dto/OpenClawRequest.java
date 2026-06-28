package io.openclaw.runtime.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/** 表示发往 OpenClaw Gateway 的原始 HTTP 请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenClawRequest {

    private String requestId;
    private String method;
    private String path;
    private Map<String, String> headers;
    private Object body;
    private Instant timestamp;
}
