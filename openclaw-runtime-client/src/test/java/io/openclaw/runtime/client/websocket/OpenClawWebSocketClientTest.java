package io.openclaw.runtime.client.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.exception.ClientException;
import io.openclaw.runtime.api.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenClawWebSocketClientTest {

    private DisposableServer server;
    private OpenClawWebSocketClient client;

    @AfterEach
    void tearDown() {
        if (client != null && client.isConnected()) {
            client.close();
        }
        if (server != null) {
            server.disposeNow();
        }
    }

    /**
     * Creates a mock WebSocket server that implements the OpenClaw handshake protocol.
     */
    private DisposableServer createMockGateway(String token) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.ws("/", (inbound, outbound) ->
                        handleWebSocket(inbound, outbound, token)))
                .bindNow();
    }

    private Mono<Void> handleWebSocket(WebsocketInbound inbound, WebsocketOutbound outbound,
                                        String expectedToken) {
        ObjectMapper mapper = new ObjectMapper();

        // Send connect.challenge event
        String challenge = "{\"type\":\"event\",\"event\":\"connect.challenge\","
                + "\"payload\":{\"nonce\":\"test-nonce-123\",\"ts\":" + System.currentTimeMillis() + "}}";

        return outbound.sendString(Mono.just(challenge))
                .then()
                .thenMany(inbound.receive().asString())
                .flatMap(text -> {
                    try {
                        JsonNode frame = mapper.readTree(text);
                        String method = frame.path("method").asText("");

                        if ("connect".equals(method)) {
                            // Validate token
                            String authToken = frame.path("params").path("auth")
                                    .path("token").asText("");
                            if (!expectedToken.equals(authToken)) {
                                String errorRes = "{\"type\":\"res\",\"id\":\"" + frame.path("id").asText()
                                        + "\",\"ok\":false,\"error\":{\"message\":\"Invalid token\"}}";
                                return outbound.sendString(Mono.just(errorRes)).then();
                            }

                            // Send hello-ok
                            String helloOk = "{\"type\":\"res\",\"id\":\"" + frame.path("id").asText()
                                    + "\",\"ok\":true,\"payload\":{\"type\":\"hello-ok\","
                                    + "\"protocol\":4,\"server\":{\"version\":\"test-1.0\",\"connId\":\"conn-1\"},"
                                    + "\"policy\":{\"tickIntervalMs\":60000,\"maxPayload\":26214400}}}";
                            return outbound.sendString(Mono.just(helloOk)).then();
                        }

                        if ("tick".equals(method)) {
                            // Ignore tick requests in tests
                            return Mono.empty();
                        }

                        // Echo RPC: return params as payload
                        String id = frame.path("id").asText("");
                        ObjectNode response = mapper.createObjectNode();
                        response.put("type", "res");
                        response.put("id", id);
                        response.put("ok", true);
                        response.set("payload", frame.path("params"));
                        return outbound.sendString(Mono.just(mapper.writeValueAsString(response)))
                                .then();
                    } catch (Exception e) {
                        return Mono.<Void>error(e);
                    }
                })
                .then();
    }

    @Test
    void connect_shouldCompleteHandshake() {
        server = createMockGateway("test-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:" + server.port(),
                "test-token", mapper,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 3);

        client.connect().block(Duration.ofSeconds(10));

        assertTrue(client.isConnected());
    }

    @Test
    void connect_shouldFailWithInvalidToken() {
        server = createMockGateway("correct-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:" + server.port(),
                "wrong-token", mapper,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 3);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.connect().block(Duration.ofSeconds(10)));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, ex.getErrorCode());
        assertFalse(client.isConnected());
    }

    @Test
    void invoke_shouldSendRpcAndReceiveResponse() {
        server = createMockGateway("test-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:" + server.port(),
                "test-token", mapper,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 3);

        client.connect().block(Duration.ofSeconds(10));

        // Invoke RPC — mock server echoes params as payload
        ObjectNode params = mapper.createObjectNode();
        params.put("key", "agent:main:main");

        JsonNode result = client.invoke("sessions.get", params)
                .block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertEquals("agent:main:main", result.path("key").asText());
    }

    @Test
    void invoke_shouldCorrelateMultipleRequests() {
        server = createMockGateway("test-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:" + server.port(),
                "test-token", mapper,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 3);

        client.connect().block(Duration.ofSeconds(10));

        // Send two RPCs and verify they get correct responses
        Mono<JsonNode> rpc1 = client.invoke("tasks.get",
                mapper.createObjectNode().put("taskId", "task-1"));
        Mono<JsonNode> rpc2 = client.invoke("tasks.get",
                mapper.createObjectNode().put("taskId", "task-2"));

        JsonNode result1 = rpc1.block(Duration.ofSeconds(5));
        JsonNode result2 = rpc2.block(Duration.ofSeconds(5));

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals("task-1", result1.path("taskId").asText());
        assertEquals("task-2", result2.path("taskId").asText());
    }

    @Test
    void invoke_whenNotConnected_shouldFail() {
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:99999", "token", mapper);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.invoke("test.method", mapper.createObjectNode())
                        .block(Duration.ofSeconds(5)));
        assertEquals(ErrorCode.WEBSOCKET_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("not connected"));
    }

    @Test
    void connect_withNullEndpoint_shouldFail() {
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(null, "token", mapper);

        ClientException ex = assertThrows(ClientException.class,
                () -> client.connect().block(Duration.ofSeconds(5)));
        assertEquals(ErrorCode.CONNECTION_REFUSED, ex.getErrorCode());
    }

    @Test
    void close_shouldCleanUpResources() {
        server = createMockGateway("test-token");
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient(
                "ws://localhost:" + server.port(),
                "test-token", mapper,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 3);

        client.connect().block(Duration.ofSeconds(10));
        assertTrue(client.isConnected());

        client.close();

        assertFalse(client.isConnected());
    }

    @Test
    void subscribe_shouldRegisterAndUnregisterSubscriber() {
        ObjectMapper mapper = new ObjectMapper();
        client = new OpenClawWebSocketClient("ws://localhost:99999", "token", mapper);

        AtomicReference<String> receivedEvent = new AtomicReference<>();
        OpenClawWebSocketClient.EventSubscriber subscriber = (eventType, payload) ->
                receivedEvent.set(eventType);

        // Subscribe returns a disposable
        Disposable subscription = client.subscribe(subscriber);
        assertNotNull(subscription);

        // Dispose should remove the subscriber
        subscription.dispose();
    }

    @Test
    void deprecatedConstructor_shouldCreateInstance() {
        @SuppressWarnings("deprecation")
        OpenClawWebSocketClient legacyClient = new OpenClawWebSocketClient();
        assertFalse(legacyClient.isConnected());
    }

    @Test
    void chatStreamChunk_shouldBuildCorrectly() {
        OpenClawWebSocketClient.ChatStreamChunk chunk =
                OpenClawWebSocketClient.ChatStreamChunk.builder()
                        .delta("Hello")
                        .type("text")
                        .done(false)
                        .build();

        assertEquals("Hello", chunk.getDelta());
        assertEquals("text", chunk.getType());
        assertFalse(chunk.isDone());
    }
}
