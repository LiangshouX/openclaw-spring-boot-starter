package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

/** 制品 HTTP 客户端，用于与 OpenClaw Gateway 进行制品相关操作。 */
public class ArtifactClient {

    private final WebClient webClient;

    public ArtifactClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 获取指定制品的元数据。
     *
     * @param artifactId 制品的唯一标识符
     * @return 制品元数据的 JSON 表示
     */
    public JsonNode getArtifact(String artifactId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 列出指定会话关联的所有制品。
     *
     * @param sessionId 要查询的会话标识符
     * @return 制品元数据 JSON 表示的列表
     */
    public List<JsonNode> listArtifacts(String sessionId) {
        return Collections.emptyList();
    }

    /**
     * 下载制品的原始内容。
     *
     * @param artifactId 要下载的制品的唯一标识符
     * @return 制品的原始字节内容
     */
    public byte[] downloadArtifact(String artifactId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
