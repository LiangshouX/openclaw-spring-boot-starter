package io.openclaw.runtime.client;

import io.openclaw.runtime.client.http.ArtifactClient;
import io.openclaw.runtime.client.http.ChatClient;
import io.openclaw.runtime.client.http.EventClient;
import io.openclaw.runtime.client.http.SessionHttpClient;
import io.openclaw.runtime.client.http.TaskClient;
import io.openclaw.runtime.client.http.UploadClient;
import lombok.Builder;
import lombok.Data;

/** OpenClaw 客户端门面，聚合所有 HTTP/WebSocket 客户端组件。 */
@Data
@Builder
public class OpenClawClient {

    private ChatClient chatClient;
    private TaskClient taskClient;
    private SessionHttpClient sessionClient;
    private EventClient eventClient;
    private UploadClient uploadClient;
    private ArtifactClient artifactClient;
}
