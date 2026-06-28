package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.client.WebClient;

/** 上传 HTTP 客户端，用于与 OpenClaw Gateway 进行文件上传操作。 */
public class UploadClient {

    private final WebClient webClient;

    public UploadClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 将原始字节内容作为文件上传到会话。
     *
     * @param sessionId 目标会话标识符
     * @param fileName 上传文件的名称
     * @param content 文件的原始字节内容
     * @param contentType 文件内容的 MIME 类型
     * @return 上传结果的 JSON 表示
     */
    public JsonNode upload(String sessionId, String fileName, byte[] content, String contentType) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * 将多部分资源上传到会话。
     *
     * @param sessionId 目标会话标识符
     * @param resource 要上传的 Spring 资源
     * @return 上传结果的 JSON 表示
     */
    public JsonNode uploadMultipart(String sessionId, Resource resource) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
