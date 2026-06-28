package io.openclaw.runtime.client.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/** 表示来自 OpenClaw Gateway 的原始 HTTP 响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenClawResponse {

    private String requestId;
    private int statusCode;
    private Map<String, String> headers;
    private JsonNode body;
    private Instant timestamp;
    private boolean success;
}
