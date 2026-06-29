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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskClientTest {

    @Mock
    private OpenClawWebSocketClient wsClient;

    private TaskClient taskClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        taskClient = new TaskClient(null, wsClient, objectMapper);
    }

    @Test
    void getTask_shouldInvokeTasksGetRpc() {
        ObjectNode taskPayload = objectMapper.createObjectNode();
        ObjectNode task = taskPayload.putObject("task");
        task.put("id", "task-123");
        task.put("status", "running");
        when(wsClient.invoke(eq("tasks.get"), eq(Map.of("taskId", "task-123"))))
                .thenReturn(Mono.just(taskPayload));

        JsonNode result = taskClient.getTask("task-123");

        assertNotNull(result);
        assertEquals("task-123", result.path("task").path("id").asText());
    }

    @Test
    void listTasks_shouldParseTasksArray() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tasks = payload.putArray("tasks");
        ObjectNode task1 = tasks.addObject();
        task1.put("id", "task-1");
        task1.put("status", "running");
        ObjectNode task2 = tasks.addObject();
        task2.put("id", "task-2");
        task2.put("status", "completed");

        when(wsClient.invoke(eq("tasks.list"), any()))
                .thenReturn(Mono.just(payload));

        List<JsonNode> result = taskClient.listTasks("sess-123");

        assertEquals(2, result.size());
        assertEquals("task-1", result.get(0).get("id").asText());
        assertEquals("task-2", result.get(1).get("id").asText());
    }

    @Test
    void listTasks_shouldReturnEmptyForNullResponse() {
        when(wsClient.invoke(eq("tasks.list"), any()))
                .thenReturn(Mono.empty());

        List<JsonNode> result = taskClient.listTasks("sess-123");

        assertTrue(result.isEmpty());
    }

    @Test
    void listTasks_shouldHandleDirectArrayResponse() {
        ArrayNode directArray = objectMapper.createArrayNode();
        ObjectNode task = directArray.addObject();
        task.put("id", "task-direct");

        when(wsClient.invoke(eq("tasks.list"), any()))
                .thenReturn(Mono.just(directArray));

        List<JsonNode> result = taskClient.listTasks("sess-123");

        assertEquals(1, result.size());
        assertEquals("task-direct", result.get(0).get("id").asText());
    }

    @Test
    void cancelTask_shouldInvokeTasksCancelRpc() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("found", true);
        response.put("cancelled", true);
        when(wsClient.invoke(eq("tasks.cancel"), eq(Map.of("taskId", "task-456"))))
                .thenReturn(Mono.just(response));

        assertDoesNotThrow(() -> taskClient.cancelTask("task-456"));
        verify(wsClient).invoke(eq("tasks.cancel"), eq(Map.of("taskId", "task-456")));
    }

    @Test
    void getTask_withoutWsClient_shouldThrow() {
        TaskClient client = new TaskClient(null);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.getTask("task-123"));
        assertEquals(ErrorCode.WEBSOCKET_ERROR, ex.getErrorCode());
    }
}
