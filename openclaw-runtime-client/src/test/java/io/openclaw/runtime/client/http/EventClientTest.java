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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventClientTest {

    @Mock
    private OpenClawWebSocketClient wsClient;

    private EventClient eventClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        eventClient = new EventClient(null, wsClient, objectMapper);
    }

    @Test
    void publishEvent_shouldInvokeSystemEventRpc() {
        ObjectNode eventPayload = objectMapper.createObjectNode();
        eventPayload.put("type", "custom_event");
        eventPayload.put("data", "test");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("ok", true);
        when(wsClient.invoke(eq("system-event"), any()))
                .thenReturn(Mono.just(response));

        assertDoesNotThrow(() -> eventClient.publishEvent("sess-123", eventPayload));
        verify(wsClient).invoke(eq("system-event"), any());
    }

    @Test
    void getEvents_shouldParseChatHistoryResponse() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode messages = payload.putArray("messages");
        ObjectNode msg1 = messages.addObject();
        msg1.put("role", "user");
        msg1.put("content", "hello");
        ObjectNode msg2 = messages.addObject();
        msg2.put("role", "assistant");
        msg2.put("content", "hi there");

        when(wsClient.invoke(eq("chat.history"), any()))
                .thenReturn(Mono.just(payload));

        List<JsonNode> result = eventClient.getEvents("sess-123");

        assertEquals(2, result.size());
        assertEquals("user", result.get(0).get("role").asText());
        assertEquals("assistant", result.get(1).get("role").asText());
    }

    @Test
    void getEvents_shouldReturnEmptyForNullResponse() {
        when(wsClient.invoke(eq("chat.history"), any()))
                .thenReturn(Mono.empty());

        List<JsonNode> result = eventClient.getEvents("sess-123");

        assertTrue(result.isEmpty());
    }

    @Test
    void getEvents_shouldHandleHistoryKey() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode history = payload.putArray("history");
        history.addObject().put("id", "evt-1");

        when(wsClient.invoke(eq("chat.history"), any()))
                .thenReturn(Mono.just(payload));

        List<JsonNode> result = eventClient.getEvents("sess-123");

        assertEquals(1, result.size());
        assertEquals("evt-1", result.get(0).get("id").asText());
    }

    @Test
    void publishEvent_withoutWsClient_shouldThrow() {
        EventClient client = new EventClient(null);
        ObjectNode event = objectMapper.createObjectNode();

        ClientException ex = assertThrows(ClientException.class,
                () -> client.publishEvent("sess-123", event));
        assertEquals(ErrorCode.WEBSOCKET_ERROR, ex.getErrorCode());
    }
}
