package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class UploadClientTest {

    private DisposableServer server;
    private UploadClient uploadClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/v1/files", (req, res) -> {
                    String contentType = req.requestHeaders().get("Content-Type");
                    if (contentType != null && contentType.contains("multipart")) {
                        return res.header("Content-Type", "application/json")
                                .sendString(Flux.just("{\"file_id\":\"file-123\",\"status\":\"uploaded\"}"))
                                .then();
                    }
                    return res.status(400)
                            .sendString(Flux.just("{\"error\":\"expected multipart\"}"))
                            .then();
                }))
                .bindNow();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        uploadClient = new UploadClient(webClient);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.disposeNow();
    }

    @Test
    void upload_shouldSendMultipartAndParseResponse() {
        byte[] content = "Hello, file!".getBytes();

        JsonNode result = uploadClient.upload("sess-123", "test.txt",
                content, "text/plain");

        assertNotNull(result);
        assertEquals("file-123", result.get("file_id").asText());
        assertEquals("uploaded", result.get("status").asText());
    }

    @Test
    void uploadMultipart_shouldSendResourceAndParseResponse() {
        ByteArrayResource resource = new ByteArrayResource("resource content".getBytes()) {
            @Override
            public String getFilename() {
                return "resource.txt";
            }
        };

        JsonNode result = uploadClient.uploadMultipart("sess-456", resource);

        assertNotNull(result);
        assertEquals("file-123", result.get("file_id").asText());
    }

    @Test
    void upload_shouldThrowClientExceptionOnServerError() {
        DisposableServer errorServer = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/v1/files", (req, res) ->
                        res.status(500)
                                .sendString(Flux.just("{\"error\":\"disk full\"}"))
                                .then()))
                .bindNow();

        try {
            WebClient errorWebClient = WebClient.builder()
                    .baseUrl("http://localhost:" + errorServer.port())
                    .build();
            UploadClient errorClient = new UploadClient(errorWebClient);

            ClientException ex = assertThrows(ClientException.class,
                    () -> errorClient.upload("sess-err", "fail.bin",
                            new byte[]{1, 2, 3}, "application/octet-stream"));
            assertTrue(ex.getMessage().contains("File upload failed"));
        } finally {
            errorServer.disposeNow();
        }
    }
}
