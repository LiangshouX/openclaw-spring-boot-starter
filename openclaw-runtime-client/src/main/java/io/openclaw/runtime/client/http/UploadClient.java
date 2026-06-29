package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 上传 HTTP 客户端，用于向 OpenClaw Gateway 上传文件。
 * <p>
 * 支持原始字节上传（{@link #upload}）和 Spring {@link Resource} 多部分上传（{@link #uploadMultipart}）。
 */
public class UploadClient {

    private static final Logger log = LoggerFactory.getLogger(UploadClient.class);
    private static final String UPLOAD_PATH = "/v1/files";

    private final WebClient webClient;

    public UploadClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 将原始字节内容作为文件上传到会话。
     *
     * @param sessionId   目标会话标识符
     * @param fileName    上传文件的名称
     * @param content     文件的原始字节内容
     * @param contentType 文件内容的 MIME 类型
     * @return 上传结果的 JSON 表示
     * @throws ClientException 当上传失败时
     */
    public JsonNode upload(String sessionId, String fileName, byte[] content, String contentType) {
        try {
            log.debug("Uploading file '{}' ({} bytes) to session {}", fileName,
                    content.length, sessionId);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            }).contentType(MediaType.parseMediaType(contentType));

            if (sessionId != null) {
                builder.part("session_id", sessionId);
            }

            return webClient.post()
                    .uri(UPLOAD_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .map(errorBody -> new ClientException(ErrorCode.HTTP_ERROR,
                                            "File upload failed [" + response.statusCode() + "]: " + errorBody)))
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(ErrorCode.HTTP_ERROR, "File upload failed", e);
        }
    }

    /**
     * 将 Spring {@link Resource} 作为多部分文件上传到会话。
     *
     * @param sessionId 目标会话标识符
     * @param resource  要上传的 Spring 资源
     * @return 上传结果的 JSON 表示
     * @throws ClientException 当上传失败时
     */
    public JsonNode uploadMultipart(String sessionId, Resource resource) {
        try {
            log.debug("Uploading resource '{}' to session {}", resource.getFilename(), sessionId);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", resource);

            if (sessionId != null) {
                builder.part("session_id", sessionId);
            }

            return webClient.post()
                    .uri(UPLOAD_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .map(errorBody -> new ClientException(ErrorCode.HTTP_ERROR,
                                            "Multipart upload failed [" + response.statusCode() + "]: " + errorBody)))
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(ErrorCode.HTTP_ERROR, "Multipart upload failed", e);
        }
    }
}
