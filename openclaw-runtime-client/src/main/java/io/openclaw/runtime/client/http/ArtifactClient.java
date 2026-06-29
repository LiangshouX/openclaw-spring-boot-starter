package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制品 HTTP 客户端，通过 WebSocket RPC 与 OpenClaw Gateway 进行制品操作。
 * <p>
 * 使用 WebSocket RPC 方法：{@code artifacts.get}、{@code artifacts.list}、{@code artifacts.download}。
 */
public class ArtifactClient {

    private static final Logger log = LoggerFactory.getLogger(ArtifactClient.class);

    private final WebClient webClient;
    private final OpenClawWebSocketClient wsClient;
    private final ObjectMapper objectMapper;

    public ArtifactClient(WebClient webClient) {
        this(webClient, null, new ObjectMapper());
    }

    public ArtifactClient(WebClient webClient, OpenClawWebSocketClient wsClient) {
        this(webClient, wsClient, new ObjectMapper());
    }

    public ArtifactClient(WebClient webClient, OpenClawWebSocketClient wsClient,
                          ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
    }

    private void requireWsClient() {
        if (wsClient == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "WebSocket client not configured for ArtifactClient");
        }
    }

    /**
     * 获取指定制品的元数据。
     *
     * @param artifactId 制品的唯一标识符
     * @return 制品元数据的 JSON 表示
     * @throws ClientException 当查询失败时
     */
    public JsonNode getArtifact(String artifactId) {
        requireWsClient();
        log.debug("Getting artifact: {}", artifactId);
        return wsClient.invoke("artifacts.get", Map.of("artifactId", artifactId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to get artifact: " + artifactId, e);
                })
                .block();
    }

    /**
     * 列出指定会话关联的所有制品。
     *
     * @param sessionId 要查询的会话标识符
     * @return 制品元数据 JSON 表示的列表
     * @throws ClientException 当查询失败时
     */
    public List<JsonNode> listArtifacts(String sessionId) {
        requireWsClient();
        log.debug("Listing artifacts for session: {}", sessionId);

        Map<String, Object> params = new LinkedHashMap<>();
        if (sessionId != null) {
            params.put("sessionKey", sessionId);
        }

        JsonNode result = wsClient.invoke("artifacts.list", params)
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to list artifacts for session: " + sessionId, e);
                })
                .block();

        if (result == null) {
            return Collections.emptyList();
        }

        // Response may contain { artifacts: [...] } or be a direct array
        JsonNode artifactsArray = result.path("artifacts");
        if (artifactsArray.isMissingNode() || !artifactsArray.isArray()) {
            if (result.isArray()) {
                artifactsArray = result;
            } else {
                return Collections.emptyList();
            }
        }

        List<JsonNode> artifacts = new ArrayList<>();
        artifactsArray.forEach(artifacts::add);
        return artifacts;
    }

    /**
     * 下载制品的原始内容。
     *
     * @param artifactId 要下载的制品的唯一标识符
     * @return 制品的原始字节内容
     * @throws ClientException 当下载失败时
     */
    public byte[] downloadArtifact(String artifactId) {
        requireWsClient();
        log.debug("Downloading artifact: {}", artifactId);

        JsonNode result = wsClient.invoke("artifacts.download", Map.of("artifactId", artifactId))
                .onErrorMap(e -> {
                    if (e instanceof ClientException) return e;
                    return new ClientException(ErrorCode.WEBSOCKET_ERROR,
                            "Failed to download artifact: " + artifactId, e);
                })
                .block();

        if (result == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "Empty response for artifact download: " + artifactId);
        }

        // Response contains { content: "<base64>" } or { data: "<base64>" } or raw bytes
        String base64Content = result.path("content").asText(
                result.path("data").asText(null));

        if (base64Content == null) {
            throw new ClientException(ErrorCode.WEBSOCKET_ERROR,
                    "No content in artifact download response: " + artifactId);
        }

        try {
            return Base64.getDecoder().decode(base64Content);
        } catch (IllegalArgumentException e) {
            // Content might not be base64 encoded — return as raw bytes
            return base64Content.getBytes();
        }
    }
}
