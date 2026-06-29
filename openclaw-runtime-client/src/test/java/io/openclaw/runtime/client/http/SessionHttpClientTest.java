package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionHttpClientTest {

    @Mock
    private OpenClawWebSocketClient wsClient;

    private SessionHttpClient sessionHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessionHttpClient = new SessionHttpClient(null, wsClient, objectMapper);
    }

    @Test
    void createSession_shouldInvokeSessionsCreateRpc() {
        ObjectNode expectedResponse = objectMapper.createObjectNode();
        expectedResponse.put("sessionId", "sess-123");
        when(wsClient.invoke(eq("sessions.create"), any()))
                .thenReturn(Mono.just(expectedResponse));

        JsonNode result = sessionHttpClient.createSession("ws-1");

        assertNotNull(result);
        assertEquals("sess-123", result.get("sessionId").asText());
        verify(wsClient).invoke(eq("sessions.create"), any());
    }

    @Test
    void getSession_shouldInvokeSessionsGetRpc() {
        ObjectNode expectedResponse = objectMapper.createObjectNode();
        expectedResponse.put("key", "sess-123");
        when(wsClient.invoke(eq("sessions.get"), eq(Map.of("key", "sess-123"))))
                .thenReturn(Mono.just(expectedResponse));

        JsonNode result = sessionHttpClient.getSession("sess-123");

        assertNotNull(result);
        assertEquals("sess-123", result.get("key").asText());
    }

    @Test
    void closeSession_shouldInvokeSessionsDeleteRpc() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("deleted", true);
        when(wsClient.invoke(eq("sessions.delete"), eq(Map.of("key", "sess-123"))))
                .thenReturn(Mono.just(response));

        assertDoesNotThrow(() -> sessionHttpClient.closeSession("sess-123"));
        verify(wsClient).invoke(eq("sessions.delete"), eq(Map.of("key", "sess-123")));
    }

    @Test
    void heartbeat_shouldInvokeSessionsDescribeRpc() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "active");
        when(wsClient.invoke(eq("sessions.describe"), eq(Map.of("key", "sess-123"))))
                .thenReturn(Mono.just(response));

        assertDoesNotThrow(() -> sessionHttpClient.heartbeat("sess-123"));
        verify(wsClient).invoke(eq("sessions.describe"), eq(Map.of("key", "sess-123")));
    }

    @Test
    void constructor_withoutWsClient_shouldThrowOnMethodCall() {
        SessionHttpClient client = new SessionHttpClient(null);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.createSession("ws-1"));
        assertEquals(ErrorCode.WEBSOCKET_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("WebSocket client not configured"));
    }

    @Test
    void createSession_shouldPropagateRpcError() {
        when(wsClient.invoke(eq("sessions.create"), any()))
                .thenReturn(Mono.error(new ClientException(ErrorCode.SESSION_CREATE_FAILED,
                        "workspace not found")));

        ClientException ex = assertThrows(ClientException.class,
                () -> sessionHttpClient.createSession("invalid-ws"));
        assertEquals(ErrorCode.SESSION_CREATE_FAILED, ex.getErrorCode());
    }

    @Test
    void getSession_shouldMapErrorToSessionNotFound() {
        when(wsClient.invoke(eq("sessions.get"), any()))
                .thenReturn(Mono.error(new RuntimeException("connection lost")));

        ClientException ex = assertThrows(ClientException.class,
                () -> sessionHttpClient.getSession("missing"));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
    }
}
