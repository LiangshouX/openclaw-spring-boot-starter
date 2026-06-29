package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactClientTest {

    @Mock
    private OpenClawWebSocketClient wsClient;

    private ArtifactClient artifactClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        artifactClient = new ArtifactClient(null, wsClient, objectMapper);
    }

    @Test
    void getArtifact_shouldInvokeArtifactsGetRpc() {
        ObjectNode artifact = objectMapper.createObjectNode();
        artifact.put("artifactId", "art-123");
        artifact.put("type", "image");
        when(wsClient.invoke(eq("artifacts.get"), eq(Map.of("artifactId", "art-123"))))
                .thenReturn(Mono.just(artifact));

        JsonNode result = artifactClient.getArtifact("art-123");

        assertNotNull(result);
        assertEquals("art-123", result.get("artifactId").asText());
    }

    @Test
    void listArtifacts_shouldParseArtifactsArray() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode artifacts = payload.putArray("artifacts");
        artifacts.addObject().put("artifactId", "art-1");
        artifacts.addObject().put("artifactId", "art-2");

        when(wsClient.invoke(eq("artifacts.list"), any()))
                .thenReturn(Mono.just(payload));

        List<JsonNode> result = artifactClient.listArtifacts("sess-123");

        assertEquals(2, result.size());
    }

    @Test
    void listArtifacts_shouldReturnEmptyForNullResponse() {
        when(wsClient.invoke(eq("artifacts.list"), any()))
                .thenReturn(Mono.empty());

        List<JsonNode> result = artifactClient.listArtifacts("sess-123");

        assertTrue(result.isEmpty());
    }

    @Test
    void downloadArtifact_shouldDecodeBase64Content() {
        byte[] originalContent = "Hello, World!".getBytes();
        String base64 = Base64.getEncoder().encodeToString(originalContent);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("content", base64);
        when(wsClient.invoke(eq("artifacts.download"), eq(Map.of("artifactId", "art-dl"))))
                .thenReturn(Mono.just(response));

        byte[] result = artifactClient.downloadArtifact("art-dl");

        assertArrayEquals(originalContent, result);
    }

    @Test
    void downloadArtifact_shouldFallbackToDataKey() {
        byte[] originalContent = "Test data".getBytes();
        String base64 = Base64.getEncoder().encodeToString(originalContent);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("data", base64);
        when(wsClient.invoke(eq("artifacts.download"), any()))
                .thenReturn(Mono.just(response));

        byte[] result = artifactClient.downloadArtifact("art-dl");

        assertArrayEquals(originalContent, result);
    }

    @Test
    void downloadArtifact_shouldThrowForNullResponse() {
        when(wsClient.invoke(eq("artifacts.download"), any()))
                .thenReturn(Mono.empty());

        ClientException ex = assertThrows(ClientException.class,
                () -> artifactClient.downloadArtifact("art-null"));
        assertTrue(ex.getMessage().contains("Empty response"));
    }

    @Test
    void downloadArtifact_withoutWsClient_shouldThrow() {
        ArtifactClient client = new ArtifactClient(null);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.downloadArtifact("art-123"));
        assertEquals(ErrorCode.WEBSOCKET_ERROR, ex.getErrorCode());
    }
}
