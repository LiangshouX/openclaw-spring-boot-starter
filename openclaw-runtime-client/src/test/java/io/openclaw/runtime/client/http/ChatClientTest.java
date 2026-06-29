package io.openclaw.runtime.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openclaw.runtime.api.dto.ChatRequest;
import io.openclaw.runtime.api.dto.ChatResponse;
import io.openclaw.runtime.api.dto.StreamMode;
import io.openclaw.runtime.api.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatClientTest {

    private DisposableServer server;
    private ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    private DisposableServer createMockServer(String responseBody) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/v1/chat/completions", (req, res) ->
                        res.header("Content-Type", "application/json")
                                .sendString(reactor.core.publisher.Mono.just(responseBody))))
                .bindNow();
    }

    private DisposableServer createMockStreamingServer() {
        String sseData = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"World\"}}]}\n\n"
                + "data: [DONE]\n\n";

        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/v1/chat/completions", (req, res) ->
                        res.header("Content-Type", "text/event-stream")
                                .sendString(reactor.core.publisher.Mono.just(sseData))))
                .bindNow();
    }

    private DisposableServer createMockErrorServer() {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/v1/chat/completions", (req, res) ->
                        res.status(404)
                                .header("Content-Type", "application/json")
                                .sendString(reactor.core.publisher.Mono.just(
                                        "{\"error\":{\"message\":\"Endpoint not found\"}}"))))
                .bindNow();
    }

    @Test
    void sendMessage_shouldReturnChatResponse() {
        String responseJson = "{\"id\":\"chatcmpl-123\",\"model\":\"openclaw/default\","
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello World\"},"
                + "\"finish_reason\":\"stop\"}]}";
        server = createMockServer(responseJson);

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        chatClient = new ChatClient(webClient, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-123")
                .message("Hello")
                .mode(StreamMode.SYNC)
                .build();

        ChatResponse response = chatClient.sendMessage(request);

        assertNotNull(response);
        assertEquals("Hello World", response.getContent());
        assertEquals("sess-123", response.getSessionId());
        assertEquals("chatcmpl-123", response.getRequestId());
    }

    @Test
    void sendMessage_withToolCalls_shouldParseToolCalls() {
        String responseJson = "{\"id\":\"chatcmpl-456\",\"model\":\"openclaw/default\","
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Let me check\","
                + "\"tool_calls\":[{\"id\":\"tc-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"q\\\":\\\"test\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}]}";
        server = createMockServer(responseJson);

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        chatClient = new ChatClient(webClient, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-tool")
                .message("Search for test")
                .mode(StreamMode.SYNC)
                .build();

        ChatResponse response = chatClient.sendMessage(request);

        assertNotNull(response);
        assertEquals(1, response.getToolCalls().size());
        assertEquals("search", response.getToolCalls().get(0).getSkillName());
        assertEquals("tc-1", response.getToolCalls().get(0).getId());
        assertEquals("tool_calls", response.getMetadata().get("finish_reason"));
    }

    @Test
    void streamMessage_shouldEmitDeltas() {
        server = createMockStreamingServer();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        chatClient = new ChatClient(webClient, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-stream")
                .message("Stream me")
                .mode(StreamMode.STREAM)
                .build();

        List<String> deltas = chatClient.streamMessage(request)
                .collectList()
                .block();

        assertNotNull(deltas);
        assertEquals(2, deltas.size());
        assertEquals("Hello ", deltas.get(0));
        assertEquals("World", deltas.get(1));
    }

    @Test
    void sendMessage_onHttpError_shouldThrowClientException() {
        server = createMockErrorServer();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build();
        chatClient = new ChatClient(webClient, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-err")
                .message("Hello")
                .mode(StreamMode.SYNC)
                .build();

        ClientException ex = assertThrows(ClientException.class,
                () -> chatClient.sendMessage(request));
        assertTrue(ex.getMessage().contains("chatCompletions.enabled"));
    }

    @Test
    void sendMessage_withoutWebClient_shouldThrow() {
        chatClient = new ChatClient(null, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-123")
                .message("Hello")
                .mode(StreamMode.SYNC)
                .build();

        ClientException ex = assertThrows(ClientException.class,
                () -> chatClient.sendMessage(request));
        assertTrue(ex.getMessage().contains("WebClient not configured"));
    }

    @Test
    void streamMessage_withoutWebClient_shouldEmitError() {
        chatClient = new ChatClient(null, objectMapper);

        ChatRequest request = ChatRequest.builder()
                .sessionId("sess-123")
                .message("Hello")
                .mode(StreamMode.STREAM)
                .build();

        StepVerifier.create(chatClient.streamMessage(request))
                .expectError(ClientException.class)
                .verify();
    }
}
