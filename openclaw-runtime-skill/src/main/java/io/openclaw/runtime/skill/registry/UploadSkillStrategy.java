package io.openclaw.runtime.skill.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openclaw.runtime.api.exception.ErrorCode;
import io.openclaw.runtime.api.exception.RegisterException;
import io.openclaw.runtime.client.websocket.OpenClawWebSocketClient;
import io.openclaw.runtime.skill.archive.SkillArchiveBuilder;
import io.openclaw.runtime.skill.model.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * 通过 WebSocket RPC 上传 Skill 归档并安装到 OpenClaw Gateway。
 * <p>
 * 完整流程：
 * <ol>
 *   <li>{@link SkillArchiveBuilder} 将 SKILL.md 打包为 zip 归档</li>
 *   <li>{@code skills.upload.begin} — 创建上传会话，获取 uploadId</li>
 *   <li>{@code skills.upload.chunk} — 分块上传 base64 编码的归档字节</li>
 *   <li>{@code skills.upload.commit} — 提交上传，验证大小和 SHA-256</li>
 *   <li>{@code skills.install({source: "upload"})} — 安装到 Gateway workspace</li>
 * </ol>
 * <p>
 * <b>前提条件</b>：Gateway 配置必须启用 {@code skills.install.allowUploadedArchives: true}。
 * 若未启用，注册将静默跳过并记录警告日志。
 */
public class UploadSkillStrategy implements SkillRegistrationStrategy {

    private static final Logger log = LoggerFactory.getLogger(UploadSkillStrategy.class);
    private static final String NAME = "upload-install";

    /** 分块上传大小：64 KB。 */
    private static final int CHUNK_SIZE = 64 * 1024;

    private final OpenClawWebSocketClient wsClient;
    private final SkillArchiveBuilder archiveBuilder;
    private final ObjectMapper objectMapper;

    public UploadSkillStrategy(OpenClawWebSocketClient wsClient, SkillArchiveBuilder archiveBuilder) {
        this(wsClient, archiveBuilder, new ObjectMapper());
    }

    public UploadSkillStrategy(OpenClawWebSocketClient wsClient,
                                SkillArchiveBuilder archiveBuilder,
                                ObjectMapper objectMapper) {
        this.wsClient = wsClient;
        this.archiveBuilder = archiveBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(List<SkillDefinition> skills) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.warn("WebSocket not connected — skipping skill upload registration. " +
                    "Ensure WebSocket auto-connect is enabled and the Gateway is reachable.");
            return;
        }

        log.info("Uploading {} skills to Gateway via skills.upload + skills.install", skills.size());

        int successCount = 0;
        for (SkillDefinition skill : skills) {
            try {
                uploadAndInstall(skill);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to upload and install skill '{}': {}", skill.getName(), e.getMessage());
                log.debug("Upload error details for '{}'", skill.getName(), e);
            }
        }

        if (successCount > 0) {
            log.info("Successfully uploaded and installed {}/{} skills", successCount, skills.size());
        } else if (!skills.isEmpty()) {
            log.warn("No skills were successfully uploaded. This may indicate that " +
                    "'skills.install.allowUploadedArchives' is not enabled on the Gateway, " +
                    "or the WebSocket connection lacks 'operator.admin' scope.");
        }
    }

    @Override
    public void unregister(List<String> skillNames) {
        if (wsClient == null || !wsClient.isConnected()) {
            log.debug("WebSocket not connected — skipping skill uninstall via upload strategy");
            return;
        }

        log.info("Uninstalling {} skills is not directly supported via upload strategy. " +
                "Skills can be disabled via skills.update (config mode) or removed manually.", skillNames.size());
        // Note: OpenClaw Gateway does not have a skills.uninstall RPC.
        // Skills can be disabled via skills.update config mode (handled by SkillStatusStrategy).
    }

    @Override
    public String getName() {
        return NAME;
    }

    /**
     * 执行单个 Skill 的完整上传+安装流程。
     */
    private void uploadAndInstall(SkillDefinition skill) throws IOException {
        // Step 1: Build zip archive
        byte[] archive = archiveBuilder.buildArchive(skill);
        String sha256 = archiveBuilder.computeSha256(archive);
        log.debug("Skill '{}': archive size={} bytes, sha256={}", skill.getName(), archive.length, sha256);

        // Step 2: skills.upload.begin
        ObjectNode beginParams = objectMapper.createObjectNode();
        beginParams.put("kind", "skill-archive");
        beginParams.put("slug", skill.getName());
        beginParams.put("sizeBytes", archive.length);
        beginParams.put("sha256", sha256);
        beginParams.put("force", true);

        JsonNode beginResult = wsClient.invoke("skills.upload.begin", beginParams).block();
        if (beginResult == null) {
            throw new RegisterException(ErrorCode.SKILL_UPLOAD_FAILED,
                    "skills.upload.begin returned null for skill: " + skill.getName());
        }

        String uploadId = extractStringField(beginResult, "uploadId");
        if (uploadId == null || uploadId.isEmpty()) {
            throw new RegisterException(ErrorCode.SKILL_UPLOAD_FAILED,
                    "skills.upload.begin did not return uploadId for skill: " + skill.getName());
        }
        log.debug("Skill '{}': upload session created, uploadId={}", skill.getName(), uploadId);

        // Step 3: skills.upload.chunk — upload in chunks
        Base64.Encoder encoder = Base64.getEncoder();
        int offset = 0;
        while (offset < archive.length) {
            int end = Math.min(offset + CHUNK_SIZE, archive.length);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(archive, offset, chunk, 0, chunk.length);

            ObjectNode chunkParams = objectMapper.createObjectNode();
            chunkParams.put("uploadId", uploadId);
            chunkParams.put("offset", offset);
            chunkParams.put("dataBase64", encoder.encodeToString(chunk));

            wsClient.invoke("skills.upload.chunk", chunkParams).block();
            offset = end;
        }
        log.debug("Skill '{}': uploaded {} bytes in chunks", skill.getName(), archive.length);

        // Step 4: skills.upload.commit
        ObjectNode commitParams = objectMapper.createObjectNode();
        commitParams.put("uploadId", uploadId);
        commitParams.put("sha256", sha256);

        wsClient.invoke("skills.upload.commit", commitParams).block();
        log.debug("Skill '{}': upload committed", skill.getName());

        // Step 5: skills.install
        ObjectNode installParams = objectMapper.createObjectNode();
        installParams.put("source", "upload");
        installParams.put("uploadId", uploadId);
        installParams.put("slug", skill.getName());
        installParams.put("force", true);
        installParams.put("sha256", sha256);

        JsonNode installResult = wsClient.invoke("skills.install", installParams).block();
        log.info("Skill '{}' installed successfully via upload strategy", skill.getName());
        log.debug("Install result for '{}': {}", skill.getName(), installResult);
    }

    /**
     * 从 JsonNode 响应中安全提取字符串字段。
     */
    private String extractStringField(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode field = node.get(fieldName);
        if (field != null && field.isTextual()) {
            return field.asText();
        }
        // Also check nested "result" wrapper
        JsonNode result = node.get("result");
        if (result != null && result.isObject()) {
            JsonNode nested = result.get(fieldName);
            if (nested != null && nested.isTextual()) {
                return nested.asText();
            }
        }
        return null;
    }
}
